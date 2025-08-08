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

package io.livekit.android.room.track

import io.livekit.android.audio.InterceptingAudioTrackSink
import io.livekit.android.audio.MutingInterceptor
import io.livekit.android.audio.RemoteAudioDataInterceptor
import io.livekit.android.room.participant.Participant
import livekit.org.webrtc.AudioTrackSink

/**
 * Extension functions for RemoteAudioTrack to support audio data interception.
 */

/**
 * Sets an interceptor to process remote audio data.
 *
 * This allows you to:
 * - Record or analyze incoming audio
 * - Process audio data for custom purposes
 * - Control whether audio should be muted
 *
 * Note: When an interceptor is set, the normal audio playback to speakers will be disabled.
 * If you want to maintain playback, return InterceptorResult(allowPlayback = true) from your interceptor.
 *
 * @param participant The participant who owns this track
 * @param trackSid The track SID for identification
 * @param interceptor The interceptor to handle audio data
 */
fun RemoteAudioTrack.setAudioDataInterceptor(
    participant: Participant,
    trackSid: String,
    interceptor: RemoteAudioDataInterceptor
) {
    // Remove any existing intercepting sinks first
    clearAudioDataInterceptor()

    // Get all current sinks and store them for potential restoration
    val currentSinks = mutableListOf<livekit.org.webrtc.AudioTrackSink>()

    // We need to use reflection or find another way to get current sinks
    // For now, let's try a different approach - disable the track when muting

    // Create the intercepting sink
    val interceptingSink = InterceptingAudioTrackSink(
        participant = participant,
        trackSid = trackSid,
        interceptor = interceptor,
        originalSink = null // We'll handle muting differently
    )

    // Store reference for later removal
    setTag("intercepting_sink", interceptingSink)
    setTag("was_enabled", this.enabled) // Store original enabled state

    // For muting interceptors, disable the track entirely
    if (interceptor is MutingInterceptor) {
        this.enabled = false
    }

    // Add the intercepting sink
    addSink(interceptingSink)
}

/**
 * Removes the audio data interceptor and restores normal audio playback.
 */
fun RemoteAudioTrack.clearAudioDataInterceptor() {
    // Remove the intercepting sink if it exists
    val interceptingSink = getTag("intercepting_sink") as? AudioTrackSink
    if (interceptingSink != null) {
        removeSink(interceptingSink)
        setTag("intercepting_sink", null)
    }

    // Restore original enabled state if it was stored
    val wasEnabled = getTag("was_enabled") as? Boolean
    if (wasEnabled != null) {
        this.enabled = wasEnabled
        setTag("was_enabled", null)
    }
}

/**
 * Sets a simple audio processor that only processes data without affecting playback.
 *
 * @param participant The participant who owns this track
 * @param trackSid The track SID for identification
 * @param processor Function to process the audio data
 */
fun RemoteAudioTrack.setAudioDataProcessor(
    participant: Participant,
    trackSid: String,
    processor: (
        participant: Participant,
        trackSid: String,
        audioData: java.nio.ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ) -> Unit
) {
    val processorInterceptor = object : io.livekit.android.audio.AudioDataProcessor() {
        override fun processAudioData(
            participant: Participant,
            trackSid: String,
            audioData: java.nio.ByteBuffer,
            bitsPerSample: Int,
            sampleRate: Int,
            numberOfChannels: Int,
            numberOfFrames: Int,
            timestamp: Long
        ) {
            processor(participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp)
        }
    }

    setAudioDataInterceptor(participant, trackSid, processorInterceptor)
}

/**
 * Mutes this remote audio track by setting an interceptor that blocks playback.
 * The audio data is still processed and can be accessed through the optional processor.
 *
 * @param participant The participant who owns this track
 * @param trackSid The track SID for identification
 * @param processor Optional processor to still receive the audio data for processing
 */
fun RemoteAudioTrack.muteWithProcessor(
    participant: Participant,
    trackSid: String,
    processor: ((
        participant: Participant,
        trackSid: String,
        audioData: java.nio.ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ) -> Unit)? = null
) {
    val mutingInterceptor = object : RemoteAudioDataInterceptor, io.livekit.android.audio.MutingInterceptor {
        override fun onRemoteAudioData(
            participant: Participant,
            trackSid: String,
            audioData: java.nio.ByteBuffer,
            bitsPerSample: Int,
            sampleRate: Int,
            numberOfChannels: Int,
            numberOfFrames: Int,
            timestamp: Long
        ): RemoteAudioDataInterceptor.InterceptorResult {
            // Process the data if a processor is provided
            processor?.invoke(participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp)

            // But don't allow playback (mute)
            return RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = false)
        }
    }

    setAudioDataInterceptor(participant, trackSid, mutingInterceptor)
}



/**
 * Helper extension to store and retrieve tags on tracks.
 * This is used internally to track intercepting sinks.
 */
private val trackTags = mutableMapOf<String, MutableMap<String, Any?>>()

private fun Track.setTag(key: String, value: Any?) {
    val trackId = this.rtcTrack.id()
    trackTags.getOrPut(trackId) { mutableMapOf() }[key] = value
}

private fun Track.getTag(key: String): Any? {
    val trackId = this.rtcTrack.id()
    return trackTags[trackId]?.get(key)
}
