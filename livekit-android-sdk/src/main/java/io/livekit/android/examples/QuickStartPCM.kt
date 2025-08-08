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
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 最简单的 PCM 文件音频示例
 *
 * 用法：
 * ```kotlin
 * val pcm = QuickStartPCM(context, room)
 * pcm.start(inputPCMFile, outputPCMFile)
 *
 * // 稍后停止
 * pcm.stop()
 * ```
 */
class QuickStartPCM(
    private val context: Context,
    private val room: Room
) {
    private var isRunning = false
    private var outputStream: FileOutputStream? = null

    /**
     * 开始：播放输入 PCM 文件，录制远程音频到输出文件
     */
    suspend fun start(inputFile: File, outputFile: File) {
        if (!inputFile.exists()) {
            throw IllegalArgumentException("输入文件不存在: ${inputFile.absolutePath}")
        }

        isRunning = true

        // 1. 设置音频输入：从 PCM 文件播放
        setupInput(inputFile)

        // 2. 设置音频输出：录制远程音频
        setupOutput(outputFile)
    }

    /**
     * 停止音频处理
     */
    fun stop() {
        isRunning = false
        outputStream?.close()
        outputStream = null
    }

    private suspend fun setupInput(inputFile: File) {
        // 创建音频轨道
        val (audioTrack, bufferProvider) = room.localParticipant.createAudioTrackWithBuffer(
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,
            sampleRate = 44100
        )

        // 发布到房间
        room.localParticipant.publishAudioTrack(audioTrack)

        // 读取文件并推送数据
        CoroutineScope(Dispatchers.IO).launch {
            FileInputStream(inputFile).use { input ->
                val buffer = ByteArray(4096)

                while (isRunning && isActive) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead > 0) {
                        bufferProvider.addAudioData(buffer.copyOf(bytesRead))
                        delay(23)  // 控制播放速度
                    } else {
                        input.channel.position(0)  // 循环播放
                    }
                }
            }
        }
    }

    private fun setupOutput(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputStream = FileOutputStream(outputFile)

        // 录制所有远程参与者的音频
        room.remoteParticipants.values.forEach { participant ->
            val audioTrack = participant.audioTrackPublications
                .firstOrNull()?.first?.track as? io.livekit.android.room.track.RemoteAudioTrack

            audioTrack?.setAudioDataProcessor(participant, audioTrack.sid.toString()) { _, _, audioData, _, _, _, _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    val bytes = ByteArray(audioData.remaining())
                    audioData.duplicate().get(bytes)
                    outputStream?.write(bytes)
                }
            }
        }
    }
}

/**
 * 使用示例
 */
/*
// 在您的 Activity 中：

lifecycleScope.launch {
    val room = LiveKit.create(context)
    val pcm = QuickStartPCM(context, room)

    // 连接房间
    room.connect("wss://your-server", "your-token")

    // 开始处理
    val inputFile = File(filesDir, "input.pcm")
    val outputFile = File(filesDir, "output.pcm")
    pcm.start(inputFile, outputFile)

    // 60秒后停止
    delay(60000)
    pcm.stop()
}
*/
