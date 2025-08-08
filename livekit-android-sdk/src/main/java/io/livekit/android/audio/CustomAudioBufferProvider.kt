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

import java.nio.ByteBuffer

/**
 * Interface for providing custom audio data to be mixed into the audio track.
 * 
 * Implementations should provide audio data that matches the requested format,
 * channel count, and sample rate.
 */
interface CustomAudioBufferProvider {
    
    /**
     * Called when audio data is needed for the next audio frame.
     * 
     * @param requestedBytes the number of bytes requested
     * @param audioFormat the audio encoding format (see AudioFormat constants)
     * @param channelCount the number of audio channels
     * @param sampleRate the sample rate in Hz
     * @return ByteBuffer containing audio data, or null if no data is available.
     *         The buffer should contain exactly [requestedBytes] bytes or less.
     */
    fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer?
    
    /**
     * Called when the audio provider should be started.
     */
    fun start()
    
    /**
     * Called when the audio provider should be stopped.
     */
    fun stop()
    
    /**
     * Returns true if the provider has more audio data available.
     */
    fun hasMoreData(): Boolean
    
    /**
     * Returns the capture timestamp for the current audio data in nanoseconds.
     * Return 0 if not available.
     */
    fun getCaptureTimeNs(): Long = 0L
}