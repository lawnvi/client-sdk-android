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
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

/**
 * 自定义音频输入源使用示例
 * 
 * 这个文件包含了使用自定义音频输入源的各种示例代码
 */
object CustomAudioInputExamples {
    
    /**
     * 示例 1: 使用 PCM 文件作为音频输入源（完全替换麦克风）
     */
    fun usePcmFileAsAudioInput(
        room: Room,
        pcmFilePath: String
    ) {
        // 创建音频配置，匹配您的 PCM 文件格式
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,                                    // 48kHz 采样率
            channelCount = 1,                                       // 单声道
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,          // 16-bit PCM
            bufferSizeMs = 10                                       // 10ms 缓冲区
        )
        
        // 获取或创建音频轨道
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        
        // 设置 PCM 文件作为音频输入源（完全替换麦克风）
        val audioCallback = audioTrack.setCustomPcmFileInput(
            filePath = pcmFilePath,
            audioConfig = audioConfig,
            replaceOriginal = true,        // 完全替换麦克风音频
            enableLooping = true           // 循环播放
        )
        
        // 启动自定义音频输入
        audioCallback.start()
        
        // 发布音频轨道
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            room.localParticipant.publishAudioTrack(audioTrack)
        }
        
        // 记住保存 callback 引用以便后续控制
        // audioCallback.stop()      // 停止自定义音频输入
        // audioCallback.close()     // 清理资源
    }
    
    /**
     * 示例 2: 使用 PCM 文件与麦克风混合
     */
    fun usePcmFileWithMicrophone(
        room: Room,
        pcmFilePath: String
    ) {
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,
            channelCount = 1,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
        
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        
        // 设置 PCM 文件与麦克风混合
        val audioCallback = audioTrack.setCustomPcmFileInput(
            filePath = pcmFilePath,
            audioConfig = audioConfig,
            replaceOriginal = false,       // 与麦克风音频混合
            enableLooping = true
        )
        
        audioCallback.start()
        
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            room.localParticipant.publishAudioTrack(audioTrack)
        }
    }
    
    /**
     * 示例 3: 使用网络音频流
     */
    fun useNetworkAudioStream(
        room: Room,
        networkInputStream: java.io.InputStream
    ) {
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,
            channelCount = 1,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
        
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        
        // 使用网络流作为音频输入源
        val audioCallback = audioTrack.setCustomStreamInput(
            inputStream = networkInputStream,
            audioConfig = audioConfig,
            replaceOriginal = true,
            enableLooping = false,         // 网络流通常不循环
            bufferSizeMs = 200            // 增大缓冲区以应对网络延迟
        )
        
        audioCallback.start()
        
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            room.localParticipant.publishAudioTrack(audioTrack)
        }
    }
    
    /**
     * 示例 4: 动态切换音频源
     */
    fun dynamicAudioSourceSwitching(
        room: Room
    ) {
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,
            channelCount = 1,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
        
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        var currentCallback: CustomAudioInputCallback? = null
        
        // 函数：切换到麦克风
        fun switchToMicrophone() {
            currentCallback?.close()
            audioTrack.clearCustomAudioInput()
            currentCallback = null
        }
        
        // 函数：切换到 PCM 文件
        fun switchToPcmFile(filePath: String) {
            currentCallback?.close()
            currentCallback = audioTrack.setCustomPcmFileInput(
                filePath = filePath,
                audioConfig = audioConfig,
                replaceOriginal = true,
                enableLooping = true
            )
            currentCallback?.start()
        }
        
        // 示例切换序列
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            // 先发布音频轨道
            room.localParticipant.publishAudioTrack(audioTrack)
            
            // 使用麦克风 5 秒
            kotlinx.coroutines.delay(5000)
            
            // 切换到 PCM 文件 10 秒
            switchToPcmFile("/path/to/audio.pcm")
            kotlinx.coroutines.delay(10000)
            
            // 切换回麦克风
            switchToMicrophone()
        }
    }
    
    /**
     * 示例 5: 生成合成音频（正弦波）
     */
    fun generateSineWaveAudio(
        room: Room,
        frequency: Double = 440.0  // A4 音符频率
    ) {
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,
            channelCount = 1,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
        
        // 生成 5 秒的正弦波音频数据
        val durationSeconds = 5
        val numSamples = audioConfig.sampleRate * durationSeconds
        val amplitude = 32767 * 0.3  // 30% 音量
        
        val audioData = ByteArray(numSamples * 2)  // 16-bit = 2 bytes per sample
        
        for (i in 0 until numSamples) {
            val sample = (amplitude * kotlin.math.sin(2.0 * kotlin.math.PI * frequency * i / audioConfig.sampleRate)).toInt().toShort()
            audioData[i * 2] = (sample.toInt() and 0xFF).toByte()         // 低字节
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()  // 高字节
        }
        
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        
        // 使用生成的音频数据
        val audioCallback = audioTrack.setCustomStreamInput(
            inputStream = ByteArrayInputStream(audioData),
            audioConfig = audioConfig,
            replaceOriginal = true,
            enableLooping = true
        )
        
        audioCallback.start()
        
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            room.localParticipant.publishAudioTrack(audioTrack)
        }
    }
    
    /**
     * 示例 6: 监控播放进度（PCM 文件）
     */
    fun monitorPlaybackProgress(
        room: Room,
        pcmFilePath: String
    ) {
        val audioConfig = CustomAudioInputSource.AudioConfig(
            sampleRate = 48000,
            channelCount = 1,
            audioFormat = AudioFormat.ENCODING_PCM_16BIT
        )
        
        // 创建 PCM 文件音频源（直接使用而不是通过扩展方法）
        val pcmSource = PcmFileAudioInputSource.fromFile(
            filePath = pcmFilePath,
            audioConfig = audioConfig,
            enableLooping = false
        )
        
        val audioCallback = CustomAudioInputCallback(
            audioSource = pcmSource,
            replaceOriginal = true
        )
        
        val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()
        audioTrack.setAudioBufferCallback(audioCallback)
        audioCallback.start()
        
        // 监控播放进度
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (pcmSource.isDataAvailable) {
                val progress = pcmSource.playbackProgress
                val duration = pcmSource.durationMs
                println("播放进度: ${(progress * 100).toInt()}%, 总时长: ${duration}ms")
                kotlinx.coroutines.delay(1000)  // 每秒更新一次
            }
            println("音频播放完成")
        }
        
        CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            room.localParticipant.publishAudioTrack(audioTrack)
        }
    }
}

