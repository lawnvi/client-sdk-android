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
import java.nio.ByteBuffer

/**
 * @suppress
 */
class AudioBufferCallbackDispatcher : livekit.org.webrtc.audio.JavaAudioDeviceModule.AudioBufferCallback {
    var bufferCallback: AudioBufferCallback? = null
    private var callCount = 0

    override fun onBuffer(buffer: ByteBuffer, audioFormat: Int, channelCount: Int, sampleRate: Int, bytesRead: Int, captureTimeNs: Long): Long {
        callCount++
        
        // 调试：每100次调用打印一次状态
        if (callCount % 100 == 0) {
            android.util.Log.d("AudioBufferCallbackDispatcher", "🎤 WebRTC音频回调 #$callCount - 格式:$audioFormat 声道:$channelCount 采样率:$sampleRate 缓冲区:${buffer.remaining()}字节")
        }
        
        val callback = bufferCallback
        if (callback == null) {
            if (callCount <= 5) { // 只在前5次打印警告
                android.util.Log.w("AudioBufferCallbackDispatcher", "⚠️ 音频回调被调用但没有设置bufferCallback处理器")
            }
            return 0L
        }
        
        return callback.onBuffer(
            buffer = buffer,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate,
            bytesRead = bytesRead,
            captureTimeNs = captureTimeNs,
        )
    }
    
    fun getCallCount(): Int = callCount
    
    /**
     * 手动触发音频回调 - 用于独立的自定义音频输入
     * 
     * 这个方法允许外部代码直接驱动音频回调，绕过WebRTC的麦克风输入机制
     */
    fun triggerManualAudioCallback(
        audioData: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        captureTimeNs: Long = System.nanoTime()
    ): Long {
        callCount++
        
        if (callCount % 100 == 0) {
            android.util.Log.d("AudioBufferCallbackDispatcher", "🔧 手动音频回调 #$callCount - 格式:$audioFormat 声道:$channelCount 采样率:$sampleRate 数据:${audioData.remaining()}字节")
        }
        
        val callback = bufferCallback
        if (callback == null) {
            if (callCount <= 5) {
                android.util.Log.w("AudioBufferCallbackDispatcher", "⚠️ 手动音频回调被调用但没有设置bufferCallback处理器")
            }
            return 0L
        }
        
        return callback.onBuffer(
            buffer = audioData,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate,
            bytesRead = audioData.remaining(),
            captureTimeNs = captureTimeNs
        )
    }
}

interface AudioBufferCallback {
    /**
     * Called when new audio samples are ready.
     * @param buffer the buffer of audio bytes. Changes to this buffer will be published on the audio track.
     * @param audioFormat the audio encoding. See [AudioFormat.ENCODING_PCM_8BIT],
     * [AudioFormat.ENCODING_PCM_16BIT], and [AudioFormat.ENCODING_PCM_FLOAT]. Note
     * that [AudioFormat.ENCODING_DEFAULT] defaults to PCM-16bit.
     * @param channelCount
     * @param sampleRate
     * @param bytesRead the byte count originally read from the microphone.
     * @param captureTimeNs the capture timestamp of the original audio data in nanoseconds.
     * @return the capture timestamp in nanoseconds. Return 0 if not available.
     */
    fun onBuffer(buffer: ByteBuffer, audioFormat: Int, channelCount: Int, sampleRate: Int, bytesRead: Int, captureTimeNs: Long): Long
}
