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

package io.livekit.android.examples

import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.audio.CustomAudioMixer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.participant.createAudioTrackWithCustomSource
import io.livekit.android.room.participant.createAudioTrackWithFile
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.Track
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import kotlin.math.sin

/**
 * Example demonstrating how to use custom audio sources with LiveKit Android SDK.
 * 
 * This class shows three different approaches:
 * 1. Playing audio from a local file
 * 2. Streaming audio from buffers (for real-time audio streaming)
 * 3. Generating synthetic audio (for testing or special effects)
 */
class CustomAudioExample {

    private lateinit var room: Room
    private lateinit var context: Context

    /**
     * Initialize the example with a LiveKit room.
     */
    fun initialize(context: Context) {
        this.context = context
        this.room = LiveKit.create(context)
    }

    /**
     * Example 1: Play audio from a local file
     * 
     * This example shows how to mix audio from a local audio file
     * (MP3, WAV, OPUS, etc.) with microphone input.
     */
    suspend fun playAudioFromFile(audioFileUri: Uri) {
        val localParticipant = room.localParticipant

        // Create audio track with file input
        val audioTrack = localParticipant.createAudioTrackWithFile(
            context = context,
            name = "mixed_audio_with_file",
            audioFileUri = audioFileUri,
            loop = true, // Loop the audio file
            microphoneGain = 0.7f, // Reduce microphone volume slightly
            customAudioGain = 0.8f, // Custom audio at 80% volume
            mixMode = CustomAudioMixer.MixMode.ADDITIVE // Mix both sources
        )

        // Publish the audio track
        localParticipant.publishAudioTrack(audioTrack)
    }

    /**
     * Example 2: Stream audio from buffers
     * 
     * This example shows how to stream audio data from buffers,
     * useful for real-time audio processing or external audio sources.
     */
    suspend fun streamAudioFromBuffers() {
        val localParticipant = room.localParticipant

        // Create audio track with buffer input
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
            name = "streamed_audio",
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,
            sampleRate = 44100,
            microphoneGain = 0.5f, // Reduce microphone to 50%
            customAudioGain = 1.0f, // Custom audio at full volume
            mixMode = CustomAudioMixer.MixMode.ADDITIVE
        )

        // Publish the audio track
        localParticipant.publishAudioTrack(audioTrack)

        // Example: Generate and stream some audio data
        // In a real application, you would get this data from your audio source
        generateAndStreamAudioData(bufferProvider)
    }

    /**
     * Example 3: Generate synthetic audio
     * 
     * This example shows how to generate audio programmatically,
     * useful for testing, tone generation, or special effects.
     */
    suspend fun generateSyntheticAudio() {
        val localParticipant = room.localParticipant

        // Create a custom audio provider that generates a sine wave
        val toneGenerator = SineToneAudioProvider(
            frequency = 440.0, // A4 note
            sampleRate = 44100,
            channelCount = 2
        )

        // Create audio track with the custom provider
        val audioTrack = localParticipant.createAudioTrackWithCustomSource(
            name = "synthetic_audio",
            customAudioProvider = toneGenerator,
            microphoneGain = 0.3f, // Very low microphone volume
            customAudioGain = 0.5f, // Moderate tone volume
            mixMode = CustomAudioMixer.MixMode.ADDITIVE
        )

        // Publish the audio track
        localParticipant.publishAudioTrack(audioTrack)
    }

    /**
     * Example 4: Replace microphone with custom audio
     * 
     * This example shows how to completely replace microphone input
     * with custom audio, falling back to microphone when no custom audio is available.
     */
    suspend fun replaceAudioSource(audioFileUri: Uri) {
        val localParticipant = room.localParticipant

        val audioTrack = localParticipant.createAudioTrackWithFile(
            context = context,
            name = "replacement_audio",
            audioFileUri = audioFileUri,
            loop = false, // Don't loop, fall back to microphone when done
            microphoneGain = 1.0f, // Normal microphone volume for fallback
            customAudioGain = 1.0f, // Normal custom audio volume
            mixMode = CustomAudioMixer.MixMode.REPLACE // Replace microphone with custom audio
        )

        localParticipant.publishAudioTrack(audioTrack)
    }

    /**
     * Helper function to generate and stream audio data.
     * In a real application, you would replace this with your actual audio source.
     */
    private fun generateAndStreamAudioData(bufferProvider: io.livekit.android.audio.BufferAudioBufferProvider) {
        // This would typically run in a background thread
        // For demonstration, we'll generate some simple audio data
        
        val sampleRate = 44100
        val channels = 2
        val frequency = 220.0 // A3 note
        val duration = 5.0 // 5 seconds
        val samples = (sampleRate * duration).toInt()
        
        val audioData = ByteArray(samples * channels * 2) // 16-bit samples
        val buffer = ByteBuffer.wrap(audioData)
        
        // Generate sine wave
        for (i in 0 until samples) {
            val sample = (sin(2.0 * Math.PI * frequency * i / sampleRate) * Short.MAX_VALUE * 0.5).toInt().toShort()
            
            // Stereo: same sample for both channels
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        
        // Add the generated audio data to the buffer provider
        buffer.rewind()
        bufferProvider.addAudioData(buffer)
    }

    /**
     * Clean up resources when done.
     */
    fun cleanup() {
        room.disconnect()
    }
}

/**
 * Example custom audio provider that generates a sine tone.
 * This demonstrates how to implement CustomAudioBufferProvider for procedural audio generation.
 */
class SineToneAudioProvider(
    private val frequency: Double,
    private val sampleRate: Int = 44100,
    private val channelCount: Int = 2
) : io.livekit.android.audio.CustomAudioBufferProvider {

    private var isRunning = false
    private var sampleIndex = 0L

    override fun start() {
        isRunning = true
        sampleIndex = 0L
    }

    override fun stop() {
        isRunning = false
    }

    override fun hasMoreData(): Boolean {
        return isRunning
    }

    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        if (!isRunning) return null

        val bytesPerSample = when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_DEFAULT -> 2
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }

        val samplesNeeded = requestedBytes / (bytesPerSample * channelCount)
        val buffer = ByteBuffer.allocate(requestedBytes)

        when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_DEFAULT -> {
                for (i in 0 until samplesNeeded) {
                    val sample = (sin(2.0 * Math.PI * frequency * sampleIndex / sampleRate) * Short.MAX_VALUE * 0.3).toInt().toShort()
                    sampleIndex++

                    // Write to all channels
                    for (ch in 0 until channelCount) {
                        buffer.putShort(sample)
                    }
                }
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                for (i in 0 until samplesNeeded) {
                    val sample = (sin(2.0 * Math.PI * frequency * sampleIndex / sampleRate) * 0.3).toFloat()
                    sampleIndex++

                    // Write to all channels
                    for (ch in 0 until channelCount) {
                        buffer.putFloat(sample)
                    }
                }
            }
            // Add other formats as needed
        }

        buffer.flip()
        return buffer
    }

    override fun getCaptureTimeNs(): Long {
        return System.nanoTime()
    }
}