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
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * 自定义音频输入源接口
 * 支持从不同数据源（如 Stream、PCM 文件等）读取音频数据
 */
interface CustomAudioInputSource : Closeable {
    
    /**
     * 音频配置信息
     */
    data class AudioConfig(
        val sampleRate: Int = 48000,        // 采样率，默认 48kHz
        val channelCount: Int = 1,          // 声道数，默认单声道
        val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,  // 音频格式，默认 16-bit PCM
        val bufferSizeMs: Int = 10          // 缓冲区大小（毫秒），默认 10ms
    ) {
        /**
         * 计算每个样本的字节数
         */
        fun getBytesPerSample(): Int {
            return when (audioFormat) {
                AudioFormat.ENCODING_PCM_8BIT -> 1
                AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_DEFAULT -> 2
                AudioFormat.ENCODING_PCM_FLOAT -> 4
                else -> throw IllegalArgumentException("Unsupported audio format: $audioFormat")
            }
        }
        
        /**
         * 计算每次读取的缓冲区大小（字节）
         */
        fun getBufferSizeBytes(): Int {
            val samplesPerBuffer = sampleRate * channelCount * bufferSizeMs / 1000
            return samplesPerBuffer * getBytesPerSample()
        }
    }
    
    /**
     * 获取音频配置
     */
    val audioConfig: AudioConfig
    
    /**
     * 是否有可用的音频数据
     */
    val isDataAvailable: Boolean
    
    /**
     * 开始读取音频数据
     * 在开始使用前必须调用此方法
     */
    fun start()
    
    /**
     * 停止读取音频数据
     */
    fun stop()
    
    /**
     * 读取音频数据到指定的缓冲区
     * 
     * @param buffer 目标缓冲区
     * @param bytesToRead 需要读取的字节数
     * @return 实际读取的字节数，如果没有数据可读则返回 0
     */
    fun readData(buffer: ByteBuffer, bytesToRead: Int): Int
    
    /**
     * 获取当前时间戳（纳秒）
     */
    fun getCurrentTimestampNs(): Long = System.nanoTime()
}

/**
 * 自定义音频输入回调
 * 使用自定义音频输入源替换或混合麦克风音频
 */
class CustomAudioInputCallback(
    private val audioSource: CustomAudioInputSource,
    private val replaceOriginal: Boolean = false  // true: 完全替换麦克风音频, false: 与麦克风音频混合
) : MixerAudioBufferCallback() {
    
    private var isStarted = false
    
    /**
     * 开始音频输入
     */
    fun start() {
        if (!isStarted) {
            audioSource.start()
            isStarted = true
            LKLog.d { "CustomAudioInputCallback started" }
        }
    }
    
    /**
     * 停止音频输入
     */
    fun stop() {
        if (isStarted) {
            audioSource.stop()
            isStarted = false
            LKLog.d { "CustomAudioInputCallback stopped" }
        }
    }
    
    override fun onBufferRequest(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        if (!isStarted || !audioSource.isDataAvailable) {
            return null
        }
        
        val config = audioSource.audioConfig
        
        // 验证音频格式是否匹配
        if (config.audioFormat != audioFormat || 
            config.channelCount != channelCount || 
            config.sampleRate != sampleRate) {
            LKLog.w { 
                "Audio format mismatch: expected (${config.audioFormat}, ${config.channelCount}, ${config.sampleRate}), " +
                "got ($audioFormat, $channelCount, $sampleRate)" 
            }
            return null
        }
        
        // 创建自定义音频缓冲区
        val customBuffer = ByteBuffer.allocateDirect(bytesRead)
        val readBytes = audioSource.readData(customBuffer, bytesRead)
        
        if (readBytes <= 0) {
            return null
        }
        
        customBuffer.position(0)
        customBuffer.limit(readBytes)
        
        if (replaceOriginal) {
            // 完全替换原始音频
            originalBuffer.position(0)
            originalBuffer.put(customBuffer)
            return BufferResponse(null, audioSource.getCurrentTimestampNs())
        } else {
            // 与原始音频混合
            return BufferResponse(customBuffer, audioSource.getCurrentTimestampNs())
        }
    }
    
    fun close() {
        stop()
        audioSource.close()
    }
} 