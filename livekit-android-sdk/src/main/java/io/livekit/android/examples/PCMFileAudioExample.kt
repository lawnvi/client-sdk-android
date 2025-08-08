/*
 * Copyright 2024 LiveKit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.livekit.android.examples

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.BufferAudioBufferProvider
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.clearAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * 完整示例：从 PCM 文件读取音频作为输入，将接收到的远程音频写入文件
 *
 * 这个示例展示了：
 * 1. 如何从 PCM 文件读取音频数据并发布到房间
 * 2. 如何拦截远程参与者的音频数据并保存到文件
 */
class PCMFileAudioExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // PCM 文件的音频格式参数
    private val sampleRate = 44100
    private val channelCount = 2  // 立体声
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerSample = 2  // 16-bit = 2 bytes
    private val bytesPerFrame = channelCount * bytesPerSample  // 每帧的字节数

    // 音频输入相关
    private var inputBufferProvider: BufferAudioBufferProvider? = null
    private var inputFileStream: FileInputStream? = null

    // 音频输出相关
    private val participantOutputStreams = mutableMapOf<String, FileOutputStream>()

    /**
     * 开始从 PCM 文件播放音频到房间
     *
     * @param pcmInputFile 输入的 PCM 文件路径
     * @param loop 是否循环播放
     */
    suspend fun startPlayingFromPCMFile(pcmInputFile: File, loop: Boolean = true) {
        if (!pcmInputFile.exists()) {
            Log.e("PCMExample", "PCM 输入文件不存在: ${pcmInputFile.absolutePath}")
            return
        }

        try {
            val localParticipant = room.localParticipant

            // 创建缓冲区音频轨道
            val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
                name = "pcm_file_audio",
                audioFormat = audioFormat,
                channelCount = channelCount,
                sampleRate = sampleRate,
                loop = loop,
                microphoneGain = 0.0f,  // 完全禁用麦克风
                customAudioGain = 1.0f   // 文件音频正常音量
            )

            inputBufferProvider = bufferProvider

            // 发布音频轨道
            localParticipant.publishAudioTrack(audioTrack)

            // 开始从文件读取和推送音频数据
            startPCMFileReading(pcmInputFile, bufferProvider, loop)

            Log.d("PCMExample", "开始从 PCM 文件播放音频: ${pcmInputFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("PCMExample", "启动 PCM 文件播放失败", e)
        }
    }

    /**
     * 开始录制远程参与者的音频到文件
     *
     * @param outputDirectory 输出文件夹
     */
    fun startRecordingRemoteAudio(outputDirectory: File) {
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        // 监听所有远程参与者的音频
        room.remoteParticipants.values.forEach { participant ->
            setupAudioRecordingForParticipant(participant, outputDirectory)
        }

        Log.d("PCMExample", "开始录制远程音频到目录: ${outputDirectory.absolutePath}")
    }

    /**
     * 为指定参与者设置音频录制
     */
    private fun setupAudioRecordingForParticipant(
        participant: io.livekit.android.room.participant.Participant,
        outputDirectory: File
    ) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull()?.first?.track as? io.livekit.android.room.track.RemoteAudioTrack

        if (audioTrack == null) {
            Log.w("PCMExample", "参与者 ${participant.identity} 没有音频轨道")
            return
        }

        // 创建输出文件
        val outputFile = File(outputDirectory, "${participant.identity}_${System.currentTimeMillis()}.pcm")
        val outputStream = FileOutputStream(outputFile)
        participantOutputStreams[participant.identity?.value!!] = outputStream

        // 设置音频数据处理器
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            // 将音频数据写入文件
            scope.launch {
                try {
                    val audioBytes = ByteArray(audioData.remaining())
                    audioData.duplicate().get(audioBytes)
                    outputStream.write(audioBytes)
                    outputStream.flush()

                    Log.v("PCMExample", "录制音频: ${participant.identity} - ${audioBytes.size} bytes")

                } catch (e: IOException) {
                    Log.e("PCMExample", "写入音频文件失败: ${participant.identity}", e)
                }
            }
        }

        Log.d("PCMExample", "为参与者 ${participant.identity} 设置音频录制: ${outputFile.absolutePath}")
    }

    /**
     * 从 PCM 文件读取音频数据并推送到缓冲区提供者
     */
    private fun startPCMFileReading(
        pcmFile: File,
        bufferProvider: BufferAudioBufferProvider,
        loop: Boolean
    ) {
        scope.launch {
            isRunning = true

            try {
                while (isRunning && isActive) {
                    inputFileStream = FileInputStream(pcmFile)

                    // 定义缓冲区大小（约 20ms 的音频数据）
                    val bufferSizeMs = 20
                    val bufferSizeFrames = (sampleRate * bufferSizeMs) / 1000
                    val bufferSizeBytes = bufferSizeFrames * bytesPerFrame

                    val buffer = ByteArray(bufferSizeBytes)

                    Log.d("PCMExample", "开始读取 PCM 文件，缓冲区大小: $bufferSizeBytes 字节")

                    var totalBytesRead = 0L

                    while (isRunning && isActive) {
                        val bytesRead = inputFileStream!!.read(buffer)

                        if (bytesRead > 0) {
                            // 推送音频数据到缓冲区
                            val audioData = buffer.copyOf(bytesRead)
                            bufferProvider.addAudioData(audioData)

                            totalBytesRead += bytesRead

                            // 按照音频播放速度延迟
                            val delayMs = (bufferSizeMs * bytesRead) / bufferSizeBytes
                            delay(delayMs.toLong())

                        } else {
                            // 文件读取完毕
                            Log.d("PCMExample", "PCM 文件读取完毕，总共读取: $totalBytesRead 字节")

                            if (loop) {
                                Log.d("PCMExample", "循环播放，重新开始读取文件")
                                inputFileStream?.close()
                                break  // 跳出内循环，重新开始读取文件
                            } else {
                                Log.d("PCMExample", "文件播放完毕，停止")
                                isRunning = false
                                break
                            }
                        }
                    }

                    inputFileStream?.close()

                    if (!loop) break
                }

            } catch (e: Exception) {
                Log.e("PCMExample", "读取 PCM 文件时发生错误", e)
            } finally {
                inputFileStream?.close()
            }
        }
    }

    /**
     * 停止音频播放和录制
     */
    fun stop() {
        isRunning = false

        // 关闭输入流
        try {
            inputFileStream?.close()
        } catch (e: Exception) {
            Log.e("PCMExample", "关闭输入流失败", e)
        }

        // 关闭所有输出流
        participantOutputStreams.values.forEach { outputStream ->
            try {
                outputStream.close()
            } catch (e: Exception) {
                Log.e("PCMExample", "关闭输出流失败", e)
            }
        }
        participantOutputStreams.clear()

        // 清理音频拦截器
        room.remoteParticipants.values.forEach { participant ->
            participant.audioTrackPublications.forEach { publication ->
                (publication.first.track as? io.livekit.android.room.track.RemoteAudioTrack)?.clearAudioDataInterceptor()
            }
        }

        Log.d("PCMExample", "PCM 音频示例已停止")
    }

    /**
     * 获取音频信息
     */
    fun getAudioInfo(): String {
        return """
            音频格式信息:
            - 采样率: $sampleRate Hz
            - 声道数: $channelCount (${if (channelCount == 1) "单声道" else "立体声"})
            - 位深度: ${if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) "16-bit" else "其他"}
            - 每帧字节数: $bytesPerFrame
            - 每秒数据量: ${sampleRate * bytesPerFrame} 字节 (${(sampleRate * bytesPerFrame) / 1024} KB)
        """.trimIndent()
    }
}

