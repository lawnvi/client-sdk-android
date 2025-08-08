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
import android.net.Uri
import io.livekit.android.room.participant.LocalParticipant
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.participant.createAudioTrackWithFile
import java.nio.ByteBuffer

/**
 * Quick start examples for custom audio functionality.
 * 
 * These examples show the simplest way to implement custom audio sources.
 */
class QuickStartCustomAudio {

    /**
     * Quick example: Play an audio file with microphone
     */
    suspend fun playAudioFile(
        context: Context,
        localParticipant: LocalParticipant,
        audioFileUri: Uri
    ) {
        // Create and publish audio track with file
        val audioTrack = localParticipant.createAudioTrackWithFile(
            context = context,
            audioFileUri = audioFileUri,
            loop = true,  // Loop the file
            microphoneGain = 0.7f,  // Lower mic volume
            customAudioGain = 0.8f  // Custom audio volume
        )
        
        localParticipant.publishAudioTrack(audioTrack)
    }

    /**
     * Quick example: Stream audio from buffer
     */
    suspend fun streamAudioBuffer(localParticipant: LocalParticipant) {
        // Create buffer-based audio track
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer()
        
        // Publish the track
        localParticipant.publishAudioTrack(audioTrack)
        
        // Add audio data (example with dummy data)
        val audioData = ByteArray(1024) // Your actual audio data here
        bufferProvider.addAudioData(audioData)
    }

    /**
     * Quick example: Replace microphone with file audio
     */
    suspend fun replaceWithFileAudio(
        context: Context,
        localParticipant: LocalParticipant,
        audioFileUri: Uri
    ) {
        val audioTrack = localParticipant.createAudioTrackWithFile(
            context = context,
            audioFileUri = audioFileUri,
            mixMode = io.livekit.android.audio.CustomAudioMixer.MixMode.REPLACE
        )
        
        localParticipant.publishAudioTrack(audioTrack)
    }
}