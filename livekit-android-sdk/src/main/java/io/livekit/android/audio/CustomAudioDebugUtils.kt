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
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * 自定义音频调试工具
 */
object CustomAudioDebugUtils {
    private const val TAG = "CustomAudioDebug"
    
    /**
     * 音频缓冲区分析器
     */
    class AudioBufferAnalyzer {
        private val totalBuffers = AtomicLong(0)
        private val totalBytes = AtomicLong(0)
        private val silentBuffers = AtomicLong(0)
        private var startTime = System.currentTimeMillis()
        
        fun analyzeBuffer(buffer: ByteBuffer, audioFormat: Int): BufferAnalysis {
            val bufferCount = totalBuffers.incrementAndGet()
            val bytesCount = totalBytes.addAndGet(buffer.remaining().toLong())
            
            val analysis = when (audioFormat) {
                AudioFormat.ENCODING_PCM_16BIT -> analyzePCM16Buffer(buffer)
                AudioFormat.ENCODING_PCM_8BIT -> analyzePCM8Buffer(buffer)
                AudioFormat.ENCODING_PCM_FLOAT -> analyzePCMFloatBuffer(buffer)
                else -> BufferAnalysis(isSilent = true, peakAmplitude = 0.0, rmsLevel = 0.0, dynamicRange = 0.0)
            }
            
            if (analysis.isSilent) {
                silentBuffers.incrementAndGet()
            }
            
            return analysis.copy(
                bufferNumber = bufferCount,
                totalBytes = bytesCount,
                elapsedTimeMs = System.currentTimeMillis() - startTime
            )
        }
        
        private fun analyzePCM16Buffer(buffer: ByteBuffer): BufferAnalysis {
            val shortBuffer = buffer.asShortBuffer()
            val samples = mutableListOf<Short>()
            
            while (shortBuffer.hasRemaining()) {
                samples.add(shortBuffer.get())
            }
            
            if (samples.isEmpty()) {
                return BufferAnalysis(isSilent = true, peakAmplitude = 0.0, rmsLevel = 0.0, dynamicRange = 0.0)
            }
            
            val maxSample = samples.maxOrNull()?.toInt() ?: 0
            val minSample = samples.minOrNull()?.toInt() ?: 0
            val peakAmplitude = maxOf(kotlin.math.abs(maxSample), kotlin.math.abs(minSample))
            
            val rms = kotlin.math.sqrt(samples.map { (it.toDouble() * it.toDouble()) }.average())
            val dynamicRange = if (minSample != maxSample) 20.0 * kotlin.math.log10(peakAmplitude.toDouble() / kotlin.math.abs(minSample).coerceAtLeast(1)) else 0.0
            
            val isSilent = peakAmplitude < 100 // 阈值可调
            
            return BufferAnalysis(
                isSilent = isSilent,
                peakAmplitude = peakAmplitude / Short.MAX_VALUE.toDouble(),
                rmsLevel = rms / Short.MAX_VALUE.toDouble(),
                dynamicRange = dynamicRange,
                sampleCount = samples.size
            )
        }
        
        private fun analyzePCM8Buffer(buffer: ByteBuffer): BufferAnalysis {
            val samples = mutableListOf<Byte>()
            
            while (buffer.hasRemaining()) {
                samples.add(buffer.get())
            }
            
            if (samples.isEmpty()) {
                return BufferAnalysis(isSilent = true, peakAmplitude = 0.0, rmsLevel = 0.0, dynamicRange = 0.0)
            }
            
            val maxSample = samples.maxOrNull()?.toInt() ?: 0
            val minSample = samples.minOrNull()?.toInt() ?: 0
            val peakAmplitude = maxOf(kotlin.math.abs(maxSample), kotlin.math.abs(minSample))
            
            val rms = kotlin.math.sqrt(samples.map { (it.toDouble() * it.toDouble()) }.average())
            val dynamicRange = if (minSample != maxSample) 20.0 * kotlin.math.log10(peakAmplitude.toDouble() / kotlin.math.abs(minSample).coerceAtLeast(1)) else 0.0
            
            val isSilent = peakAmplitude < 10
            
            return BufferAnalysis(
                isSilent = isSilent,
                peakAmplitude = peakAmplitude / 128.0,
                rmsLevel = rms / 128.0,
                dynamicRange = dynamicRange,
                sampleCount = samples.size
            )
        }
        
