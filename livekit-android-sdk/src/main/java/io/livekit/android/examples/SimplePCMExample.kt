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
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.AudioTrack
import io.livekit.android.room.track.LocalAudioTrack
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
 * 简化的 PCM 文件音频示例
 *
 * 使用方法：
 * 1. 准备输入的 PCM 文件（44.1kHz, 16-bit, 立体声）
 * 2. 调用 startExample() 开始运行
 * 3. 输入音频会发布到房间，远程音频会保存到文件
 */
class SimplePCMExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var outputStream: FileOutputStream? = null

    /**
     * 开始示例：从 PCM 文件播放，录制远程音频
     */
    suspend fun startExample(inputPCMFile: File, outputPCMFile: File) {
        try {
            // 1. 设置音频输入（从 PCM 文件播放）
            setupAudioInput(inputPCMFile)

            // 2. 设置音频输出（录制远程音频到文件）
            setupAudioOutput(outputPCMFile)

            Log.d("SimplePCM", "示例已启动")
            Log.d("SimplePCM", "输入文件: ${inputPCMFile.absolutePath}")
            Log.d("SimplePCM", "输出文件: ${outputPCMFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("SimplePCM", "启动示例失败", e)
        }
    }

    /**
     * 设置音频输入：从 PCM 文件读取并发布到房间
     */
    private suspend fun setupAudioInput(inputFile: File) {
        if (!inputFile.exists()) {
            Log.w("SimplePCM", "输入文件不存在: ${inputFile.absolutePath}")
            return
        }

        val localParticipant = room.localParticipant

        // 创建音频轨道 - 使用标准音频参数
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,
            sampleRate = 44100,  // 使用标准采样率
            microphoneGain = 0.0f,  // 禁用麦克风
            customAudioGain = 1.0f  // 自定义音频全音量
        )

        // 发布音频轨道
        localParticipant.publishAudioTrack(audioTrack)

        // 开始读取文件并推送音频数据
        scope.launch {
            isRunning = true
            FileInputStream(inputFile).use { inputStream ->
                val buffer = ByteArray(4096)  // 4KB 缓冲区

                while (isRunning && isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        bufferProvider.addAudioData(audioData)
                        println(">>>>>>> read input $bytesRead bytes")
                        // 控制播放速度（约 10ms 的音频数据，适用于44.1kHz）
                        delay(10)
                    } else {
                        // 文件结束，重新开始（循环播放）
//                        inputStream.channel.position(0)
                        isRunning = false
                    }
                }
            }
        }

        Log.d("SimplePCM", "音频输入已设置")
    }

    /**
     * 设置音频输出：录制远程音频到文件
     */
    private fun setupAudioOutput(outputFile: File) {
        // 确保输出目录存在
        outputFile.parentFile?.mkdirs()

        // 创建输出流
        outputStream = FileOutputStream(outputFile)

        // 监听所有远程参与者的音频
        room.remoteParticipants.values.forEach { participant ->
            val audioTrack = participant.audioTrackPublications
                .firstOrNull()?.first?.track as? io.livekit.android.room.track.RemoteAudioTrack

            audioTrack?.setAudioDataProcessor(
                participant = participant,
                trackSid = audioTrack.sid.toString()
            ) { participant, _, audioData, _, _, _, _, _ ->
                // 将音频数据写入文件
                scope.launch {
                    try {
                        val audioBytes = ByteArray(audioData.remaining())
                        audioData.duplicate().get(audioBytes)
                        outputStream?.write(audioBytes)

                        Log.v("SimplePCM", "录制: ${participant.identity} - ${audioBytes.size} bytes")
                    } catch (e: Exception) {
                        Log.e("SimplePCM", "写入文件失败", e)
                    }
                }
            }
        }

        Log.d("SimplePCM", "音频输出已设置")
    }

    /**
     * 停止示例
     */
    fun stop() {
        isRunning = false

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            Log.e("SimplePCM", "关闭输出流失败", e)
        }

        Log.d("SimplePCM", "示例已停止")
    }
}

/**
 * 使用示例
 */
fun main() {
    // 在您的 Activity 或 Service 中使用：

    /*
    val room = LiveKit.create(context)
    val example = SimplePCMExample(context, room)

    // 准备文件路径
    val inputFile = File(context.filesDir, "input.pcm")   // 您的输入 PCM 文件
    val outputFile = File(context.filesDir, "output.pcm") // 录制的输出文件

    lifecycleScope.launch {
        // 连接到房间
        room.connect(url, token)

        // 开始示例
        example.startExample(inputFile, outputFile)

        // 运行一段时间后停止
        delay(60000) // 60 秒
        example.stop()
    }
    */
}
