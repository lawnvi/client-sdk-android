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
import livekit.org.webrtc.AudioSource
import livekit.org.webrtc.MediaConstraints
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 自定义音频源 - 完全独立于麦克风的音频数据源
 *
 * 这个类实现了WebRTC AudioSource接口，但完全不依赖麦克风输入。
 * 它通过自定义的音频数据提供器来生成音频数据。
 */
class CustomAudioSource(
    private val customAudioProvider: CustomAudioBufferProvider,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val channelCount: Int = 2,
    private val sampleRate: Int = 44100
) : AudioSource(100L) {

    companion object {
        private const val TAG = "CustomAudioSource"

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

    fun setVolume(volume: Double) {
        LKLog.d { "$TAG: setVolume($volume)" }
        // 可以在这里实现音量控制
    }

    /**
     * 开始音频数据生成
     */
    fun startAudioGeneration() {
        if (isRunning.compareAndSet(false, true)) {
            LKLog.d { "$TAG: 🎵 开始自定义音频生成 - 格式:$audioFormat 声道:$channelCount 采样率:$sampleRate 帧大小:$frameSize" }

            customAudioProvider.start()

            audioGeneratorJob = audioGeneratorScope.launch {
                generateAudioFrames()
            }
        }
    }

    /**
     * 停止音频数据生成
     */
    fun stopAudioGeneration() {
        if (isRunning.compareAndSet(true, false)) {
            LKLog.d { "$TAG: ⏹️ 停止自定义音频生成" }

            audioGeneratorJob?.cancel()
            audioGeneratorJob = null
            customAudioProvider.stop()
        }
    }

    /**
     * 音频帧生成循环
     */
    private suspend fun generateAudioFrames() {
        var frameCount = 0

        while (isRunning.get()) {
            try {
                // 从提供器获取音频数据
                val audioData = customAudioProvider.provideAudioData(
                    requestedBytes = frameSize,
                    audioFormat = audioFormat,
                    channelCount = channelCount,
                    sampleRate = sampleRate
                )

                if (audioData != null && audioData.hasRemaining()) {
                    // 将音频数据发送到WebRTC
                    sendAudioFrame(audioData)
                    frameCount++

                    if (frameCount % 100 == 0) {
                        LKLog.d { "$TAG: 📤 已发送 $frameCount 个音频帧" }
                    }
                } else {
                    // 没有音频数据，发送静音
                    sendSilenceFrame()
                }

                // 按照帧率延迟
                delay(FRAME_DURATION_MS.toLong())

            } catch (e: Exception) {
                LKLog.e(e) { "$TAG: 音频帧生成错误" }
                break
            }
        }

        LKLog.d { "$TAG: 音频帧生成循环结束，总共发送 $frameCount 帧" }
    }

    /**
     * 发送音频帧到WebRTC
     */
    private fun sendAudioFrame(audioData: ByteBuffer) {
        try {
            // 准备音频数据
            val audioArray = ByteArray(audioData.remaining())
            audioData.get(audioArray)

            // 计算时间戳
            val captureTimeMs = System.currentTimeMillis()

            // 发送到WebRTC音频处理链
            // 注意：这里需要直接调用WebRTC的音频处理方法
            // 由于WebRTC的Java API限制，我们需要通过反射或JNI调用
            sendAudioDataToWebRTC(audioArray, captureTimeMs)

        } catch (e: Exception) {
            LKLog.e(e) { "$TAG: 发送音频帧失败" }
        }
    }

    /**
     * 发送静音帧
     */
    private fun sendSilenceFrame() {
        val silenceData = ByteArray(frameSize) // 全零数组代表静音
        sendAudioDataToWebRTC(silenceData, System.currentTimeMillis())
    }

    /**
     * 将音频数据发送到WebRTC处理链
     *
     * 注意：由于WebRTC Java API的限制，这里使用了一个变通方法
     * 实际实现中可能需要通过JNI或其他方式来实现
     */
    private fun sendAudioDataToWebRTC(audioData: ByteArray, captureTimeMs: Long) {
        // TODO: 这里需要实现将音频数据发送到WebRTC的机制
        // 由于WebRTC AudioSource的Java API限制，可能需要：
        // 1. 使用JNI调用native方法
        // 2. 或者通过修改WebRTC源码
        // 3. 或者使用其他变通方法

        LKLog.v { "$TAG: 发送音频数据: ${audioData.size} 字节, 时间戳: $captureTimeMs" }
    }

    override fun dispose() {
        stopAudioGeneration()
        super.dispose()
    }
}

/**
 * 自定义音频源工厂
 */
class CustomAudioSourceFactory {
    companion object {
        /**
         * 创建自定义音频源
         */
        fun createCustomAudioSource(
            customAudioProvider: CustomAudioBufferProvider,
            audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
            channelCount: Int = 2,
            sampleRate: Int = 44100
        ): CustomAudioSource {
            return CustomAudioSource(
                customAudioProvider = customAudioProvider,
                audioFormat = audioFormat,
                channelCount = channelCount,
                sampleRate = sampleRate
            )
        }
    }
}
