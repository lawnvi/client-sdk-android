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
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * PCM 文件音频输入源
 * 支持读取原始 PCM 音频文件（无文件头的纯音频数据）
 */
class PcmFileAudioInputSource private constructor(
    private val inputStream: InputStream,
    private val fileSize: Long,
    override val audioConfig: CustomAudioInputSource.AudioConfig,
    private val enableLooping: Boolean
) : CustomAudioInputSource {
    
    private val isStarted = AtomicBoolean(false)
    private val audioDataQueue = LinkedBlockingQueue<ByteArray>()
    private var readingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 读取进度跟踪
    private val bytesRead = AtomicLong(0)
    private var fileData: ByteArray? = null
    private var currentPosition = 0
    
    // 计算音频文件的时长
    val durationMs: Long by lazy {
        val bytesPerSecond = audioConfig.sampleRate * audioConfig.channelCount * audioConfig.getBytesPerSample()
        (fileSize * 1000) / bytesPerSecond
    }
    
    /**
     * 获取当前播放进度（0.0 到 1.0）
     */
    val playbackProgress: Float
        get() = if (fileSize > 0) {
            (bytesRead.get().toFloat() / fileSize).coerceIn(0f, 1f)
        } else 0f
    
    override val isDataAvailable: Boolean
        get() = isStarted.get() && (audioDataQueue.isNotEmpty() || 
                                   (enableLooping && fileData != null) || 
                                   bytesRead.get() < fileSize)
    
    companion object {
        /**
         * 从文件路径创建 PCM 音频输入源
         */
        fun fromFile(
            filePath: String,
            audioConfig: CustomAudioInputSource.AudioConfig,
            enableLooping: Boolean = false
        ): PcmFileAudioInputSource {
            val file = File(filePath)
            if (!file.exists()) {
                throw IllegalArgumentException("PCM file not found: $filePath")
            }
            if (!file.canRead()) {
                throw IllegalArgumentException("PCM file is not readable: $filePath")
            }
            return PcmFileAudioInputSource(
                inputStream = FileInputStream(file),
                fileSize = file.length(),
                audioConfig = audioConfig,
                enableLooping = enableLooping
            )
        }
    }
    
    override fun start() {
        if (isStarted.compareAndSet(false, true)) {
            LKLog.d { "PcmFileAudioInputSource starting... File size: $fileSize bytes, Duration: ${durationMs}ms" }
            
            if (enableLooping) {
                loadFileToMemory()
            }
            
            readingJob = scope.launch {
                try {
                    readAudioFileLoop()
                } catch (e: Exception) {
                    LKLog.e(e) { "Error in PCM file reading loop" }
                }
            }
        }
    }
    
    override fun stop() {
        if (isStarted.compareAndSet(true, false)) {
            LKLog.d { "PcmFileAudioInputSource stopping..." }
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
            LKLog.w(e) { "Error closing PCM file input stream" }
        }
    }
    
    /**
     * 跳转到指定的播放位置
     * @param progressRatio 播放进度比例（0.0 到 1.0）
     */
    fun seekTo(progressRatio: Float) {
        if (!enableLooping || fileData == null) {
            LKLog.w { "Seek is only supported when looping is enabled" }
            return
        }
        
        val targetPosition = (fileData!!.size * progressRatio.coerceIn(0f, 1f)).toInt()
        
        // 确保位置对齐到音频帧边界
        val frameSize = audioConfig.channelCount * audioConfig.getBytesPerSample()
        currentPosition = (targetPosition / frameSize) * frameSize
        
        LKLog.d { "Seeked to position: $currentPosition (${progressRatio * 100}%)" }
    }
    
    private fun loadFileToMemory() {
        if (fileSize > 100 * 1024 * 1024) { // 100MB 限制
            LKLog.w { "PCM file too large for memory loading: ${fileSize / 1024 / 1024}MB" }
            return
        }
        
        try {
            fileData = inputStream.readBytes()
            currentPosition = 0
            LKLog.d { "Loaded PCM file to memory: ${fileData?.size} bytes" }
        } catch (e: IOException) {
            LKLog.e(e) { "Failed to load PCM file to memory" }
        }
    }
    
    private suspend fun readAudioFileLoop() {
        val chunkSize = audioConfig.getBufferSizeBytes()
        val readBuffer = ByteArray(chunkSize)
        
        while (isStarted.get() && !currentCoroutineContext().job.isCancelled) {
            try {
                val bytesReadThisTime = if (enableLooping && fileData != null) {
                    readFromMemory(readBuffer)
                } else {
                    readFromStream(readBuffer)
                }
                
                if (bytesReadThisTime > 0) {
                    val audioChunk = readBuffer.copyOfRange(0, bytesReadThisTime)
                    bytesRead.addAndGet(bytesReadThisTime.toLong())
                    
                    // 控制队列大小避免内存占用过多
                    while (audioDataQueue.size > 20) {
                        audioDataQueue.poll()
                    }
                    
                    audioDataQueue.offer(audioChunk)
                } else if (!enableLooping) {
                    // 文件读取完毕且不循环播放
                    LKLog.d { "PCM file playback completed" }
                    break
                }
                
                // 控制读取速度，模拟实时播放
                delay(audioConfig.bufferSizeMs.toLong())
                
            } catch (e: IOException) {
                LKLog.e(e) { "Error reading PCM file" }
                break
            }
        }
    }
    
    private fun readFromStream(buffer: ByteArray): Int {
        return inputStream.read(buffer, 0, buffer.size)
    }
    
    private fun readFromMemory(buffer: ByteArray): Int {
        val fileData = this.fileData ?: return 0
        
        if (currentPosition >= fileData.size) {
            // 重置到文件开始
            currentPosition = 0
        }
        
        val bytesToRead = min(buffer.size, fileData.size - currentPosition)
        if (bytesToRead <= 0) return 0
        
        System.arraycopy(fileData, currentPosition, buffer, 0, bytesToRead)
        currentPosition += bytesToRead
        
        return bytesToRead
    }
} 