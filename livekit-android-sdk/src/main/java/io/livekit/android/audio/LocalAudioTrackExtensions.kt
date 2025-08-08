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

import io.livekit.android.room.track.LocalAudioTrack
import java.io.InputStream

/**
 * LocalAudioTrack 扩展方法，用于简化自定义音频输入源的使用
 */

/**
 * 设置基于 InputStream 的自定义音频输入源
 * 
 * @param inputStream 音频数据输入流
 * @param audioConfig 音频配置，必须与当前音频轨道的配置匹配
 * @param replaceOriginal 是否完全替换麦克风音频（true）或与麦克风音频混合（false）
 * @param enableLooping 是否启用循环播放（仅对有限流有效）
 * @param bufferSizeMs 内部缓冲区大小（毫秒）
 * @return CustomAudioInputCallback 实例，用于控制音频输入
 */
fun LocalAudioTrack.setCustomStreamInput(
    inputStream: InputStream,
    audioConfig: CustomAudioInputSource.AudioConfig,
    replaceOriginal: Boolean = false,
    enableLooping: Boolean = false,
    bufferSizeMs: Int = 100
): CustomAudioInputCallback {
    val audioSource = StreamAudioInputSource(
        inputStream = inputStream,
        audioConfig = audioConfig,
        bufferSizeMs = bufferSizeMs,
        enableLooping = enableLooping
    )
    
    val callback = CustomAudioInputCallback(
        audioSource = audioSource,
        replaceOriginal = replaceOriginal
    )
    
    setAudioBufferCallback(callback)
    return callback
}

/**
 * 设置基于 PCM 文件的自定义音频输入源
 * 
 * @param filePath PCM 文件的完整路径
 * @param audioConfig 音频配置，必须与 PCM 文件的格式和当前音频轨道配置匹配
 * @param replaceOriginal 是否完全替换麦克风音频（true）或与麦克风音频混合（false）
 * @param enableLooping 是否启用循环播放
 * @return CustomAudioInputCallback 实例，用于控制音频输入
 */
fun LocalAudioTrack.setCustomPcmFileInput(
    filePath: String,
    audioConfig: CustomAudioInputSource.AudioConfig,
    replaceOriginal: Boolean = false,
    enableLooping: Boolean = false
): CustomAudioInputCallback {
    val audioSource = PcmFileAudioInputSource.fromFile(
        filePath = filePath,
        audioConfig = audioConfig,
        enableLooping = enableLooping
    )
    
    val callback = CustomAudioInputCallback(
        audioSource = audioSource,
        replaceOriginal = replaceOriginal
    )
    
    setAudioBufferCallback(callback)
    return callback
}

/**
 * 清除自定义音频输入源，恢复使用麦克风
 */
fun LocalAudioTrack.clearCustomAudioInput() {
    setAudioBufferCallback(null)
} 