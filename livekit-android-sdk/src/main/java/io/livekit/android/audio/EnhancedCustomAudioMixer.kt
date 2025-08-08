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
import android.util.Log
import io.livekit.android.util.LKLog
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 增强版自定义音频混音器
 * 
 * 修复了原版CustomAudioMixer的激活状态管理问题，
 * 提供更可靠的自定义音频输入功能。
 */
class EnhancedCustomAudioMixer(
    private val customAudioProvider: CustomAudioBufferProvider,
    private val microphoneGain: Float = 0.0f,  // 默认静音麦克风
    private val customAudioGain: Float = 1.0f,
    private val mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
) : MixerAudioBufferCallback() {

    companion object {
        private const val TAG = "EnhancedCustomAudioMixer"
        private const val DEBUG_LOG_INTERVAL = 100 // 每100次请求打印一次调试信息
    }

    // 状态管理
    private val isActive = AtomicBoolean(false)
    private val totalBuffersRequested = AtomicLong(0)
    private val totalCustomBuffersProvided = AtomicLong(0)
    private val totalSilenceBuffersGenerated = AtomicLong(0)
    
    // 性能统计
    private var lastDebugTime = System.currentTimeMillis()
    private var processingErrors = 0

    /**
     * 启动混音器
     */
    fun start(): Boolean {
        return try {
            LKLog.d { "$TAG: 🚀 启动增强版混音器" }
            LKLog.d { "$TAG: 配置 - 混音模式: $mixMode, 麦克风增益: $microphoneGain, 自定义音频增益: $customAudioGain" }
            
            // 启动自定义音频提供器
            customAudioProvider.start()
            
            // 验证提供器状态
            val hasData = customAudioProvider.hasMoreData()
            LKLog.d { "$TAG: 音频提供器状态 - 有数据: $hasData, 类型: ${customAudioProvider.javaClass.simpleName}" }
            
            // 激活混音器
            isActive.set(true)
            
            LKLog.i { "$TAG: ✅ 混音器启动成功" }
            true
        } catch (e: Exception) {
            LKLog.e(e) { "$TAG: ❌ 混音器启动失败" }
            false
        }
    }

    /**
     * 停止混音器
     */
    fun stop() {
        if (isActive.compareAndSet(true, false)) {
            LKLog.d { "$TAG: ⏹️ 停止混音器" }
            
            try {
                customAudioProvider.stop()
                
                // 打印统计信息
                val totalRequested = totalBuffersRequested.get()
                val totalProvided = totalCustomBuffersProvided.get()
                val totalSilence = totalSilenceBuffersGenerated.get()
                val successRate = if (totalRequested > 0) (totalProvided * 100.0 / totalRequested) else 0.0
                
                LKLog.i { "$TAG: 📊 混音器统计 - 总请求: $totalRequested, 自定义提供: $totalProvided, 静音生成: $totalSilence, 成功率: ${String.format("%.1f", successRate)}%" }
                
                if (processingErrors > 0) {
                    LKLog.w { "$TAG: ⚠️ 处理错误次数: $processingErrors" }
                }
                
            } catch (e: Exception) {
                LKLog.e(e) { "$TAG: 停止混音器时发生错误" }
            }
        }
    }

    /**
     * 检查混音器是否激活
     */
    fun isActivated(): Boolean = isActive.get()

    /**
     * 获取详细状态信息
     */
    fun getStatusInfo(): String {
        val totalRequested = totalBuffersRequested.get()
        val totalProvided = totalCustomBuffersProvided.get()
        val successRate = if (totalRequested > 0) (totalProvided * 100.0 / totalRequested) else 0.0
        
        return """
            增强版混音器状态:
            - 激活状态: ${isActive.get()}
            - 混音模式: $mixMode
            - 总缓冲区请求: $totalRequested
            - 自定义音频提供: $totalProvided
            - 静音缓冲区生成: ${totalSilenceBuffersGenerated.get()}
            - 成功率: ${String.format("%.1f", successRate)}%
            - 处理错误: $processingErrors
            - 提供器类型: ${customAudioProvider.javaClass.simpleName}
            - 提供器有数据: ${customAudioProvider.hasMoreData()}
        """.trimIndent()
    }

    override fun onBufferRequest(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        val requestCount = totalBuffersRequested.incrementAndGet()
        
        // 检查是否激活
        if (!isActive.get()) {
            if (requestCount <= 5) {
                LKLog.w { "$TAG: ⚠️ 混音器未激活，忽略缓冲区请求 #$requestCount" }
            }
            return null
        }

        // 定期打印调试信息
        if (requestCount % DEBUG_LOG_INTERVAL == 0L) {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastDebug = currentTime - lastDebugTime
            val requestsPerSecond = if (timeSinceLastDebug > 0) (DEBUG_LOG_INTERVAL * 1000.0 / timeSinceLastDebug) else 0.0
            
            LKLog.d { "$TAG: 📈 处理状态 #$requestCount - 请求频率: ${String.format("%.1f", requestsPerSecond)}/s, 格式: $audioFormat, 声道: $channelCount, 采样率: $sampleRate, 请求字节: ${originalBuffer.remaining()}" }
            lastDebugTime = currentTime
        }

        return try {
            // 根据混音模式处理
            when (mixMode) {
                CustomAudioMixer.MixMode.CUSTOM_ONLY -> {
                    handleCustomOnlyMode(originalBuffer, audioFormat, channelCount, sampleRate, captureTimeNs)
                }
                CustomAudioMixer.MixMode.REPLACE -> {
                    handleReplaceMode(originalBuffer, audioFormat, channelCount, sampleRate, captureTimeNs)
                }
                CustomAudioMixer.MixMode.ADDITIVE -> {
                    handleAdditiveMode(originalBuffer, audioFormat, channelCount, sampleRate, captureTimeNs)
                }
            }
        } catch (e: Exception) {
            processingErrors++
            LKLog.e(e) { "$TAG: ❌ 处理音频缓冲区时发生错误 #$requestCount" }
            null
        }
    }

    /**
     * 处理纯自定义音频模式
     */
    private fun handleCustomOnlyMode(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        captureTimeNs: Long
    ): BufferResponse {
        val requestedBytes = originalBuffer.remaining()
        
        // 获取自定义音频数据
        val customAudioBuffer = customAudioProvider.provideAudioData(
            requestedBytes = requestedBytes,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate
        )

        return if (customAudioBuffer != null && customAudioBuffer.hasRemaining()) {
            // 有自定义音频数据
            totalCustomBuffersProvided.incrementAndGet()
            
            val processedBuffer = if (customAudioGain != 1.0f) {
                applyGain(customAudioBuffer, customAudioGain, audioFormat)
            } else {
                customAudioBuffer
            }
            
            BufferResponse(
                processedBuffer,
                customAudioProvider.getCaptureTimeNs().takeIf { it != 0L } ?: captureTimeNs
            )
        } else {
            // 没有自定义音频，生成静音
            totalSilenceBuffersGenerated.incrementAndGet()
            BufferResponse(
                createSilenceBuffer(requestedBytes),
                captureTimeNs
            )
        }
    }

    /**
     * 处理替换模式
     */
    private fun handleReplaceMode(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        val requestedBytes = originalBuffer.remaining()
        
        val customAudioBuffer = customAudioProvider.provideAudioData(
            requestedBytes = requestedBytes,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate
        )

        return if (customAudioBuffer != null && customAudioBuffer.hasRemaining()) {
            // 有自定义音频，替换原始音频
            totalCustomBuffersProvided.incrementAndGet()
            
            val processedBuffer = if (customAudioGain != 1.0f) {
                applyGain(customAudioBuffer, customAudioGain, audioFormat)
            } else {
                customAudioBuffer
            }
            
            BufferResponse(
                processedBuffer,
                customAudioProvider.getCaptureTimeNs().takeIf { it != 0L } ?: captureTimeNs
            )
        } else {
            // 没有自定义音频，使用原始音频（可能应用增益）
            if (microphoneGain != 1.0f) {
                BufferResponse(
                    applyGain(originalBuffer, microphoneGain, audioFormat),
                    captureTimeNs
                )
            } else {
                null // 使用原始缓冲区
            }
        }
    }

    /**
     * 处理加法混音模式
     */
    private fun handleAdditiveMode(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        val requestedBytes = originalBuffer.remaining()
        
        val customAudioBuffer = customAudioProvider.provideAudioData(
            requestedBytes = requestedBytes,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate
        )

        return if (customAudioBuffer != null && customAudioBuffer.hasRemaining()) {
            // 混合自定义音频和原始音频
            totalCustomBuffersProvided.incrementAndGet()
            
            val micBuffer = if (microphoneGain != 1.0f) {
                applyGain(originalBuffer, microphoneGain, audioFormat)
            } else {
                originalBuffer.duplicate()
            }

            val customBuffer = if (customAudioGain != 1.0f) {
                applyGain(customAudioBuffer, customAudioGain, audioFormat)
            } else {
                customAudioBuffer
            }

            val mixedBuffer = mixBuffers(micBuffer, customBuffer, audioFormat)
            
            BufferResponse(
                mixedBuffer,
                customAudioProvider.getCaptureTimeNs().takeIf { it != 0L } ?: captureTimeNs
            )
        } else {
            // 没有自定义音频，只使用原始音频（可能应用增益）
            if (microphoneGain != 1.0f) {
                BufferResponse(
                    applyGain(originalBuffer, microphoneGain, audioFormat),
                    captureTimeNs
                )
            } else {
                null // 使用原始缓冲区
            }
        }
    }

    /**
     * 应用增益到音频缓冲区
     */
    private fun applyGain(buffer: ByteBuffer, gain: Float, audioFormat: Int): ByteBuffer {
        if (gain == 1.0f) {
            return buffer.duplicate()
        }

        val outputBuffer = ByteBuffer.allocate(buffer.remaining())
        outputBuffer.order(buffer.order())

        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val inputShortBuffer = buffer.asShortBuffer()
                val outputShortBuffer = outputBuffer.asShortBuffer()
                
                while (inputShortBuffer.hasRemaining()) {
                    val sample = inputShortBuffer.get()
                    val scaledSample = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outputShortBuffer.put(scaledSample.toShort())
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (buffer.hasRemaining()) {
                    val sample = buffer.get()
                    val scaledSample = (sample * gain).toInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt())
                    outputBuffer.put(scaledSample.toByte())
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val inputFloatBuffer = buffer.asFloatBuffer()
                val outputFloatBuffer = outputBuffer.asFloatBuffer()
                
                while (inputFloatBuffer.hasRemaining()) {
                    val sample = inputFloatBuffer.get()
                    val scaledSample = (sample * gain).coerceIn(-1.0f, 1.0f)
                    outputFloatBuffer.put(scaledSample)
                }
            }
            else -> {
                // 不支持的格式，返回原始缓冲区
                return buffer.duplicate()
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    /**
     * 混合两个音频缓冲区
     */
    private fun mixBuffers(buffer1: ByteBuffer, buffer2: ByteBuffer, audioFormat: Int): ByteBuffer {
        val size = minOf(buffer1.remaining(), buffer2.remaining())
        val outputBuffer = ByteBuffer.allocate(size)
        outputBuffer.order(buffer1.order())

        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shortBuffer1 = buffer1.asShortBuffer()
                val shortBuffer2 = buffer2.asShortBuffer()
                val outputShortBuffer = outputBuffer.asShortBuffer()
                
                repeat(size / 2) {
                    if (shortBuffer1.hasRemaining() && shortBuffer2.hasRemaining()) {
                        val sample1 = shortBuffer1.get().toInt()
                        val sample2 = shortBuffer2.get().toInt()
                        val mixed = (sample1 + sample2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        outputShortBuffer.put(mixed.toShort())
                    }
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                repeat(size) {
                    if (buffer1.hasRemaining() && buffer2.hasRemaining()) {
                        val sample1 = buffer1.get().toInt()
                        val sample2 = buffer2.get().toInt()
                        val mixed = (sample1 + sample2).coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt())
                        outputBuffer.put(mixed.toByte())
                    }
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatBuffer1 = buffer1.asFloatBuffer()
                val floatBuffer2 = buffer2.asFloatBuffer()
                val outputFloatBuffer = outputBuffer.asFloatBuffer()
                
                repeat(size / 4) {
                    if (floatBuffer1.hasRemaining() && floatBuffer2.hasRemaining()) {
                        val sample1 = floatBuffer1.get()
                        val sample2 = floatBuffer2.get()
                        val mixed = (sample1 + sample2).coerceIn(-1.0f, 1.0f)
                        outputFloatBuffer.put(mixed)
                    }
                }
            }
            else -> {
                // 不支持的格式，返回第一个缓冲区
                return buffer1.duplicate()
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    /**
     * 创建静音缓冲区
     */
    private fun createSilenceBuffer(size: Int): ByteBuffer {
        val silenceBuffer = ByteBuffer.allocate(size)
        // ByteBuffer默认填充零，这就是静音
        repeat(size) { silenceBuffer.put(0) }
        silenceBuffer.flip()
        return silenceBuffer
    }
}