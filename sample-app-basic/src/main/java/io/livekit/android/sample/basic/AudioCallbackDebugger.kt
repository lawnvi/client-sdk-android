package io.livekit.android.sample.basic

import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.AudioBufferCallback
import java.nio.ByteBuffer

/**
 * 调试工具：检查音频回调是否被调用
 */
class AudioCallbackDebugger : AudioBufferCallback {
    companion object {
        private const val TAG = "AudioCallbackDebugger"
    }
    
    private var callbackCount = 0
    private val startTime = System.currentTimeMillis()
    
    override fun onBuffer(
        buffer: ByteBuffer, 
        audioFormat: Int, 
        channelCount: Int, 
        sampleRate: Int, 
        bytesRead: Int, 
        captureTimeNs: Long
    ): Long {
        callbackCount++
        
        Log.d(TAG, "🎤 Audio callback #$callbackCount")
        Log.d(TAG, "  - Buffer size: ${buffer.remaining()} bytes")
        Log.d(TAG, "  - Audio format: $audioFormat")
        Log.d(TAG, "  - Channels: $channelCount")
        Log.d(TAG, "  - Sample rate: $sampleRate")
        Log.d(TAG, "  - Bytes read: $bytesRead")
        Log.d(TAG, "  - Capture time: $captureTimeNs")
        
        if (callbackCount % 100 == 0) {
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            Log.d(TAG, "📊 Stats: $callbackCount callbacks in ${elapsed}s")
        }
        
        return captureTimeNs
    }
    
    fun getStatus(): String {
        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        return "AudioCallbackDebugger: $callbackCount calls in ${elapsed}s"
    }
}