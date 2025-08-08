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

import io.livekit.android.util.LKLog
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * 基于 InputStream 的音频输入源
 * 支持从任何 InputStream（如网络流、文件流等）读取音频数据
 */
class StreamAudioInputSource(
    private val inputStream: InputStream,
    override val audioConfig: CustomAudioInputSource.AudioConfig,
    private val bufferSizeMs: Int = 100,  // 内部缓冲区大小（毫秒）
    private val enableLooping: Boolean = false  // 是否循环播放（仅对有限流有效）
) : CustomAudioInputSource {
    
    private val isStarted = AtomicBoolean(false)
    private val audioDataQueue = LinkedBlockingQueue<ByteArray>()
    private var readingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 内部缓冲区
    private val internalBufferSize = audioConfig.sampleRate * audioConfig.channelCount * 
                                   audioConfig.getBytesPerSample() * bufferSizeMs / 1000
    private val readBuffer = ByteArray(internalBufferSize)
    
    // 循环播放支持
    private var streamData: ByteArray? = null
    private var streamPosition = 0
    
    override val isDataAvailable: Boolean
        get() = isStarted.get() && (audioDataQueue.isNotEmpty() || streamData != null)
    
    override fun start() {
        if (isStarted.compareAndSet(false, true)) {
            LKLog.d { "StreamAudioInputSource starting..." }
            
            if (enableLooping) {
                // 如果启用循环播放，先读取整个流到内存
                loadStreamData()
            }
            
            // 启动后台读取任务
            readingJob = scope.launch {
                try {
                    readAudioDataLoop()
                } catch (e: Exception) {
                    LKLog.e(e) { "Error in audio reading loop" }
                }
            }
        }
    }
    
    override fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            LKLog.d { "StreamAudioInputSource stopping..." }
            readingJob?.cancel()
            audioDataQueue.clear()
        }
    }
    
    override fun readData(buffer: ByteBuffer, bytesToRead: Int): Int {
        if (!isStarted.get()) {
            return 0
        }
        
        val data = audioDataQueue.poll() ?: return 0
        val bytesToCopy = min(bytesToRead, data.size)
        
        buffer.put(data, 0, bytesToCopy)
        
        // 如果数据还有剩余，将剩余部分重新放回队列
        if (bytesToCopy < data.size) {
            val remainingData = data.copyOfRange(bytesToCopy, data.size)
            audioDataQueue.offer(remainingData)
        }
        
        return bytesToCopy
    }
    
    override fun close() {
        stop()
        scope.cancel()
        try {
            inputStream.close()
        } catch (e: IOException) {
            LKLog.w(e) { "Error closing input stream" }
        }
    }
    
    private fun loadStreamData() {
        try {
            streamData = inputStream.readBytes()
            streamPosition = 0
            LKLog.d { "Loaded ${streamData?.size} bytes for looping playback" }
        } catch (e: IOException) {
            LKLog.e(e) { "Failed to load stream data for looping" }
        }
    }
    
    private suspend fun readAudioDataLoop() {
        while (isStarted.get() && !currentCoroutineContext().job.isCancelled) {
            try {
                val bytesRead = if (enableLooping && streamData != null) {
                    readFromMemoryBuffer()
                } else {
                    readFromInputStream()
                }
                
                if (bytesRead > 0) {
                    val audioData = readBuffer.copyOfRange(0, bytesRead)
                    
                    // 如果队列太满，移除旧数据避免延迟累积
                    while (audioDataQueue.size > 10) {
                        audioDataQueue.poll()
                    }
                    
                    audioDataQueue.offer(audioData)
                } else if (!enableLooping) {
                    // 流结束且不循环播放
                    LKLog.d { "End of stream reached" }
                    break
                }
                
                // 控制读取频率，避免占用过多 CPU
                delay(audioConfig.bufferSizeMs.toLong())
                
            } catch (e: IOException) {
                LKLog.e(e) { "Error reading audio data" }
                break
            }
        }
    }
    
    private fun readFromInputStream(): Int {
        return inputStream.read(readBuffer, 0, readBuffer.size)
    }
    
    private fun readFromMemoryBuffer(): Int {
        val streamData = this.streamData ?: return 0
        
        if (streamPosition >= streamData.size) {
            // 重置到开始位置
            streamPosition = 0
        }
        
        val bytesToRead = min(readBuffer.size, streamData.size - streamPosition)
        System.arraycopy(streamData, streamPosition, readBuffer, 0, bytesToRead)
        streamPosition += bytesToRead
        
        return bytesToRead
    }
} 