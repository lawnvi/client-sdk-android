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

package io.livekit.android.audio

import android.media.AudioFormat
import io.livekit.android.util.LKLog
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 独立的自定义音频生成器
 *
 * 这个类完全绕过麦克风和WebRTC的AudioBufferCallback机制，
 * 直接模拟麦克风输入来驱动音频回调链。
 */
class StandaloneCustomAudioGenerator(
    private val audioBufferCallbackDispatcher: AudioBufferCallbackDispatcher,
    private val customAudioProvider: CustomAudioBufferProvider,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val channelCount: Int = 2,
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val TAG = "StandaloneCustomAudio"

        // 音频帧配置 (10ms)
        private const val FRAME_DURATION_MS = 10
        private fun getFrameSize(sampleRate: Int, channels: Int, format: Int): Int {
            val bytesPerSample = when (format) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT -> 2
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> 2
            }
            return sampleRate * channels * bytesPerSample * FRAME_DURATION_MS / 1000
        }
    }

    private val isRunning = AtomicBoolean(false)
    private val audioGeneratorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var audioGeneratorJob: Job? = null

    // 音频帧配置
    private val frameSize = getFrameSize(sampleRate, channelCount, audioFormat)

    /**
     * 开始独立音频生成
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            LKLog.d { "$TAG: 🎵 开始独立自定义音频生成" }
            LKLog.d { "$TAG: 配置 - 格式:$audioFormat 声道:$channelCount 采样率:$sampleRate 帧大小:$frameSize" }

            customAudioProvider.start()

            audioGeneratorJob = audioGeneratorScope.launch {
                generateAudioLoop()
            }
        }
    }

    /**
     * 停止音频生成
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            LKLog.d { "$TAG: ⏹️ 停止独立音频生成" }

            audioGeneratorJob?.cancel()
            audioGeneratorJob = null
            customAudioProvider.stop()
        }
    }

    /**
     * 主要的音频生成循环
     */
    private suspend fun generateAudioLoop() {
        var frameCount = 0
        val startTime = System.currentTimeMillis()

        LKLog.d { "$TAG: 🔄 开始音频生成循环" }

        while (isRunning.get()) {
            try {
                val frameStartTime = System.nanoTime()

                // 从自定义音频提供器获取音频数据
                val customAudioData = customAudioProvider.provideAudioData(
                    requestedBytes = frameSize,
                    audioFormat = audioFormat,
                    channelCount = channelCount,
                    sampleRate = sampleRate
                )

                // 准备要发送的音频数据
                val audioBuffer = if (customAudioData != null && customAudioData.hasRemaining()) {
                    // 使用自定义音频数据
                    customAudioData
                } else {
                    // 没有自定义数据时发送静音
                    val silenceBuffer = ByteBuffer.allocate(frameSize)
                    repeat(frameSize) { silenceBuffer.put(0) }
                    silenceBuffer.flip()
                    silenceBuffer
                }

                // 手动触发音频回调，绕过WebRTC的麦克风机制
                val result = audioBufferCallbackDispatcher.triggerManualAudioCallback(
                    audioData = audioBuffer,
                    audioFormat = audioFormat,
                    channelCount = channelCount,
                    sampleRate = sampleRate,
                    captureTimeNs = frameStartTime
                )

                frameCount++

                // 统计信息
                if (frameCount % 100 == 0) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                    val fps = frameCount / elapsed
                    LKLog.d { "$TAG: 📊 已生成 $frameCount 帧，运行时间 ${elapsed.toInt()}s，帧率 ${fps.toInt()}fps" }
                }

                // 按帧率延迟
                delay(FRAME_DURATION_MS.toLong())

            } catch (e: CancellationException) {
                LKLog.d { "$TAG: 音频生成循环被取消" }
                break
            } catch (e: Exception) {
                LKLog.e(e) { "$TAG: 音频生成循环错误" }
                delay(100) // 短暂延迟后重试
            }
        }

        LKLog.d { "$TAG: 🏁 音频生成循环结束，总共生成 $frameCount 帧" }
    }

    /**
     * 获取生成器状态
     */
    fun isActive(): Boolean = isRunning.get()

    /**
     * 获取状态信息
     */
    fun getStatusInfo(): String {
        return """
StandaloneCustomAudioGenerator:
  运行状态: ${if (isRunning.get()) "🟢 活跃" else "🔴 停止"}
  音频格式: $audioFormat
  声道数: $channelCount
  采样率: $sampleRate Hz
  帧大小: $frameSize bytes
  帧间隔: ${FRAME_DURATION_MS}ms
        """.trimIndent()
    }
}
