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
 * 音频功能诊断工具
 * 帮助调试音频输入输出功能是否正常工作
 */
class AudioDiagnostic(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var outputStream: FileOutputStream? = null
    private var totalBytesWritten = 0L
    private var totalBytesRead = 0L

    // 诊断信息
    private val diagnosticInfo = mutableListOf<String>()

    fun startDiagnostic(inputFile: File, outputFile: File) {
        addLog("=== 开始音频诊断 ===")
        addLog("输入文件: ${inputFile.absolutePath}")
        addLog("输出文件: ${outputFile.absolutePath}")

        // 检查输入文件
        if (!inputFile.exists()) {
            addLog("❌ 输入文件不存在")
            createTestInputFile(inputFile)
        } else {
            val fileSize = inputFile.length()
            addLog("✅ 输入文件存在，大小: $fileSize 字节")

            // 估算时长（假设16kHz单声道16bit）
            val estimatedDuration = fileSize / (16000 * 1 * 2)
            addLog("   预估时长: $estimatedDuration 秒")
        }

        isRunning = true
        totalBytesWritten = 0L
        totalBytesRead = 0L

        // 1. 设置音频输入
        setupAudioInput(inputFile)

        // 2. 设置音频输出
        setupAudioOutput(outputFile)

        // 3. 监听房间事件
        setupRoomEventListener()

        addLog("✅ 诊断启动完成")
    }

    private fun createTestInputFile(inputFile: File) {
        addLog("📝 创建测试输入文件...")

        try {
            inputFile.parentFile?.mkdirs()

            // 创建5秒的440Hz正弦波
            val sampleRate = 16000
            val channels = 1
            val durationSeconds = 5
            val frequency = 440.0

            val totalFrames = sampleRate * durationSeconds

            FileOutputStream(inputFile).use { output ->
                for (frame in 0 until totalFrames) {
                    val sample = (Math.sin(2.0 * Math.PI * frequency * frame / sampleRate) * Short.MAX_VALUE * 0.3).toInt().toShort()

                    // 写入16-bit小端序
                    output.write(sample.toInt() and 0xFF)  // 低字节
                    output.write((sample.toInt() shr 8) and 0xFF)  // 高字节
                }
            }

            addLog("✅ 测试文件创建成功: ${inputFile.length()} 字节")

        } catch (e: Exception) {
            addLog("❌ 创建测试文件失败: ${e.message}")
        }
    }

    private fun setupAudioInput(inputFile: File) {
        scope.launch {
            try {
                addLog("🎵 设置音频输入...")

                val localParticipant = room.localParticipant

                // 创建音频轨道
                val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
                    name = "diagnostic_input",
                    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                    channelCount = 1,  // 单声道
                    sampleRate = 16000,  // 16kHz
                    microphoneGain = 0.0f  // 禁用麦克风
                )

                addLog("✅ 音频轨道创建成功: ${audioTrack.name}")

                // 发布音频轨道
                val published = localParticipant.publishAudioTrack(audioTrack)
                addLog("✅ 音频轨道发布: $published")

                // 开始读取文件
                addLog("📖 开始读取音频文件...")

                FileInputStream(inputFile).use { inputStream ->
                    val buffer = ByteArray(1600)  // 100ms的音频数据 (16000 * 0.1 * 2)
                    var totalRead = 0L

                    while (isRunning && isActive) {
                        val bytesRead = inputStream.read(buffer)

                        if (bytesRead > 0) {
                            val audioData = buffer.copyOf(bytesRead)
                            bufferProvider.addAudioData(audioData)

                            totalRead += bytesRead
                            totalBytesRead = totalRead

                            // 每秒打印一次进度
                            if (totalRead % 32000 == 0L) {  // 32000字节 = 1秒音频
                                val seconds = totalRead / 32000
                                addLog("📖 已读取 $seconds 秒音频数据")
                            }

                            delay(100)  // 100ms延迟

                        } else {
                            addLog("📖 文件读取完毕")
                            break
                        }
                    }
                }

                addLog("✅ 音频输入设置完成，总共读取: $totalBytesRead 字节")

            } catch (e: Exception) {
                addLog("❌ 音频输入设置失败: ${e.message}")
                Log.e("AudioDiagnostic", "音频输入设置失败", e)
            }
        }
    }

    private fun setupAudioOutput(outputFile: File) {
        try {
            addLog("🎧 设置音频输出...")

            outputFile.parentFile?.mkdirs()
            outputStream = FileOutputStream(outputFile)

            addLog("✅ 输出文件创建成功: ${outputFile.absolutePath}")

            // 检查当前远程参与者
            val remoteParticipants = room.remoteParticipants.values
            addLog("🔍 当前远程参与者数量: ${remoteParticipants.size}")

            if (remoteParticipants.isEmpty()) {
                addLog("⚠️ 暂无远程参与者，等待其他人加入...")
            } else {
                remoteParticipants.forEach { participant ->
                    addLog("👤 远程参与者: ${participant.identity}")
                    setupParticipantAudioRecording(participant)
                }
            }

        } catch (e: Exception) {
            addLog("❌ 音频输出设置失败: ${e.message}")
            Log.e("AudioDiagnostic", "音频输出设置失败", e)
        }
    }

    private fun setupRoomEventListener() {
        scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        addLog("👤 参与者加入: ${event.participant.identity}")
                    }

                    is RoomEvent.TrackSubscribed -> {
                        addLog("📡 轨道订阅: ${event.track::class.simpleName}")

                        if (event.track is RemoteAudioTrack && event.track.kind == Track.Kind.AUDIO) {
                            addLog("🎤 发现远程音频轨道，开始录制")
                            setupParticipantAudioRecording(event.participant)
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
                        addLog("📡 轨道取消订阅: ${event.track::class.simpleName}")
                    }

                    else -> {
                        // 其他事件
                    }
                }
            }
        }
    }

    private fun setupParticipantAudioRecording(participant: io.livekit.android.room.participant.Participant) {
        try {
            val audioPublication = participant.audioTrackPublications.firstOrNull()
            if (audioPublication == null) {
                addLog("⚠️ 参与者 ${participant.identity} 没有音频轨道")
                return
            }

            val audioTrack = audioPublication.first.track as? RemoteAudioTrack
            if (audioTrack == null) {
                addLog("⚠️ 参与者 ${participant.identity} 的音频轨道类型错误")
                return
            }

            addLog("🎤 设置音频录制: ${participant.identity}")

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

                        totalBytesWritten += audioBytes.size

                        // 每秒打印一次统计
                        if (totalBytesWritten % (sampleRate * numberOfChannels * (bitsPerSample / 8)) == 0L) {
                            val seconds = totalBytesWritten / (sampleRate * numberOfChannels * (bitsPerSample / 8))
                            addLog("🎧 已录制 $seconds 秒音频 (${participant.identity})")
                        }

                    } catch (e: Exception) {
                        addLog("❌ 写入音频数据失败: ${e.message}")
                        Log.e("AudioDiagnostic", "写入音频数据失败", e)
                    }
                }
            }

            addLog("✅ 音频录制设置完成: ${participant.identity}")

        } catch (e: Exception) {
            addLog("❌ 设置参与者音频录制失败: ${e.message}")
            Log.e("AudioDiagnostic", "设置参与者音频录制失败", e)
        }
    }

    fun stop() {
        isRunning = false

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: Exception) {
            addLog("❌ 关闭输出流失败: ${e.message}")
        }

        addLog("=== 诊断结束 ===")
        addLog("总读取: $totalBytesRead 字节")
        addLog("总写入: $totalBytesWritten 字节")

        if (totalBytesRead > 0) {
            addLog("✅ 音频输入正常工作")
        } else {
            addLog("❌ 音频输入未工作")
        }

        if (totalBytesWritten > 0) {
            addLog("✅ 音频输出正常工作")
        } else {
            addLog("❌ 音频输出未工作 - 可能原因:")
            addLog("   1. 房间中没有其他参与者")
            addLog("   2. 其他参与者没有开启麦克风")
            addLog("   3. 网络连接问题")
        }
    }

    fun getDiagnosticReport(): String {
        return diagnosticInfo.joinToString("\n")
    }

    private fun addLog(message: String) {
        val timestamp = System.currentTimeMillis()
        val logMessage = "[${timestamp}] $message"
        diagnosticInfo.add(logMessage)
        Log.d("AudioDiagnostic", message)
        println(message)  // 也输出到控制台
    }
}
