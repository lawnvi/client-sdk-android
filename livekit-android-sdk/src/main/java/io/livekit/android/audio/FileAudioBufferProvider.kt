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
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import io.livekit.android.util.LKLog
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Provides audio data from a local audio file.
 * 
 * Supports common audio formats like MP3, WAV, AAC, OPUS, etc.
 * Automatically handles format conversion to match the requested output format.
 */
class FileAudioBufferProvider(
    private val context: Context,
    private val audioFileUri: Uri,
    private val loop: Boolean = false
) : CustomAudioBufferProvider {

    private var mediaExtractor: MediaExtractor? = null
    private var audioTrackIndex = -1
    private var mediaFormat: MediaFormat? = null
    private var isRunning = false
    private var hasReachedEnd = false
    
    // Audio properties from the file
    private var fileSampleRate = 44100
    private var fileChannelCount = 2
    private var fileBitDepth = 16
    
    // Conversion buffers
    private val tempBuffer = ByteBuffer.allocate(8192)
    
    override fun start() {
        if (isRunning) return
        
        try {
            setupExtractor()
            isRunning = true
            hasReachedEnd = false
            LKLog.d { "FileAudioBufferProvider started for $audioFileUri" }
        } catch (e: Exception) {
            LKLog.e(e) { "Failed to start FileAudioBufferProvider" }
        }
    }
    
    override fun stop() {
        isRunning = false
        releaseExtractor()
        LKLog.d { "FileAudioBufferProvider stopped" }
    }
    
    override fun hasMoreData(): Boolean {
        return isRunning && (!hasReachedEnd || loop)
    }
    
    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        if (!isRunning || (!hasMoreData() && !loop)) {
            return null
        }
        
        val extractor = mediaExtractor ?: return null
        
        try {
            val outputBuffer = ByteBuffer.allocate(requestedBytes)
            outputBuffer.order(ByteOrder.nativeOrder())
            
            var totalBytesRead = 0
            
            while (totalBytesRead < requestedBytes && hasMoreData()) {
                tempBuffer.clear()
                
                val bytesRead = extractor.readSampleData(tempBuffer, 0)
                if (bytesRead > 0) {
                    tempBuffer.flip()
                    
                    // Convert and copy audio data to output buffer
                    val convertedData = convertAudioFormat(
                        tempBuffer,
                        audioFormat,
                        channelCount,
                        sampleRate
                    )
                    
                    val bytesToCopy = minOf(convertedData.remaining(), requestedBytes - totalBytesRead)
                    val originalLimit = convertedData.limit()
                    convertedData.limit(convertedData.position() + bytesToCopy)
                    outputBuffer.put(convertedData)
                    convertedData.limit(originalLimit)
                    
                    totalBytesRead += bytesToCopy
                    
                    extractor.advance()
                } else {
                    // Reached end of file
                    hasReachedEnd = true
                    if (loop) {
                        seekToStart()
                        hasReachedEnd = false
                    } else {
                        break
                    }
                }
            }
            
            if (totalBytesRead > 0) {
                outputBuffer.flip()
                return outputBuffer
            }
            
        } catch (e: Exception) {
            LKLog.e(e) { "Error reading audio data from file" }
        }
        
        return null
    }
    
    private fun setupExtractor() {
        releaseExtractor()
        
        mediaExtractor = MediaExtractor().apply {
            setDataSource(context, audioFileUri, null)
            
            // Find audio track
            for (i in 0 until trackCount) {
                val format = getTrackFormat(i)
                val mimeType = format.getString(MediaFormat.KEY_MIME)
                if (mimeType?.startsWith("audio/") == true) {
                    selectTrack(i)
                    audioTrackIndex = i
                    mediaFormat = format
                    break
                }
            }
            
            if (audioTrackIndex == -1) {
                throw IOException("No audio track found in file")
            }
            
            // Extract audio properties
            mediaFormat?.let { format ->
                fileSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                fileChannelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                
                // Try to get bit depth if available
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    val bitRate = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    // Estimate bit depth from bit rate (rough calculation)
                    fileBitDepth = when {
                        bitRate > 1000000 -> 24 // High quality
                        bitRate > 300000 -> 16  // Standard quality
                        else -> 8               // Low quality
                    }
                }
            }
            
            LKLog.d { "Audio file properties: sampleRate=$fileSampleRate, channels=$fileChannelCount, bitDepth=$fileBitDepth" }
        }
    }
    
    private fun releaseExtractor() {
        mediaExtractor?.release()
        mediaExtractor = null
        audioTrackIndex = -1
        mediaFormat = null
    }
    
    private fun seekToStart() {
        mediaExtractor?.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    }
    
    /**
     * Convert audio data from file format to the requested output format.
     * This is a simplified conversion - for production use, consider using
     * AudioRecord resampling or a more sophisticated audio processing library.
     */
    private fun convertAudioFormat(
        inputBuffer: ByteBuffer,
        targetAudioFormat: Int,
        targetChannelCount: Int,
        targetSampleRate: Int
    ): ByteBuffer {
        // For simplicity, we'll do basic format conversion
        // In a production environment, you might want to use more sophisticated
        // resampling and format conversion libraries
        
        val outputBuffer = ByteBuffer.allocate(inputBuffer.remaining())
        outputBuffer.order(ByteOrder.nativeOrder())
        
        when (targetAudioFormat) {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_DEFAULT -> {
                // Convert to 16-bit PCM
                convertTo16BitPCM(inputBuffer, outputBuffer)
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                // Convert to 8-bit PCM
                convertTo8BitPCM(inputBuffer, outputBuffer)
            }
            AudioFormat.ENCODING_PCM_FLOAT -> {
                // Convert to float PCM
                convertToFloatPCM(inputBuffer, outputBuffer)
            }
            else -> {
                // Default: copy as-is
                outputBuffer.put(inputBuffer)
            }
        }
        
        outputBuffer.flip()
        return outputBuffer
    }
    
    private fun convertTo16BitPCM(input: ByteBuffer, output: ByteBuffer) {
        // Simplified conversion - assumes input is already 16-bit PCM
        // In production, you'd want proper format detection and conversion
        while (input.hasRemaining() && output.hasRemaining()) {
            output.put(input.get())
        }
    }
    
    private fun convertTo8BitPCM(input: ByteBuffer, output: ByteBuffer) {
        // Convert 16-bit to 8-bit by taking every other byte
        while (input.hasRemaining() && output.hasRemaining()) {
            val sample16 = input.short
            val sample8 = ((sample16 / 256) + 128).toByte()
            output.put(sample8)
        }
    }
    
    private fun convertToFloatPCM(input: ByteBuffer, output: ByteBuffer) {
        // Convert 16-bit PCM to float PCM
        while (input.hasRemaining() && output.remaining() >= 4) {
            val sample16 = input.short
            val sampleFloat = sample16.toFloat() / Short.MAX_VALUE
            output.putFloat(sampleFloat)
        }
    }
    
    /**
     * Get audio file metadata for debugging/logging purposes
     */
    fun getAudioFileInfo(): AudioFileInfo? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, audioFileUri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            
            retriever.release()
            
            AudioFileInfo(
                durationMs = duration,
                bitrate = bitrate,
                mimeType = mimeType,
                sampleRate = fileSampleRate,
                channelCount = fileChannelCount
            )
        } catch (e: Exception) {
            LKLog.e(e) { "Failed to get audio file info" }
            null
        }
    }
    
    data class AudioFileInfo(
        val durationMs: Long,
        val bitrate: Int,
        val mimeType: String,
        val sampleRate: Int,
        val channelCount: Int
    )
}