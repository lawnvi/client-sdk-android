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

import io.livekit.android.room.participant.Participant
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer

/**
 * Interface for intercepting and processing remote audio data before it reaches the speaker.
 * 
 * This allows you to:
 * - Process remote audio data for recording, analysis, or custom playback
 * - Control whether the audio should continue to the speaker or be muted
 * - Modify the audio data before it's played
 */
interface RemoteAudioDataInterceptor {
    
    /**
     * Called when remote audio data is received.
     * 
     * @param participant The participant who sent the audio
     * @param trackSid The track SID of the audio source
     * @param audioData The raw audio data buffer
     * @param bitsPerSample Number of bits per audio sample (typically 16)
     * @param sampleRate Sample rate in Hz (typically 16000, 44100, or 48000)
     * @param numberOfChannels Number of audio channels (1 for mono, 2 for stereo)
     * @param numberOfFrames Number of audio frames in this buffer
     * @param timestamp Timestamp when the audio was captured (in milliseconds)
     * @return InterceptorResult indicating how to handle the audio
     */
    fun onRemoteAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ): InterceptorResult
    
    /**
     * Result of audio data interception.
     */
    data class InterceptorResult(
        /**
         * Whether the audio should continue to the speaker.
         * If false, the audio will be muted for this participant.
         */
        val allowPlayback: Boolean = true,
        
        /**
         * Modified audio data to play instead of the original.
         * If null, the original audio data will be used (if allowPlayback is true).
         * The buffer should have the same format as the input.
         */
        val modifiedAudioData: ByteBuffer? = null
    )
}

/**
 * Marker interface for interceptors that should mute audio playback.
 */
interface MutingInterceptor

/**
 * A convenience interceptor that only processes audio data without affecting playback.
 */
abstract class AudioDataProcessor : RemoteAudioDataInterceptor {
    
    /**
     * Process the audio data without affecting playback.
     * This method is called for every remote audio frame.
     */
    abstract fun processAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    )
    
    final override fun onRemoteAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ): RemoteAudioDataInterceptor.InterceptorResult {
        processAudioData(participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp)
        return RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = true)
    }
}

/**
 * Implementation of AudioTrackSink that intercepts audio data and allows custom processing.
 */
internal class InterceptingAudioTrackSink(
    private val participant: Participant,
    private val trackSid: String,
    private val interceptor: RemoteAudioDataInterceptor,
    private val originalSink: AudioTrackSink? = null
) : AudioTrackSink {
    
    override fun onData(
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ) {
        // Make a copy of the audio data for the interceptor
        // since the original buffer might be reused
        val audioDataCopy = ByteBuffer.allocate(audioData.remaining())
        audioDataCopy.put(audioData.duplicate())
        audioDataCopy.flip()
        
        // Call the interceptor
        val result = interceptor.onRemoteAudioData(
            participant = participant,
            trackSid = trackSid,
            audioData = audioDataCopy,
            bitsPerSample = bitsPerSample,
            sampleRate = sampleRate,
            numberOfChannels = numberOfChannels,
            numberOfFrames = numberOfFrames,
            timestamp = timestamp
        )
        
        // Handle the result
        if (result.allowPlayback && originalSink != null) {
            val dataToPlay = result.modifiedAudioData ?: audioData
            originalSink.onData(
                dataToPlay,
                bitsPerSample,
                sampleRate,
                numberOfChannels,
                numberOfFrames,
                timestamp
            )
        }
        // If allowPlayback is false or originalSink is null, the audio is effectively muted
    }
}