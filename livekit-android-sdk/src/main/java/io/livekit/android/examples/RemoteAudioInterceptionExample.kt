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
import android.util.Log
import io.livekit.android.audio.AudioDataProcessor
import io.livekit.android.audio.AudioPlaybackController
import io.livekit.android.audio.PlaybackControlledInterceptor
import io.livekit.android.audio.RemoteAudioDataInterceptor
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.clearAudioDataInterceptor
import io.livekit.android.room.track.muteWithProcessor
import io.livekit.android.room.track.setAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Examples demonstrating how to intercept and process remote audio data.
 *
 * This shows various use cases:
 * 1. Recording remote audio to files
 * 2. Processing audio data without affecting playback
 * 3. Muting specific participants while still accessing their data
 * 4. Custom audio playback control
 */
class RemoteAudioInterceptionExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Example 1: Record remote audio to file while maintaining normal playback
     */
    fun startRecordingRemoteAudio(participant: Participant, outputFile: File) {
        val audioTrack = participant.audioTrackPublications.firstOrNull()?.first?.track as? RemoteAudioTrack
        if (audioTrack == null) {
            Log.w("AudioExample", "No audio track found for participant ${participant.identity}")
            return
        }

        val fileOutputStream = FileOutputStream(outputFile)

        val audioRecorder = object : AudioDataProcessor() {
            override fun processAudioData(
                participant: Participant,
                trackSid: String,
                audioData: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                timestamp: Long
            ) {
                // Save audio data to file
                scope.launch {
                    try {
                        val audioBytes = ByteArray(audioData.remaining())
                        audioData.duplicate().get(audioBytes)
                        fileOutputStream.write(audioBytes)

                        Log.d("AudioExample", "Recording audio: ${audioBytes.size} bytes from ${participant.identity}")
                    } catch (e: Exception) {
                        Log.e("AudioExample", "Error writing audio to file", e)
                    }
                }
            }
        }

        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            processor = audioRecorder::processAudioData
        )

        Log.d("AudioExample", "Started recording audio from ${participant.identity} to ${outputFile.absolutePath}")
    }

    /**
     * Example 2: Mute a specific participant while still processing their audio data
     */
    fun muteParticipantWithDataAccess(participant: Participant) {
        val audioTrack = participant.audioTrackPublications.firstOrNull()?.first?.track as? RemoteAudioTrack
        if (audioTrack == null) {
            Log.w("AudioExample", "No audio track found for participant ${participant.identity}")
            return
        }

        audioTrack.muteWithProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { _, _, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            // Process the audio data even though it's muted
            Log.d("AudioExample", "Processing muted audio from ${participant.identity}: " +
                    "sampleRate=$sampleRate, channels=$numberOfChannels, frames=$numberOfFrames")

            // You could save to file, analyze for speech, etc.
            analyzeAudioData(audioData, sampleRate, numberOfChannels)
        }

        Log.d("AudioExample", "Muted participant ${participant.identity} with data access")
    }

    /**
     * Example 3: Custom audio processing with playback control
     */
    fun setupCustomAudioProcessing(participant: Participant) {
        val audioTrack = participant.audioTrackPublications.firstOrNull()?.first?.track as? RemoteAudioTrack
        if (audioTrack == null) {
            Log.w("AudioExample", "No audio track found for participant ${participant.identity}")
            return
        }

        val customProcessor = object : PlaybackControlledInterceptor(context, enablePlayback = true) {
            override fun processAndControlPlayback(
                participant: Participant,
                trackSid: String,
                audioData: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                timestamp: Long,
                playbackController: AudioPlaybackController?
            ): Boolean {
                // Process the audio data
                val processedAudio = applyAudioFilter(audioData, sampleRate, numberOfChannels)

                // Play the processed audio through our custom controller
                playbackController?.let { controller ->
                    if (processedAudio != null) {
                        controller.playAudioData(processedAudio)
                    }
                }

                // Return false since we're handling playback ourselves
                return false
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            interceptor = customProcessor
        )

        // Start the custom playback
        customProcessor.startPlayback()

        Log.d("AudioExample", "Set up custom audio processing for ${participant.identity}")
    }

    /**
     * Example 4: Complete audio interception without playback
     */
    fun interceptAudioWithoutPlayback(participant: Participant) {
        val audioTrack = participant.audioTrackPublications.firstOrNull()?.first?.track as? RemoteAudioTrack
        if (audioTrack == null) {
            Log.w("AudioExample", "No audio track found for participant ${participant.identity}")
            return
        }

        val audioInterceptor = object : RemoteAudioDataInterceptor {
            override fun onRemoteAudioData(
                participant: Participant,
                trackSid: String,
                audioData: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                timestamp: Long
            ): RemoteAudioDataInterceptor.InterceptorResult {

                // Process the audio data as needed
                Log.d("AudioExample", "Intercepted audio from ${participant.identity}: " +
                        "${audioData.remaining()} bytes, $sampleRate Hz, $numberOfChannels channels")

                // You can:
                // - Save to file
                // - Send to server for processing
                // - Analyze for speech recognition
                // - Apply custom filters
                // - etc.

                processAudioForAnalysis(audioData, participant)

                // Don't allow playback - audio is completely intercepted
                return RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = false)
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            interceptor = audioInterceptor
        )

        Log.d("AudioExample", "Set up complete audio interception for ${participant.identity}")
    }

    /**
     * Example 5: Monitor all participants' audio automatically
     */
    fun setupAutomaticAudioMonitoring() {
        scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed -> {
                        if (event.track is RemoteAudioTrack && event.track.kind == Track.Kind.AUDIO) {
                            val participant = event.participant
                            val audioTrack = event.track

                            Log.d("AudioExample", "New audio track from ${participant.identity}, setting up monitoring")

                            // Set up automatic monitoring for this participant
                            setupAudioMonitoring(participant, audioTrack)
                        }
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        if (event.track is RemoteAudioTrack) {
                            Log.d("AudioExample", "Audio track unsubscribed from ${event.participant.identity}")
                            // Clean up if needed
                        }
                    }
                    else -> { /* Handle other events */ }
                }
            }
        }
    }

    private fun setupAudioMonitoring(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { _, _, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            // Monitor audio levels, detect speech, etc.
            val audioLevel = calculateAudioLevel(audioData, bitsPerSample)
            Log.d("AudioExample", "Audio level from ${participant.identity}: $audioLevel")

            // You could trigger events based on audio activity
            if (audioLevel > 0.1) {
                onParticipantSpeaking(participant, audioLevel)
            }
        }
    }

    // Helper methods for audio processing

    private fun analyzeAudioData(audioData: ByteBuffer, sampleRate: Int, numberOfChannels: Int) {
        // Implement your audio analysis logic here
        // E.g., speech detection, volume analysis, etc.
    }

    private fun applyAudioFilter(audioData: ByteBuffer, sampleRate: Int, numberOfChannels: Int): ByteBuffer? {
        // Implement your audio filtering logic here
        // E.g., noise reduction, echo cancellation, etc.
        return audioData.duplicate() // Return processed audio
    }

    private fun processAudioForAnalysis(audioData: ByteBuffer, participant: Participant) {
        // Implement your audio analysis logic
        // E.g., send to speech recognition service, save for later processing, etc.
    }

    private fun calculateAudioLevel(audioData: ByteBuffer, bitsPerSample: Int): Double {
        // Simple RMS calculation for audio level
        var sum = 0.0
        val data = audioData.duplicate()

        when (bitsPerSample) {
            16 -> {
                while (data.hasRemaining()) {
                    val sample = data.short.toDouble()
                    sum += sample * sample
                }
                return Math.sqrt(sum / (data.capacity() / 2)) / Short.MAX_VALUE
            }
            8 -> {
                while (data.hasRemaining()) {
                    val sample = (data.get().toInt() and 0xFF) - 128.0
                    sum += sample * sample
                }
                return Math.sqrt(sum / data.capacity()) / 127.0
            }
            else -> return 0.0
        }
    }

    private fun onParticipantSpeaking(participant: Participant, audioLevel: Double) {
        // Handle participant speaking event
        Log.d("AudioExample", "${participant.identity} is speaking (level: $audioLevel)")
    }

    /**
     * Clean up resources when done
     */
    fun cleanup() {
        // Remove all audio interceptors and clean up resources
        room.remoteParticipants.values.forEach { participant ->
            participant.audioTrackPublications.forEach { publication ->
                (publication.first.track as? RemoteAudioTrack)?.clearAudioDataInterceptor()
            }
        }
    }
}
