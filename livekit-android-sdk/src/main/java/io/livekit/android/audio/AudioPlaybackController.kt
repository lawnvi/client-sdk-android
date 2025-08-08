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

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import io.livekit.android.util.LKLog
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controller for custom audio playback when intercepting remote audio data.
 * 
 * This class provides the ability to play intercepted audio through the system speakers
 * with custom processing, volume control, and mixing capabilities.
 */
class AudioPlaybackController(
    private val context: Context,
    private val sampleRate: Int = 48000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_STEREO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    
    private var audioTrack: AudioTrack? = null
    private var isInitialized = AtomicBoolean(false)
    private var isPlaying = AtomicBoolean(false)
    
    private val channelCount = when (channelConfig) {
        AudioFormat.CHANNEL_OUT_MONO -> 1
        AudioFormat.CHANNEL_OUT_STEREO -> 2
        else -> 2
    }
    
    /**
     * Initialize the audio playback system.
     */
    fun initialize(): Boolean {
        if (isInitialized.get()) {
            return true
        }
        
        try {
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            if (bufferSize == AudioTrack.ERROR_BAD_VALUE) {
                LKLog.e { "Invalid audio configuration for playback" }
                return false
            }
            
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            
            val audioFormatBuilder = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .setEncoding(audioFormat)
            
            audioTrack = AudioTrack(
                audioAttributes,
                audioFormatBuilder.build(),
                bufferSize * 2, // Use double buffer size for smoother playback
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            
            isInitialized.set(true)
            LKLog.d { "AudioPlaybackController initialized with sampleRate=$sampleRate, channels=$channelCount" }
            return true
            
        } catch (e: Exception) {
            LKLog.e(e) { "Failed to initialize AudioPlaybackController" }
            return false
        }
    }
    
    /**
     * Start audio playback.
     */
    fun start(): Boolean {
        if (!isInitialized.get()) {
            if (!initialize()) {
                return false
            }
        }
        
        try {
            audioTrack?.play()
            isPlaying.set(true)
            LKLog.d { "AudioPlaybackController started" }
            return true
        } catch (e: Exception) {
            LKLog.e(e) { "Failed to start audio playback" }
            return false
        }
    }
    
    /**
     * Stop audio playback.
     */
    fun stop() {
        try {
            audioTrack?.stop()
            isPlaying.set(false)
            LKLog.d { "AudioPlaybackController stopped" }
        } catch (e: Exception) {
            LKLog.e(e) { "Error stopping audio playback" }
        }
    }
    
    /**
     * Release resources.
     */
    fun release() {
        stop()
        try {
            audioTrack?.release()
            audioTrack = null
            isInitialized.set(false)
            LKLog.d { "AudioPlaybackController released" }
        } catch (e: Exception) {
            LKLog.e(e) { "Error releasing audio playback resources" }
        }
    }
    
    /**
     * Play audio data.
     * 
     * @param audioData The audio data to play
     * @param offsetInBytes Offset in the audio data to start playing from
     * @param sizeInBytes Number of bytes to play
     * @return Number of bytes written, or negative value on error
     */
    fun playAudioData(audioData: ByteBuffer, offsetInBytes: Int = 0, sizeInBytes: Int = audioData.remaining()): Int {
        if (!isPlaying.get()) {
            if (!start()) {
                return -1
            }
        }
        
        val track = audioTrack ?: return -1
        
        return try {
            // Create a byte array from the ByteBuffer
            val audioBytes = ByteArray(sizeInBytes)
            audioData.position(offsetInBytes)
            audioData.get(audioBytes, 0, sizeInBytes)
            
            track.write(audioBytes, 0, sizeInBytes)
        } catch (e: Exception) {
            LKLog.e(e) { "Error writing audio data" }
            -1
        }
    }
    
    /**
     * Play audio data from a byte array.
     */
    fun playAudioData(audioData: ByteArray, offsetInBytes: Int = 0, sizeInBytes: Int = audioData.size): Int {
        if (!isPlaying.get()) {
            if (!start()) {
                return -1
            }
        }
        
        val track = audioTrack ?: return -1
        
        return try {
            track.write(audioData, offsetInBytes, sizeInBytes)
        } catch (e: Exception) {
            LKLog.e(e) { "Error writing audio data" }
            -1
        }
    }
    
    /**
     * Set the playback volume.
     * 
     * @param volume Volume level (0.0 to 1.0)
     */
    fun setVolume(volume: Float) {
        try {
            audioTrack?.setVolume(volume.coerceIn(0.0f, 1.0f))
        } catch (e: Exception) {
            LKLog.e(e) { "Error setting volume" }
        }
    }
    
    /**
     * Get the current playback state.
     */
    fun getPlaybackState(): Int {
        return audioTrack?.playState ?: AudioTrack.PLAYSTATE_STOPPED
    }
    
    /**
     * Check if the controller is ready for playback.
     */
    fun isReady(): Boolean {
        return isInitialized.get() && audioTrack != null
    }
    
    /**
     * Check if audio is currently playing.
     */
    fun isPlaying(): Boolean {
        return isPlaying.get() && getPlaybackState() == AudioTrack.PLAYSTATE_PLAYING
    }
}

/**
 * A specialized interceptor that provides easy audio playback control.
 * This allows you to process audio data while maintaining the ability to play it.
 */
abstract class PlaybackControlledInterceptor(
    private val context: Context,
    private val enablePlayback: Boolean = true
) : RemoteAudioDataInterceptor {
    
    private var playbackController: AudioPlaybackController? = null
    
    init {
        if (enablePlayback) {
            playbackController = AudioPlaybackController(context)
        }
    }
    
    /**
     * Process the audio data. Called for every audio frame.
     * 
     * @param participant The participant who sent the audio
     * @param trackSid The track SID
     * @param audioData The audio data buffer
     * @param bitsPerSample Bits per sample
     * @param sampleRate Sample rate in Hz
     * @param numberOfChannels Number of channels
     * @param numberOfFrames Number of frames
     * @param timestamp Timestamp
     * @param playbackController Controller for audio playback (null if playback is disabled)
     * @return Whether to allow automatic playback (if playbackController is null)
     */
    abstract fun processAndControlPlayback(
        participant: io.livekit.android.room.participant.Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long,
        playbackController: AudioPlaybackController?
    ): Boolean
    
    final override fun onRemoteAudioData(
        participant: io.livekit.android.room.participant.Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ): RemoteAudioDataInterceptor.InterceptorResult {
        
        val shouldAllowPlayback = processAndControlPlayback(
            participant, trackSid, audioData, bitsPerSample, sampleRate,
            numberOfChannels, numberOfFrames, timestamp, playbackController
        )
        
        return RemoteAudioDataInterceptor.InterceptorResult(
            allowPlayback = shouldAllowPlayback && playbackController == null
        )
    }
    
    /**
     * Start the playback controller if available.
     */
    fun startPlayback(): Boolean {
        return playbackController?.start() ?: false
    }
    
    /**
     * Stop the playback controller if available.
     */
    fun stopPlayback() {
        playbackController?.stop()
    }
    
    /**
     * Release playback resources.
     */
    fun releasePlayback() {
        playbackController?.release()
        playbackController = null
    }
}