/**
 * 音频格式转换工具
 */
object AudioFormatUtils {
    
    /**
     * 将 8-bit PCM 转换为 16-bit PCM
     */
    fun convert8BitTo16Bit(input8Bit: ByteArray): ByteArray {
        val output16Bit = ByteArray(input8Bit.size * 2)
        for (i in input8Bit.indices) {
            val sample16 = ((input8Bit[i].toInt() and 0xFF) - 128) * 256  // 转换为有符号 16-bit
            output16Bit[i * 2] = (sample16 and 0xFF).toByte()
            output16Bit[i * 2 + 1] = ((sample16 shr 8) and 0xFF).toByte()
        }
        return output16Bit
    }
    
    /**
     * 将立体声转换为单声道（简单平均）
     */
    fun stereoToMono16Bit(stereoData: ByteArray): ByteArray {
        val monoData = ByteArray(stereoData.size / 2)
        for (i in 0 until stereoData.size step 4) {  // 每 4 个字节是一个立体声样本
            val leftSample = (stereoData[i].toInt() and 0xFF) or ((stereoData[i + 1].toInt() and 0xFF) shl 8)
            val rightSample = (stereoData[i + 2].toInt() and 0xFF) or ((stereoData[i + 3].toInt() and 0xFF) shl 8)
            val monoSample = (leftSample + rightSample) / 2
            monoData[i / 2] = (monoSample and 0xFF).toByte()
            monoData[i / 2 + 1] = ((monoSample shr 8) and 0xFF).toByte()
        }
        return monoData
    }
} 