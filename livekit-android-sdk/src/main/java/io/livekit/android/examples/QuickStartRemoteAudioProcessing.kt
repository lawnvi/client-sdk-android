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
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.livekit.android.audio.RemoteAudioDataInterceptor
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.setAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import io.livekit.android.room.track.muteWithProcessor
import java.nio.ByteBuffer

/**
 * Quick start examples for remote audio data processing.
 */
class QuickStartRemoteAudioProcessing {

    /**
     * Example 1: Simple audio data processing (no effect on playback)
     */
    fun processAudioData(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            // Process the audio data here
            Log.d("AudioProcessor", "Received audio from ${participant.identity}: " +
                    "${audioData.remaining()} bytes, $sampleRate Hz")

            // Your processing logic here:
            // - Save to file
            // - Analyze audio levels
            // - Send to speech recognition
            // - etc.
        }
    }

    /**
     * Example 2: Mute participant but still process their audio
     */
    fun muteButStillProcess(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.muteWithProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            // Audio is muted (not played to speakers) but you can still process it
            Log.d("AudioProcessor", "Processing muted audio from ${participant.identity}")

            // Your processing logic for muted audio
        }
    }

    /**
     * Example 3: Complete control over audio (intercept and decide what to do)
     */
    fun fullAudioControl(
        context: Context,
        participant: Participant,
        audioTrack: RemoteAudioTrack
    ) {
        val interceptor = object : RemoteAudioDataInterceptor {
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

                // Process the audio data
                Log.d("AudioInterceptor", "Intercepted audio from ${participant.identity}")

                // Your custom logic here...
                val shouldPlayAudio = processAndDecidePlayback(audioData, participant)

                // Return whether audio should be played
                return RemoteAudioDataInterceptor.InterceptorResult(
                    allowPlayback = shouldPlayAudio
                )
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid,
            interceptor = interceptor
        )
    }

    /**
     * Example 4: Audio recording to buffer
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun recordAudioToBuffer(participant: Participant, audioTrack: RemoteAudioTrack) {
        val audioBuffer = mutableListOf<ByteArray>()

        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { _, _, audioData, _, sampleRate, numberOfChannels, _, timestamp ->
            // Copy audio data to our buffer
            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            audioBuffer.add(audioBytes)

            Log.d("AudioRecorder", "Recorded ${audioBytes.size} bytes. Total buffers: ${audioBuffer.size}")

            // Optional: limit buffer size
            if (audioBuffer.size > 1000) {
                audioBuffer.removeFirst()
            }
        }
    }

    private fun processAndDecidePlayback(audioData: ByteBuffer, participant: Participant): Boolean {
        // Your logic to decide whether to play the audio
        // For example, you might:
        // - Check participant permissions
        // - Analyze audio content
        // - Apply volume thresholds
        // - etc.

        return true // Allow playback by default
    }
}