        private fun analyzePCMFloatBuffer(buffer: ByteBuffer): BufferAnalysis {
            val floatBuffer = buffer.asFloatBuffer()
            val samples = mutableListOf<Float>()
            
            while (floatBuffer.hasRemaining()) {
                samples.add(floatBuffer.get())
            }
            
            if (samples.isEmpty()) {
                return BufferAnalysis(isSilent = true, peakAmplitude = 0.0, rmsLevel = 0.0, dynamicRange = 0.0)
            }
            
            val maxSample = samples.maxOrNull() ?: 0f
            val minSample = samples.minOrNull() ?: 0f
            val peakAmplitude = maxOf(kotlin.math.abs(maxSample), kotlin.math.abs(minSample))
            
            val rms = kotlin.math.sqrt(samples.map { (it.toDouble() * it.toDouble()) }.average())
            val dynamicRange = if (minSample != maxSample) 20.0 * kotlin.math.log10(peakAmplitude.toDouble() / kotlin.math.abs(minSample).coerceAtLeast(0.001f)) else 0.0
            
            val isSilent = peakAmplitude < 0.01f
            
            return BufferAnalysis(
                isSilent = isSilent,
                peakAmplitude = peakAmplitude.toDouble(),
                rmsLevel = rms,
                dynamicRange = dynamicRange,
                sampleCount = samples.size
            )
        }
        
        fun getStatistics(): String {
            val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            val buffersPerSecond = if (elapsedSeconds > 0) totalBuffers.get() / elapsedSeconds else 0.0
            val bytesPerSecond = if (elapsedSeconds > 0) totalBytes.get() / elapsedSeconds else 0.0
            val silentPercentage = if (totalBuffers.get() > 0) silentBuffers.get() * 100.0 / totalBuffers.get() else 0.0
            
            return """
                音频缓冲区统计:
                - 总缓冲区数: ${totalBuffers.get()}
                - 总字节数: ${totalBytes.get()}
                - 静音缓冲区: ${silentBuffers.get()} (${String.format("%.1f", silentPercentage)}%)
                - 运行时间: ${String.format("%.1f", elapsedSeconds)}s
                - 缓冲区频率: ${String.format("%.1f", buffersPerSecond)}/s
                - 数据速率: ${String.format("%.1f", bytesPerSecond / 1024)} KB/s
            """.trimIndent()
        }
    }
    
    /**
     * 缓冲区分析结果
     */
    data class BufferAnalysis(
        val isSilent: Boolean,
        val peakAmplitude: Double,
        val rmsLevel: Double,
        val dynamicRange: Double,
        val sampleCount: Int = 0,
        val bufferNumber: Long = 0,
        val totalBytes: Long = 0,
        val elapsedTimeMs: Long = 0
    ) {
        fun getDescription(): String {
            return "缓冲区 #$bufferNumber: ${if (isSilent) "静音" else "有声"}, " +
                   "峰值: ${String.format("%.3f", peakAmplitude)}, " +
                   "RMS: ${String.format("%.3f", rmsLevel)}, " +
                   "动态范围: ${String.format("%.1f", dynamicRange)}dB, " +
                   "样本数: $sampleCount"
        }
    }
    
