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
 * A mixer that combines custom audio sources with microphone audio.
 *
 * This class allows you to mix custom audio (from files or buffers) with the
 * microphone input, giving you full control over the audio composition.
 */
class CustomAudioMixer(
    private val customAudioProvider: CustomAudioBufferProvider? = null,
    private val microphoneGain: Float = 1.0f,
    private val customAudioGain: Float = 1.0f,
    private val mixMode: MixMode = MixMode.ADDITIVE
) : MixerAudioBufferCallback() {

    enum class MixMode {
        /**
         * Mix the custom audio additively with the microphone audio.
         * Both sources will be audible simultaneously.
         */
        ADDITIVE,

        /**
         * Replace microphone audio with custom audio when available.
         * Falls back to microphone when no custom audio is available.
         */
        REPLACE,

        /**
         * Only use custom audio, ignore microphone completely.
         */
        CUSTOM_ONLY
    }

    private var isActive = false
    private var totalBuffersRequested = 0
    private var totalCustomBuffersProvided = 0

    /**
     * Start the custom audio mixing.
     */
    fun start() {
        isActive = true
        customAudioProvider?.start()
        android.util.Log.d("CustomAudioMixer", "🎵 Mixer started - mode: $mixMode, provider: ${customAudioProvider?.javaClass?.simpleName}, micGain: $microphoneGain, customGain: $customAudioGain")
        
        // 验证提供器状态
        customAudioProvider?.let { provider ->
            val hasData = provider.hasMoreData()
            android.util.Log.d("CustomAudioMixer", "🔍 Provider status: hasMoreData=$hasData")
        }
    }

    /**
     * Stop the custom audio mixing.
     */
    fun stop() {
        isActive = false
        customAudioProvider?.stop()
        android.util.Log.d("CustomAudioMixer", "Mixer stopped - total buffers requested: $totalBuffersRequested, custom provided: $totalCustomBuffersProvided")
    }

    /**
     * Get debug information about the mixer state.
     */
    fun getDebugInfo(): String {
        return "CustomAudioMixer - Active: $isActive, Mode: $mixMode, " +
               "MicGain: $microphoneGain, CustomGain: $customAudioGain, " +
               "BuffersRequested: $totalBuffersRequested, CustomProvided: $totalCustomBuffersProvided, " +
               "Provider: ${customAudioProvider?.javaClass?.simpleName}"
    }

    override fun onBufferRequest(
        originalBuffer: ByteBuffer,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int,
        bytesRead: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        totalBuffersRequested++

        if (!isActive) {
            android.util.Log.d("CustomAudioMixer", "Buffer request #$totalBuffersRequested - INACTIVE, returning null")
            return null
        }

        val requestedBytes = originalBuffer.remaining()
        android.util.Log.d("CustomAudioMixer", "Buffer request #$totalBuffersRequested - Mode: $mixMode, Requested: $requestedBytes bytes, Format: $audioFormat, Channels: $channelCount, Rate: $sampleRate")

        val customAudioBuffer = customAudioProvider?.provideAudioData(
            requestedBytes = requestedBytes,
            audioFormat = audioFormat,
            channelCount = channelCount,
            sampleRate = sampleRate
        )

        if (customAudioBuffer != null) {
            totalCustomBuffersProvided++
            android.util.Log.d("CustomAudioMixer", "Custom audio provided: ${customAudioBuffer.remaining()} bytes (total provided: $totalCustomBuffersProvided)")
        } else {
            android.util.Log.d("CustomAudioMixer", "No custom audio available")
        }

        val result = when (mixMode) {
            MixMode.ADDITIVE -> {
                handleAdditiveMix(originalBuffer, customAudioBuffer, audioFormat, captureTimeNs)
            }
            MixMode.REPLACE -> {
                handleReplaceMix(originalBuffer, customAudioBuffer, captureTimeNs)
            }
            MixMode.CUSTOM_ONLY -> {
                // In CUSTOM_ONLY mode, completely ignore originalBuffer (microphone input)
                // and only use custom audio or silence
                handleCustomOnlyMix(customAudioBuffer, captureTimeNs, originalBuffer.remaining())
            }
        }

        android.util.Log.d("CustomAudioMixer", "Returning ${if (result != null) "${result.byteBuffer?.remaining()} bytes" else "null"}")
        return result
    }

    private fun handleAdditiveMix(
        originalBuffer: ByteBuffer,
        customAudioBuffer: ByteBuffer?,
        audioFormat: Int,
        captureTimeNs: Long
    ): BufferResponse? {
        if (customAudioBuffer == null) {
            // No custom audio, apply microphone gain if needed
            return if (microphoneGain != 1.0f) {
                val processedBuffer = applyGain(originalBuffer, microphoneGain, audioFormat)
                BufferResponse(processedBuffer, captureTimeNs)
            } else {
                null // Use original buffer as-is
            }
        }

        // Apply gains and mix
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

        // Actually mix the microphone and custom audio additively
        val mixedBuffer = mixBuffers(micBuffer, customBuffer, audioFormat)

        return BufferResponse(
            mixedBuffer,
            customAudioProvider?.getCaptureTimeNs() ?: captureTimeNs
        )
    }

    private fun handleReplaceMix(
        originalBuffer: ByteBuffer,
        customAudioBuffer: ByteBuffer?,
        captureTimeNs: Long
    ): BufferResponse? {
        return if (customAudioBuffer != null) {
            val processedBuffer = if (customAudioGain != 1.0f) {
                applyGain(customAudioBuffer, customAudioGain, AudioFormat.ENCODING_PCM_16BIT)
            } else {
                customAudioBuffer
            }
            BufferResponse(
                processedBuffer,
                customAudioProvider?.getCaptureTimeNs() ?: captureTimeNs
            )
        } else {
            // No custom audio, use microphone with gain
            if (microphoneGain != 1.0f) {
                val processedBuffer = applyGain(originalBuffer, microphoneGain, AudioFormat.ENCODING_PCM_16BIT)
                BufferResponse(processedBuffer, captureTimeNs)
            } else {
                null // Use original buffer as-is
            }
        }
    }

    private fun handleCustomOnlyMix(
        customAudioBuffer: ByteBuffer?,
        captureTimeNs: Long,
        requestedBufferSize: Int = 1024
    ): BufferResponse? {
        return if (customAudioBuffer != null) {
            val processedBuffer = if (customAudioGain != 1.0f) {
                applyGain(customAudioBuffer, customAudioGain, AudioFormat.ENCODING_PCM_16BIT)
            } else {
                customAudioBuffer
            }
            BufferResponse(
                processedBuffer,
                customAudioProvider?.getCaptureTimeNs() ?: captureTimeNs
            )
        } else {
            // No custom audio available, return silence with correct buffer size
            BufferResponse(
                createSilenceBuffer(requestedBufferSize),
                captureTimeNs
            )
        }
    }

    /**
     * Apply gain to an audio buffer.
     */
    private fun applyGain(buffer: ByteBuffer, gain: Float, audioFormat: Int): ByteBuffer {
        if (gain == 1.0f) {
            return buffer.duplicate()
        }

        val outputBuffer = ByteBuffer.allocate(buffer.remaining())
        outputBuffer.order(buffer.order())

        val inputCopy = buffer.duplicate()

        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                while (inputCopy.hasRemaining() && outputBuffer.remaining() >= 2) {
                    val sample = inputCopy.short
                    val amplified = (sample * gain).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outputBuffer.putShort(amplified.toShort())
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (inputCopy.hasRemaining()) {
                    val sample = inputCopy.get().toInt() and 0xFF
                    val amplified = ((sample - 128) * gain + 128).toInt()
                        .coerceIn(0, 255)
                    outputBuffer.put(amplified.toByte())
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                while (inputCopy.hasRemaining() && outputBuffer.remaining() >= 4) {
                    val sample = inputCopy.float
                    val amplified = (sample * gain).coerceIn(-1.0f, 1.0f)
                    outputBuffer.putFloat(amplified)
                }
            }
            else -> {
                // Unsupported format, copy as-is
                outputBuffer.put(inputCopy)
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    /**
     * Create a buffer filled with silence.
     */
    private fun createSilenceBuffer(size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(size)
        // Zero-filled buffer represents silence for PCM audio
        buffer.position(buffer.limit())
        buffer.flip()
        return buffer
    }

    /**
     * Check if custom audio is currently being mixed.
     */
    fun isCustomAudioActive(): Boolean {
        return isActive && customAudioProvider?.hasMoreData() == true
    }

    /**
     * Get the current custom audio provider.
     */
    fun getCustomAudioProvider(): CustomAudioBufferProvider? = customAudioProvider

    /**
     * Mix two audio buffers additively.
     */
    private fun mixBuffers(buffer1: ByteBuffer, buffer2: ByteBuffer, audioFormat: Int): ByteBuffer {
        // Use duplicate to avoid modifying original buffer positions
        val buf1 = buffer1.duplicate()
        val buf2 = buffer2.duplicate()

        val size = minOf(buf1.remaining(), buf2.remaining())
        val outputBuffer = ByteBuffer.allocate(size)
        outputBuffer.order(buf1.order())

        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                // Mix 16-bit PCM samples
                while (buf1.remaining() >= 2 && buf2.remaining() >= 2 && outputBuffer.remaining() >= 2) {
                    val sample1 = buf1.short.toInt()
                    val sample2 = buf2.short.toInt()
                    val mixed = (sample1 + sample2).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outputBuffer.putShort(mixed.toShort())
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                // Mix 8-bit PCM samples
                while (buf1.hasRemaining() && buf2.hasRemaining() && outputBuffer.hasRemaining()) {
                    val sample1 = buf1.get().toInt() and 0xFF
                    val sample2 = buf2.get().toInt() and 0xFF
                    val mixed = ((sample1 + sample2) / 2).coerceIn(0, 255)
                    outputBuffer.put(mixed.toByte())
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                // Mix float PCM samples
                while (buf1.remaining() >= 4 && buf2.remaining() >= 4 && outputBuffer.remaining() >= 4) {
                    val sample1 = buf1.float
                    val sample2 = buf2.float
                    val mixed = (sample1 + sample2).coerceIn(-1.0f, 1.0f)
                    outputBuffer.putFloat(mixed)
                }
            }
            else -> {
                // Fallback: just copy the first buffer
                val toCopy = minOf(buf1.remaining(), outputBuffer.remaining())
                val originalLimit = buf1.limit()
                buf1.limit(buf1.position() + toCopy)
                outputBuffer.put(buf1)
                buf1.limit(originalLimit)
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }
}