/**
 * 使用示例
 */
class PCMFileAudioUsageExample {

    fun demonstrateUsage(context: Context, room: Room) {
        val pcmExample = PCMFileAudioExample(context, room)

        // 准备文件路径
        val inputPCMFile = File(context.filesDir, "input_audio.pcm")
        val outputDirectory = File(context.filesDir, "recorded_audio")

        lifecycleScope.launch {
            try {
                // 显示音频格式信息
                Log.d("PCMUsage", pcmExample.getAudioInfo())

                // 1. 开始录制远程音频
                pcmExample.startRecordingRemoteAudio(outputDirectory)

                // 2. 开始播放 PCM 文件
                if (inputPCMFile.exists()) {
                    pcmExample.startPlayingFromPCMFile(inputPCMFile, loop = true)
                    Log.d("PCMUsage", "开始播放 PCM 文件并录制远程音频")
                } else {
                    Log.w("PCMUsage", "输入 PCM 文件不存在: ${inputPCMFile.absolutePath}")
                    Log.i("PCMUsage", "请将 PCM 文件放在此路径，格式要求：44.1kHz, 16-bit, 立体声")
                }

                // 3. 运行一段时间后停止（示例）
                delay(60000) // 运行 60 秒
                pcmExample.stop()

                Log.d("PCMUsage", "录制完成，输出文件保存在: ${outputDirectory.absolutePath}")

            } catch (e: Exception) {
                Log.e("PCMUsage", "PCM 音频示例运行失败", e)
            }
        }
    }
}