    /**
     * 音频格式信息
     */
    fun getAudioFormatInfo(audioFormat: Int, channelCount: Int, sampleRate: Int): String {
        val formatName = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> "PCM 16位"
            AudioFormat.ENCODING_PCM_8BIT -> "PCM 8位"
            AudioFormat.ENCODING_PCM_FLOAT -> "PCM Float"
            AudioFormat.ENCODING_DEFAULT -> "默认格式"
            else -> "未知格式($audioFormat)"
        }
        
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        
        val bytesPerSecond = sampleRate * channelCount * bytesPerSample
        val kilobytesPerSecond = bytesPerSecond / 1024.0
        
        return """
            音频格式信息:
            - 格式: $formatName
            - 声道数: $channelCount
            - 采样率: ${sampleRate}Hz
            - 每样本字节: $bytesPerSample
            - 数据速率: ${String.format("%.1f", kilobytesPerSecond)} KB/s
            - 位深度: ${bytesPerSample * 8}位
        """.trimIndent()
    }
    
    /**
     * 自定义音频提供器监控器
     */
    class CustomAudioProviderMonitor(
        private val provider: CustomAudioBufferProvider,
        private val monitoringInterval: Long = 5000L // 5秒
    ) {
        private var monitoringJob: Job? = null
        private val monitoringScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        
        fun startMonitoring() {
            stopMonitoring()
            
            monitoringJob = monitoringScope.launch {
                while (isActive) {
                    try {
                        val hasData = provider.hasMoreData()
                        val captureTime = provider.getCaptureTimeNs()
                        val currentTime = System.currentTimeMillis()
                        
                        LKLog.d { 
                            "$TAG: 📊 提供器监控 - " +
                            "有数据: $hasData, " +
                            "捕获时间: ${if (captureTime != 0L) "有效" else "无效"}, " +
                            "类型: ${provider.javaClass.simpleName}"
                        }
                        
                        // 如果是TestAudioProvider，获取详细信息
                        if (provider is TestAudioProvider) {
                            LKLog.d { provider.getGeneratorInfo() }
                        }
                        
                        delay(monitoringInterval)
                    } catch (e: Exception) {
                        LKLog.e(e) { "$TAG: 监控过程中发生错误" }
                        delay(1000) // 出错时短暂延迟
                    }
                }
            }
        }
        
        fun stopMonitoring() {
            monitoringJob?.cancel()
            monitoringJob = null
        }
    }
    
    /**
     * 系统音频信息检查器
     */
    fun checkSystemAudioInfo(): String {
        return try {
            val audioManager = android.media.AudioManager::class.java
            
            """
                系统音频信息:
                - AudioFormat.ENCODING_PCM_16BIT: ${AudioFormat.ENCODING_PCM_16BIT}
                - AudioFormat.ENCODING_PCM_8BIT: ${AudioFormat.ENCODING_PCM_8BIT}
                - AudioFormat.ENCODING_PCM_FLOAT: ${AudioFormat.ENCODING_PCM_FLOAT}
                - AudioFormat.ENCODING_DEFAULT: ${AudioFormat.ENCODING_DEFAULT}
                - 系统时间: ${System.currentTimeMillis()}
                - 纳秒时间: ${System.nanoTime()}
            """.trimIndent()
        } catch (e: Exception) {
            "获取系统音频信息时发生错误: ${e.message}"
        }
    }
    
    /**
     * 调试日志辅助函数
     */
    fun logBufferInfo(
        buffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        tag: String = TAG
    ) {
        val formatInfo = getAudioFormatInfo(audioFormat, channelCount, sampleRate)
        val bufferSize = buffer.remaining()
        val durationMs = calculateBufferDurationMs(bufferSize, audioFormat, channelCount, sampleRate)
        
        Log.d(tag, """
            音频缓冲区信息:
            - 大小: $bufferSize 字节
            - 时长: ${String.format("%.1f", durationMs)}ms
            $formatInfo
        """.trimIndent())
    }
    
    private fun calculateBufferDurationMs(
        bufferSize: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): Double {
        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }
        
        val samplesPerChannel = bufferSize / (bytesPerSample * channelCount)
        return (samplesPerChannel * 1000.0) / sampleRate
    }
}