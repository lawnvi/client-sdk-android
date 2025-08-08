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

import android.content.Context
import android.net.Uri
import io.livekit.android.audio.BufferAudioBufferProvider
import io.livekit.android.audio.CustomAudioBufferProvider
import io.livekit.android.audio.CustomAudioMixer
import io.livekit.android.audio.FileAudioBufferProvider
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions
import java.nio.ByteBuffer

/**
 * Extension functions for LocalParticipant to support custom audio sources.
 */

/**
 * Creates an audio track that mixes custom audio from a file with microphone input.
 *
 * @param context Android context for accessing the audio file
 * @param name The name of the track
 * @param audioFileUri URI of the audio file to play
 * @param options Audio track options
 * @param loop Whether to loop the audio file
 * @param microphoneGain Gain applied to microphone audio (1.0 = normal volume)
 * @param customAudioGain Gain applied to custom audio (1.0 = normal volume)
 * @param mixMode How to mix the custom audio with microphone
 * @return LocalAudioTrack with custom audio mixing capability
 */
fun LocalParticipant.createAudioTrackWithFile(
    context: Context,
    name: String = "",
    audioFileUri: Uri,
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    loop: Boolean = false,
    microphoneGain: Float = 1.0f,
    customAudioGain: Float = 1.0f,
    mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.ADDITIVE
): LocalAudioTrack {
    val fileProvider = FileAudioBufferProvider(context, audioFileUri, loop)

    return createAudioTrackWithCustomSource(
        name = name,
        customAudioProvider = fileProvider,
        options = options,
        microphoneGain = microphoneGain,
        customAudioGain = customAudioGain,
        mixMode = mixMode
    )
}

/**
 * Creates an audio track that mixes custom audio from buffers with microphone input.
 *
 * @param name The name of the track
 * @param options Audio track options
 * @param audioFormat Audio format of the buffer data (see AudioFormat constants)
 * @param channelCount Number of audio channels in the buffer data
 * @param sampleRate Sample rate of the buffer data
 * @param loop Whether to loop the audio buffers
 * @param microphoneGain Gain applied to microphone audio (1.0 = normal volume)
 * @param customAudioGain Gain applied to custom audio (1.0 = normal volume)
 * @param mixMode How to mix the custom audio with microphone
 * @return LocalAudioTrack with custom audio mixing capability
 */
fun LocalParticipant.createAudioTrackWithBuffer(
    name: String = "",
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    audioFormat: Int = android.media.AudioFormat.ENCODING_PCM_16BIT,
    channelCount: Int = 2,
    sampleRate: Int = 44100,
    loop: Boolean = false,
    microphoneGain: Float = 1.0f,
    customAudioGain: Float = 1.0f,
    mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.ADDITIVE
): Pair<LocalAudioTrack, BufferAudioBufferProvider> {
    val bufferProvider = BufferAudioBufferProvider(
        audioFormat = audioFormat,
        channelCount = channelCount,
        sampleRate = sampleRate,
        loop = loop
    )

    val (track, mixer) = createAudioTrackWithCustomSourceAndMixer(
        name = name,
        customAudioProvider = bufferProvider,
        options = options,
        microphoneGain = microphoneGain,
        customAudioGain = customAudioGain,
        mixMode = mixMode
    )

    // Store mixer reference in track tag for debugging
//    track.setTag("custom_mixer", mixer)

    return Pair(track, bufferProvider)
}

/**
 * Creates an audio track with a custom audio source provider and returns both track and mixer.
 *
 * @param name The name of the track
 * @param customAudioProvider The custom audio source provider
 * @param options Audio track options
 * @param microphoneGain Gain applied to microphone audio (1.0 = normal volume)
 * @param customAudioGain Gain applied to custom audio (1.0 = normal volume)
 * @param mixMode How to mix the custom audio with microphone
 * @return Pair of LocalAudioTrack and CustomAudioMixer
 */
fun LocalParticipant.createAudioTrackWithCustomSourceAndMixer(
    name: String = "",
    customAudioProvider: CustomAudioBufferProvider,
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    microphoneGain: Float = 1.0f,
    customAudioGain: Float = 1.0f,
    mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.ADDITIVE
): Pair<LocalAudioTrack, CustomAudioMixer> {
    // Create audio track - always create a regular track for now
    val audioTrack = createAudioTrack(name, options)

    // Create and set up the custom audio mixer
    val customMixer = CustomAudioMixer(
        customAudioProvider = customAudioProvider,
        microphoneGain = microphoneGain,
        customAudioGain = customAudioGain,
        mixMode = mixMode
    )

    // Set the custom mixer as the audio buffer callback
    audioTrack.setAudioBufferCallback(customMixer)

    // Start the custom mixer
    customMixer.start()

    return Pair(audioTrack, customMixer)
}

/**
 * Creates an audio track with a custom audio source provider.
 *
 * @param name The name of the track
 * @param customAudioProvider The custom audio source provider
 * @param options Audio track options
 * @param microphoneGain Gain applied to microphone audio (1.0 = normal volume)
 * @param customAudioGain Gain applied to custom audio (1.0 = normal volume)
 * @param mixMode How to mix the custom audio with microphone
 * @return LocalAudioTrack with custom audio mixing capability
 */
fun LocalParticipant.createAudioTrackWithCustomSource(
    name: String = "",
    customAudioProvider: CustomAudioBufferProvider,
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    microphoneGain: Float = 1.0f,
    customAudioGain: Float = 1.0f,
    mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.ADDITIVE
): LocalAudioTrack {
    val (track, _) = createAudioTrackWithCustomSourceAndMixer(
        name = name,
        customAudioProvider = customAudioProvider,
        options = options,
        microphoneGain = microphoneGain,
        customAudioGain = customAudioGain,
        mixMode = mixMode
    )
    return track
}



/**
 * Convenience function to add audio data to a buffer-based audio track.
 *
 * @param audioData The audio data as a ByteBuffer
 */
fun BufferAudioBufferProvider.addAudio(audioData: ByteBuffer) {
    addAudioData(audioData)
}

/**
 * Convenience function to add audio data to a buffer-based audio track.
 *
 * @param audioData The audio data as a ByteArray
 */
fun BufferAudioBufferProvider.addAudio(audioData: ByteArray) {
    addAudioData(audioData)
}
