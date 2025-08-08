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
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Provides audio data from a pre-loaded buffer or stream of buffers.
 *
 * This provider allows you to feed raw PCM audio data directly.
 * Useful for streaming audio from external sources or pre-processed audio data.
 */
class BufferAudioBufferProvider(
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val channelCount: Int = 2,
    private val sampleRate: Int = 44100,
    private val loop: Boolean = false
) : CustomAudioBufferProvider {

    private val audioDataQueue = ConcurrentLinkedQueue<ByteBuffer>()
    private var isRunning = false
    private var currentBuffer: ByteBuffer? = null
    private var originalBuffers = mutableListOf<ByteBuffer>() // For looping

    override fun start() {
        isRunning = true
        LKLog.d { "BufferAudioBufferProvider started" }
    }

    override fun stop() {
        isRunning = false
        audioDataQueue.clear()
        currentBuffer = null
        LKLog.d { "BufferAudioBufferProvider stopped" }
    }

    override fun hasMoreData(): Boolean {
        return isRunning && (currentBuffer?.hasRemaining() == true ||
                           audioDataQueue.isNotEmpty() ||
                           (loop && originalBuffers.isNotEmpty()))
    }

    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        Log.d("BufferAudioBufferProvider", "Audio requested: $requestedBytes bytes, Format: $audioFormat, Channels: $channelCount, Rate: $sampleRate, Running: $isRunning, HasData: ${hasMoreData()}, Queue: ${audioDataQueue.size}")

        if (!isRunning) {
            Log.d("BufferAudioBufferProvider", "Provider not running, returning null")
            return null
        }

        val outputBuffer = ByteBuffer.allocate(requestedBytes)
        outputBuffer.order(ByteOrder.nativeOrder())

        var totalBytesRead = 0

        while (totalBytesRead < requestedBytes && hasMoreData()) {
            // Get current buffer if we don't have one
            if (currentBuffer?.hasRemaining() != true) {
                currentBuffer = getNextBuffer()
            }

            currentBuffer?.let { buffer ->
                val bytesToCopy = minOf(buffer.remaining(), requestedBytes - totalBytesRead)
                if (bytesToCopy > 0) {
                    // Create a slice to avoid modifying the original buffer position
                    val slice = buffer.duplicate()
                    slice.limit(slice.position() + bytesToCopy)

                    // Convert format if necessary
                    val convertedData = convertAudioFormat(
                        slice,
                        this.audioFormat,
                        audioFormat,
                        this.channelCount,
                        channelCount,
                        this.sampleRate,
                        sampleRate
                    )

                    // Check if there's enough space in output buffer
                    if (outputBuffer.remaining() >= convertedData.remaining()) {
                        outputBuffer.put(convertedData)
                        buffer.position(buffer.position() + bytesToCopy)
                        totalBytesRead += convertedData.remaining()
                    } else {
                        // Not enough space, break to avoid overflow
//                        android.util.Log.w("BufferAudioBufferProvider", "Output buffer overflow prevented: available=${outputBuffer.remaining()}, needed=${convertedData.remaining()}")
//                        break
                    }
                }

                if (!buffer.hasRemaining()) {
                    currentBuffer = null
                }
            } ?: break
        }

        return if (totalBytesRead > 0) {
            outputBuffer.flip()
            outputBuffer
        } else {
            null
        }
    }

    /**
     * Add audio data to the provider.
     * The buffer should contain PCM audio data in the format specified in the constructor.
     */
    fun addAudioData(audioData: ByteBuffer) {
        // 保存原始数据大小和位置
        val originalPosition = audioData.position()
        val originalLimit = audioData.limit()
        val dataSize = audioData.remaining()
        
        // 创建队列缓冲区副本
        val bufferCopy = ByteBuffer.allocate(dataSize)
        bufferCopy.put(audioData)
        bufferCopy.flip()

        audioDataQueue.offer(bufferCopy)
        android.util.Log.d("BufferAudioBufferProvider", "Added audio data: ${bufferCopy.remaining()} bytes, Queue size: ${audioDataQueue.size}")

        // Store for looping if enabled
        if (loop) {
            // 重置audioData到原始状态以便复制到循环缓冲区
            audioData.position(originalPosition)
            audioData.limit(originalLimit)
            
            val originalCopy = ByteBuffer.allocate(dataSize)
            originalCopy.put(audioData)
            originalCopy.flip()
            originalBuffers.add(originalCopy)
            android.util.Log.d("BufferAudioBufferProvider", "Stored for looping: ${originalCopy.remaining()} bytes, Original buffers: ${originalBuffers.size}")
        }
    }

    /**
     * Add audio data from a byte array.
     */
    fun addAudioData(audioData: ByteArray) {
        val buffer = ByteBuffer.wrap(audioData.copyOf())
        addAudioData(buffer)
    }

    /**
     * Clear all queued audio data.
     */
    fun clearAudioData() {
        audioDataQueue.clear()
        currentBuffer = null
        originalBuffers.clear()
    }

    /**
     * Get the number of buffers currently queued.
     */
    fun getQueuedBufferCount(): Int = audioDataQueue.size

    private fun getNextBuffer(): ByteBuffer? {
        var buffer = audioDataQueue.poll()

        // If we're looping and no more buffers, reload from original buffers
        if (buffer == null && loop && originalBuffers.isNotEmpty()) {
            for (originalBuffer in originalBuffers) {
                val copy = ByteBuffer.allocate(originalBuffer.capacity())
                originalBuffer.rewind()
                copy.put(originalBuffer)
                copy.flip()
                audioDataQueue.offer(copy)
            }
            buffer = audioDataQueue.poll()
        }

        return buffer
    }

    /**
     * Convert audio format between different sample rates, channel counts, and bit depths.
     * This is a simplified conversion - for production use, consider using more sophisticated
     * audio processing libraries for better quality resampling.
     */
    private fun convertAudioFormat(
        inputBuffer: ByteBuffer,
        inputFormat: Int,
        outputFormat: Int,
        inputChannels: Int,
        outputChannels: Int,
        inputSampleRate: Int,
        outputSampleRate: Int
    ): ByteBuffer {
        // For now, do basic format conversion without resampling
        // In production, you'd want proper resampling for sample rate differences

        val outputBuffer = ByteBuffer.allocate(inputBuffer.remaining())
        outputBuffer.order(ByteOrder.nativeOrder())

        if (inputFormat == outputFormat && inputChannels == outputChannels && inputSampleRate == outputSampleRate) {
            // No conversion needed
            outputBuffer.put(inputBuffer)
        } else {
            // Basic format conversion
            when (outputFormat) {
                AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.ENCODING_DEFAULT -> {
                    convertTo16BitPCM(inputBuffer, outputBuffer, inputFormat)
                }
                AudioFormat.ENCODING_PCM_8BIT -> {
                    convertTo8BitPCM(inputBuffer, outputBuffer, inputFormat)
                }
                AudioFormat.ENCODING_PCM_FLOAT -> {
                    convertToFloatPCM(inputBuffer, outputBuffer, inputFormat)
                }
                else -> {
                    // Default: copy as-is
                    outputBuffer.put(inputBuffer)
                }
            }
        }

        outputBuffer.flip()
        return outputBuffer
    }

    private fun convertTo16BitPCM(input: ByteBuffer, output: ByteBuffer, inputFormat: Int) {
        when (inputFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                // Direct copy
                output.put(input)
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                // Convert 8-bit to 16-bit
                while (input.hasRemaining() && output.remaining() >= 2) {
                    val sample8 = input.get().toInt() and 0xFF
                    val sample16 = ((sample8 - 128) * 256).toShort()
                    output.putShort(sample16)
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                // Convert float to 16-bit
                while (input.hasRemaining() && output.remaining() >= 2) {
                    val sampleFloat = input.float
                    val sample16 = (sampleFloat * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    output.putShort(sample16)
                }
            }
            else -> {
                // Default: copy as-is
                output.put(input)
            }
        }
    }

    private fun convertTo8BitPCM(input: ByteBuffer, output: ByteBuffer, inputFormat: Int) {
        when (inputFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                // Convert 16-bit to 8-bit
                while (input.hasRemaining() && output.hasRemaining()) {
                    val sample16 = input.short
                    val sample8 = ((sample16 / 256) + 128).toByte()
                    output.put(sample8)
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                // Direct copy
                output.put(input)
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                // Convert float to 8-bit
                while (input.hasRemaining() && output.hasRemaining()) {
                    val sampleFloat = input.float
                    val sample8 = ((sampleFloat * 127) + 128).toInt().coerceIn(0, 255).toByte()
                    output.put(sample8)
                }
            }
            else -> {
                // Default: copy as-is
                output.put(input)
            }
        }
    }

    private fun convertToFloatPCM(input: ByteBuffer, output: ByteBuffer, inputFormat: Int) {
        when (inputFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                // Convert 16-bit to float
                while (input.hasRemaining() && output.remaining() >= 4) {
                    val sample16 = input.short
                    val sampleFloat = sample16.toFloat() / Short.MAX_VALUE
                    output.putFloat(sampleFloat)
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                // Convert 8-bit to float
                while (input.hasRemaining() && output.remaining() >= 4) {
                    val sample8 = input.get().toInt() and 0xFF
                    val sampleFloat = ((sample8 - 128).toFloat()) / 127f
                    output.putFloat(sampleFloat)
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                // Direct copy
                output.put(input)
            }
            else -> {
                // Default: try to copy as-is
                output.put(input)
            }
        }
    }
}
