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
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 调试版本的PCM示例，包含详细的日志和状态检查
 */
class DebugPCMExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var outputStream: FileOutputStream? = null
    private var bytesWritten = 0L
    private var bytesRead = 0L

    // 统一的音频参数
    private val sampleRate = 16000  // 16kHz更常用于语音
    private val channelCount = 1    // 单声道简化调试
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    /**
     * 开始测试：从PCM文件播放，录制远程音频
     */
    suspend fun startTest(inputFile: File, outputFile: File) {
        Log.d("DebugPCM", "开始测试")
        Log.d("DebugPCM", "音频参数: ${sampleRate}Hz, ${channelCount}声道, 16-bit")

        if (!inputFile.exists()) {
            Log.e("DebugPCM", "输入文件不存在: ${inputFile.absolutePath}")
            return
        }

        val fileSize = inputFile.length()
        val bytesPerFrame = channelCount * 2  // 16-bit = 2 bytes
        val totalFrames = fileSize / bytesPerFrame
        val durationSeconds = totalFrames.toDouble() / sampleRate

        Log.d("DebugPCM", "输入文件信息:")
        Log.d("DebugPCM", "  文件大小: $fileSize 字节")
        Log.d("DebugPCM", "  预计时长: ${String.format("%.2f", durationSeconds)} 秒")

        isRunning = true
        bytesWritten = 0L
        bytesRead = 0L

        // 1. 设置音频输入
        setupAudioInput(inputFile)

        // 2. 设置音频输出
        setupAudioOutput(outputFile)

        // 3. 监听房间事件
        setupRoomEventListener()

        Log.d("DebugPCM", "测试已启动，等待远程参与者...")
    }

    private suspend fun setupAudioInput(inputFile: File) {
        try {
            val localParticipant = room.localParticipant

            // 创建音频轨道
            val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
                name = "debug_pcm_input",
                audioFormat = audioFormat,
                channelCount = channelCount,
                sampleRate = sampleRate,
                microphoneGain = 0.0f  // 完全禁用麦克风
            )

            Log.d("DebugPCM", "创建音频轨道: ${audioTrack.name}, sid: ${audioTrack.sid}")

            // 发布音频轨道
            val success = localParticipant.publishAudioTrack(audioTrack)
            Log.d("DebugPCM", "发布音频轨道结果: $success")

            // 开始读取文件
            scope.launch {
                FileInputStream(inputFile).use { inputStream ->
                    val bytesPerFrame = channelCount * 2
                    val bufferDurationMs = 20  // 20ms
                    val bufferSizeFrames = (sampleRate * bufferDurationMs) / 1000
                    val bufferSizeBytes = bufferSizeFrames * bytesPerFrame
                    val buffer = ByteArray(bufferSizeBytes)

                    Log.d("DebugPCM", "开始读取文件，缓冲区大小: $bufferSizeBytes 字节")

                    while (isRunning && isActive) {
                        val bytesReadFromFile = inputStream.read(buffer)
                        if (bytesReadFromFile > 0) {
                            val audioData = buffer.copyOf(bytesReadFromFile)
                            bufferProvider.addAudioData(audioData)

                            bytesRead += bytesReadFromFile

                            if (bytesRead % (sampleRate * bytesPerFrame) == 0L) {  // 每秒打印一次
                                val secondsRead = bytesRead / (sampleRate * bytesPerFrame)
                                Log.d("DebugPCM", "已读取 $secondsRead 秒音频数据")
                            }

                            // 控制播放速度
                            delay(bufferDurationMs.toLong())
                        } else {
                            Log.d("DebugPCM", "文件读取完毕，总共读取: $bytesRead 字节")
                            // 不循环播放，方便调试
                            break
                        }
                    }
                }
            }

            Log.d("DebugPCM", "音频输入设置完成")

        } catch (e: Exception) {
            Log.e("DebugPCM", "设置音频输入失败", e)
        }
    }

    private fun setupAudioOutput(outputFile: File) {
        try {
            outputFile.parentFile?.mkdirs()
            outputStream = FileOutputStream(outputFile)

            Log.d("DebugPCM", "输出文件: ${outputFile.absolutePath}")

            // 检查当前已有的远程参与者
            val remoteParticipants = room.remoteParticipants.values
            Log.d("DebugPCM", "当前远程参与者数量: ${remoteParticipants.size}")

            remoteParticipants.forEach { participant ->
                Log.d("DebugPCM", "远程参与者: ${participant.identity}")
                setupAudioRecordingForParticipant(participant)
            }

            Log.d("DebugPCM", "音频输出设置完成")

        } catch (e: Exception) {
            Log.e("DebugPCM", "设置音频输出失败", e)
        }
    }

    private fun setupRoomEventListener() {
        scope.launch {
            room.events.collect { event ->
                Log.d("DebugPCM", "房间事件: ${event::class.simpleName}")

                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        Log.d("DebugPCM", "参与者加入: ${event.participant.identity}")
                    }

                    is RoomEvent.TrackSubscribed -> {
                        Log.d("DebugPCM", "轨道订阅: ${event.track::class.simpleName}, source: ${event.track.kind}")

                        if (event.track is RemoteAudioTrack && event.track.kind == Track.Kind.AUDIO) {
                            Log.d("DebugPCM", "发现远程音频轨道，设置录制")
                            setupAudioRecordingForParticipant(event.participant)
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
                        Log.d("DebugPCM", "轨道取消订阅: ${event.track::class.simpleName}")
                    }

                    else -> {
                        // 其他事件
                    }
                }
            }
        }
    }

    private fun setupAudioRecordingForParticipant(participant: io.livekit.android.room.participant.Participant) {
        try {
            val audioPublication = participant.audioTrackPublications.firstOrNull()
            if (audioPublication == null) {
                Log.w("DebugPCM", "参与者 ${participant.identity} 没有音频发布")
                return
            }

            val audioTrack = audioPublication.first.track as? RemoteAudioTrack
            if (audioTrack == null) {
                Log.w("DebugPCM", "参与者 ${participant.identity} 的音频轨道为空")
                return
            }

            Log.d("DebugPCM", "为参与者 ${participant.identity} 设置音频录制")
            Log.d("DebugPCM", "  轨道sid: ${audioTrack.sid}")
            Log.d("DebugPCM", "  轨道源: ${audioTrack.kind}")
            Log.d("DebugPCM", "  轨道状态: ${audioTrack.kind}")

            audioTrack.setAudioDataProcessor(
                participant = participant,
                trackSid = audioTrack.sid
            ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

                scope.launch {
                    try {
                        val audioBytes = ByteArray(audioData.remaining())
                        audioData.duplicate().get(audioBytes)
                        outputStream?.write(audioBytes)
                        outputStream?.flush()

                        bytesWritten += audioBytes.size

                        // 每秒打印一次统计
                        val bytesPerSecond = sampleRate * numberOfChannels * (bitsPerSample / 8)
                        if (bytesWritten % bytesPerSecond == 0L) {
                            val secondsWritten = bytesWritten / bytesPerSecond
                            Log.d("DebugPCM", "已录制 $secondsWritten 秒音频，总计 $bytesWritten 字节")
                        }

                        // 每10MB打印一次详细信息
                        if (bytesWritten % (10 * 1024 * 1024) == 0L) {
                            Log.d("DebugPCM", "音频数据详情:")
                            Log.d("DebugPCM", "  参与者: ${participant.identity}")
                            Log.d("DebugPCM", "  数据大小: ${audioBytes.size} 字节")
                            Log.d("DebugPCM", "  采样率: $sampleRate Hz")
                            Log.d("DebugPCM", "  声道数: $numberOfChannels")
                            Log.d("DebugPCM", "  位深度: $bitsPerSample bit")
                            Log.d("DebugPCM", "  帧数: $numberOfFrames")
                        }

                    } catch (e: Exception) {
                        Log.e("DebugPCM", "写入音频数据失败", e)
                    }
                }
            }

            Log.d("DebugPCM", "音频录制设置完成")

        } catch (e: Exception) {
            Log.e("DebugPCM", "设置参与者音频录制失败", e)
        }
    }

    /**
     * 停止测试并输出统计信息
     */
    fun stop() {
        isRunning = false

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            Log.e("DebugPCM", "关闭输出流失败", e)
        }

        Log.d("DebugPCM", "测试结束统计:")
        Log.d("DebugPCM", "  输入读取: $bytesRead 字节")
        Log.d("DebugPCM", "  输出写入: $bytesWritten 字节")
        Log.d("DebugPCM", "  输入时长: ${bytesRead / (sampleRate * channelCount * 2)} 秒")
        Log.d("DebugPCM", "  输出时长: ${bytesWritten / (sampleRate * channelCount * 2)} 秒")

        if (bytesWritten == 0L) {
            Log.w("DebugPCM", "⚠️ 没有录制到任何音频数据!")
            Log.w("DebugPCM", "可能的原因:")
            Log.w("DebugPCM", "1. 没有其他参与者加入房间")
            Log.w("DebugPCM", "2. 其他参与者没有开启麦克风")
            Log.w("DebugPCM", "3. 网络连接问题")
            Log.w("DebugPCM", "4. 权限问题")
        } else {
            Log.d("DebugPCM", "✅ 成功录制音频数据!")
        }
    }

    /**
     * 获取当前状态信息
     */
    fun getStatus(): String {
        val remoteParticipantCount = room.remoteParticipants.size
        val connectionState = room.state

        return """
            状态信息:
            - 运行中: $isRunning
            - 连接状态: $connectionState
            - 远程参与者: $remoteParticipantCount 个
            - 已读取: $bytesRead 字节
            - 已写入: $bytesWritten 字节
        """.trimIndent()
    }

    /**
     * 创建测试用的PCM文件
     */
    fun createTestFile(outputFile: File, durationSeconds: Int = 5) {
        val bytesPerSample = 2
        val totalFrames = sampleRate * durationSeconds
        val frequency = 440.0  // A4音符

        Log.d("DebugPCM", "创建测试文件: ${outputFile.absolutePath}")
        Log.d("DebugPCM", "参数: ${sampleRate}Hz, ${channelCount}声道, ${durationSeconds}秒")

        FileOutputStream(outputFile).use { output ->
            for (frame in 0 until totalFrames) {
                val sample = (Math.sin(2.0 * Math.PI * frequency * frame / sampleRate) * Short.MAX_VALUE * 0.3).toInt().toShort()

                // 写入所有声道
                for (channel in 0 until channelCount) {
                    output.write(sample.toInt() and 0xFF)  // 低字节
                    output.write((sample.toInt() shr 8) and 0xFF)  // 高字节
                }
            }
        }

        Log.d("DebugPCM", "测试文件创建完成")
    }
}
