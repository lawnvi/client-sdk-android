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

package io.livekit.android.room.participant

import android.media.AudioFormat
import io.livekit.android.audio.*
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions

/**
 * 创建完全独立于麦克风的自定义音频轨道
 * 
 * 这个方法绕过了传统的麦克风音频回调机制，创建一个独立的音频生成器
 * 来驱动自定义音频数据。
 *
 * @param name 轨道名称
 * @param customAudioProvider 自定义音频数据提供器
 * @param options 音频轨道选项
 * @param audioFormat 音频格式
 * @param channelCount 声道数
 * @param sampleRate 采样率
 * @return Triple包含: LocalAudioTrack, CustomAudioMixer, StandaloneCustomAudioGenerator
 */
fun LocalParticipant.createStandaloneCustomAudioTrack(
    name: String = "",
    customAudioProvider: CustomAudioBufferProvider,
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    channelCount: Int = 2,
    sampleRate: Int = 44100
): Triple<LocalAudioTrack, CustomAudioMixer, StandaloneCustomAudioGenerator> {
    
    // 创建普通音频轨道
    val audioTrack = createAudioTrack(name, options)
    
    // 创建自定义音频混音器（只使用自定义音频）
    val customMixer = CustomAudioMixer(
        customAudioProvider = customAudioProvider,
        microphoneGain = 0.0f, // 完全忽略麦克风
        customAudioGain = 1.0f,
        mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
    )
    
    // 设置混音器到音频轨道
    audioTrack.setAudioBufferCallback(customMixer)
    
    // 获取音频缓冲区回调分发器
    val audioBufferCallbackDispatcher = (audioTrack as? io.livekit.android.room.track.LocalAudioTrack)
        ?.let { localTrack ->
            // 通过反射获取AudioBufferCallbackDispatcher
            getAudioBufferCallbackDispatcher(localTrack)
        } ?: throw IllegalStateException("无法获取AudioBufferCallbackDispatcher")
    
    // 创建独立的音频生成器
    val standaloneGenerator = StandaloneCustomAudioGenerator(
        audioBufferCallbackDispatcher = audioBufferCallbackDispatcher,
        customAudioProvider = customAudioProvider,
        audioFormat = audioFormat,
        channelCount = channelCount,
        sampleRate = sampleRate
    )
    
    // 启动混音器
    customMixer.start()
    
    return Triple(audioTrack, customMixer, standaloneGenerator)
}

/**
 * 创建带缓冲区的独立自定义音频轨道
 */
fun LocalParticipant.createStandaloneAudioTrackWithBuffer(
    name: String = "",
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    channelCount: Int = 2,
    sampleRate: Int = 44100,
    loop: Boolean = false
): Triple<LocalAudioTrack, BufferAudioBufferProvider, StandaloneCustomAudioGenerator> {
    
    // 创建缓冲区音频提供器
    val bufferProvider = BufferAudioBufferProvider(
        audioFormat = audioFormat,
        channelCount = channelCount,
        sampleRate = sampleRate,
        loop = loop
    )
    
    // 创建独立的自定义音频轨道
    val (audioTrack, _, standaloneGenerator) = createStandaloneCustomAudioTrack(
        name = name,
        customAudioProvider = bufferProvider,
        audioFormat = audioFormat,
        channelCount = channelCount,
        sampleRate = sampleRate
    )
    
    return Triple(audioTrack, bufferProvider, standaloneGenerator)
}

/**
 * 通过反射获取AudioBufferCallbackDispatcher
 * 这是一个变通方法来访问内部的回调分发器
 */
private fun getAudioBufferCallbackDispatcher(audioTrack: io.livekit.android.room.track.LocalAudioTrack): AudioBufferCallbackDispatcher {
    return try {
        // 使用反射获取私有字段
        val field = audioTrack.javaClass.getDeclaredField("audioBufferCallbackDispatcher")
        field.isAccessible = true
        field.get(audioTrack) as AudioBufferCallbackDispatcher
    } catch (e: Exception) {
        throw IllegalStateException("无法通过反射获取AudioBufferCallbackDispatcher: ${e.message}", e)
    }
}

/**
 * 简化的独立自定义音频轨道创建（推荐使用）
 */
fun LocalParticipant.createIndependentAudioTrack(
    name: String = "",
    customAudioProvider: CustomAudioBufferProvider
): Pair<LocalAudioTrack, StandaloneCustomAudioGenerator> {
    
    val (audioTrack, _, generator) = createStandaloneCustomAudioTrack(
        name = name,
        customAudioProvider = customAudioProvider
    )
    
    return Pair(audioTrack, generator)
}