/**
 * PCM 文件格式说明和工具函数
 */
object PCMFileHelper {

    /**
     * 检查 PCM 文件格式是否正确
     */
    fun validatePCMFile(pcmFile: File, expectedSampleRate: Int, expectedChannels: Int): Boolean {
        if (!pcmFile.exists()) {
            Log.e("PCMHelper", "PCM 文件不存在")
            return false
        }

        val fileSize = pcmFile.length()
        val bytesPerSample = 2  // 16-bit
        val bytesPerFrame = expectedChannels * bytesPerSample

        // 检查文件大小是否为帧大小的整数倍
        if (fileSize % bytesPerFrame != 0L) {
            Log.w("PCMHelper", "PCM 文件大小不是帧大小的整数倍，可能格式不正确")
            return false
        }

        val totalFrames = fileSize / bytesPerFrame
        val durationSeconds = totalFrames.toDouble() / expectedSampleRate

        Log.d("PCMHelper", """
            PCM 文件信息:
            - 文件大小: $fileSize 字节
            - 总帧数: $totalFrames
            - 时长: ${String.format("%.2f", durationSeconds)} 秒
            - 预期格式: ${expectedSampleRate}Hz, ${expectedChannels}声道, 16-bit
        """.trimIndent())

        return true
    }

    /**
     * 创建测试用的 PCM 文件（正弦波）
     */
    fun createTestPCMFile(
        outputFile: File,
        sampleRate: Int = 44100,
        channels: Int = 2,
        durationSeconds: Int = 10,
        frequency: Double = 440.0  // A4 音符
    ) {
        val bytesPerSample = 2
        val totalFrames = sampleRate * durationSeconds
        val totalBytes = totalFrames * channels * bytesPerSample

        FileOutputStream(outputFile).use { output ->
            for (frame in 0 until totalFrames) {
                val sample = (Math.sin(2.0 * Math.PI * frequency * frame / sampleRate) * Short.MAX_VALUE * 0.5).toInt().toShort()

                // 写入所有声道
                for (channel in 0 until channels) {
                    output.write(sample.toInt() and 0xFF)  // 低字节
                    output.write((sample.toInt() shr 8) and 0xFF)  // 高字节
                }
            }
        }

        Log.d("PCMHelper", "创建测试 PCM 文件: ${outputFile.absolutePath} (${totalBytes} 字节, ${durationSeconds}秒)")
    }
}

// 需要导入的内容
private val lifecycleScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
