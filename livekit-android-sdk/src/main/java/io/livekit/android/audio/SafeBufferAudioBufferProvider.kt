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

import android.media.AudioFormat
import android.util.Log
import io.livekit.android.room.participant.createEnhancedCustomAudioTrack
import io.livekit.android.util.LKLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 安全版本的音频缓冲区提供器
 *
 * 修复了原版BufferAudioBufferProvider中的BufferOverflowException问题，
 * 并提供了更好的错误处理和调试功能。
 */
class SafeBufferAudioBufferProvider(
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    private val channelCount: Int = 2,
    private val sampleRate: Int = 44100,
    private val loop: Boolean = false,
    private val maxQueueSize: Int = 50 // 限制队列大小防止内存溢出
) : CustomAudioBufferProvider {

    companion object {
        private const val TAG = "SafeBufferAudioProvider"
    }

    private val audioDataQueue = ConcurrentLinkedQueue<ByteBuffer>()
    private val isRunning = AtomicBoolean(false)
    private var currentBuffer: ByteBuffer? = null
    private val originalBuffers = mutableListOf<ByteBuffer>() // For looping

    // 统计信息
    private val totalBuffersAdded = AtomicLong(0)
    private val totalBytesAdded = AtomicLong(0)
    private val totalBuffersProvided = AtomicLong(0)
    private val totalBytesProvided = AtomicLong(0)
    private val bufferOverflowCount = AtomicInteger(0)
    private val queueOverflowCount = AtomicInteger(0)

    override fun start() {
        if (isRunning.compareAndSet(false, true)) {
            LKLog.d { "$TAG: 启动安全音频缓冲区提供器" }
            LKLog.d { "$TAG: 配置 - 格式: $audioFormat, 声道: $channelCount, 采样率: $sampleRate, 循环: $loop" }
        }
    }

    override fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            audioDataQueue.clear()
            currentBuffer = null

            val totalAdded = totalBuffersAdded.get()
            val totalProvided = totalBuffersProvided.get()
            val overflowErrors = bufferOverflowCount.get()
            val queueOverflows = queueOverflowCount.get()

            LKLog.d { "$TAG: 停止音频缓冲区提供器" }
            LKLog.i { "$TAG: 统计 - 添加: $totalAdded 缓冲区, 提供: $totalProvided 缓冲区, 溢出错误: $overflowErrors, 队列溢出: $queueOverflows" }
        }
    }

    override fun hasMoreData(): Boolean {
        return isRunning.get() && (
            currentBuffer?.hasRemaining() == true ||
            audioDataQueue.isNotEmpty() ||
            (loop && originalBuffers.isNotEmpty())
        )
    }

    override fun getCaptureTimeNs(): Long = System.nanoTime()

    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        if (!isRunning.get()) {
            return null
        }

        try {
            val outputBuffer = ByteBuffer.allocate(requestedBytes)
            outputBuffer.order(ByteOrder.nativeOrder())

            var totalBytesRead = 0

            while (totalBytesRead < requestedBytes && hasMoreData()) {
                // Get current buffer if we don't have one
                if (currentBuffer?.hasRemaining() != true) {
                    currentBuffer = getNextBuffer()
                }

                currentBuffer?.let { buffer ->
                    val bytesToCopy = minOf(buffer.remaining(), requestedBytes - totalBytesRead)
                    if (bytesToCopy > 0) {
                        // 安全地复制数据
                        val originalLimit = buffer.limit()
                        buffer.limit(buffer.position() + bytesToCopy)
                        outputBuffer.put(buffer)
                        buffer.limit(originalLimit)

                        totalBytesRead += bytesToCopy
                    }
                }
            }

            if (totalBytesRead > 0) {
                outputBuffer.flip()
                totalBuffersProvided.incrementAndGet()
                totalBytesProvided.addAndGet(totalBytesRead.toLong())
                return outputBuffer
            }

        } catch (e: Exception) {
            LKLog.e(e) { "$TAG: 提供音频数据时发生错误" }
        }

        return null
    }

    /**
     * 安全地添加音频数据
     */
    fun addAudioData(audioData: ByteBuffer): Boolean {
        return try {
            addAudioDataInternal(audioData)
        } catch (e: Exception) {
            bufferOverflowCount.incrementAndGet()
            LKLog.e(e) { "$TAG: 添加音频数据失败" }
            false
        }
    }

    /**
     * 安全地添加音频数据（字节数组版本）
     */
    fun addAudioData(audioData: ByteArray): Boolean {
        return try {
            if (audioData.isEmpty()) {
                LKLog.w { "$TAG: 尝试添加空音频数据" }
                return false
            }

            val buffer = ByteBuffer.allocate(audioData.size)
            buffer.put(audioData)
            buffer.flip()
            addAudioDataInternal(buffer)
        } catch (e: Exception) {
            bufferOverflowCount.incrementAndGet()
            LKLog.e(e) { "$TAG: 添加音频数据失败（字节数组）" }
            false
        }
    }

    private fun addAudioDataInternal(audioData: ByteBuffer): Boolean {
        if (!isRunning.get()) {
            LKLog.w { "$TAG: 提供器未运行，忽略音频数据" }
            return false
        }

        // 检查队列大小限制
        if (audioDataQueue.size >= maxQueueSize) {
            queueOverflowCount.incrementAndGet()
            LKLog.w { "$TAG: 队列已满（${audioDataQueue.size}/$maxQueueSize），丢弃旧数据" }

            // 移除一些旧数据为新数据腾出空间
            repeat(maxQueueSize / 4) {
                audioDataQueue.poll()
            }
        }

        val dataSize = audioData.remaining()
        if (dataSize <= 0) {
            LKLog.w { "$TAG: 音频数据大小为0，跳过" }
            return false
        }

        // 保存原始位置和限制
        val originalPosition = audioData.position()
        val originalLimit = audioData.limit()

        // 创建队列缓冲区副本
        val bufferCopy = ByteBuffer.allocate(dataSize)
        bufferCopy.order(audioData.order())
        bufferCopy.put(audioData)
        bufferCopy.flip()

        audioDataQueue.offer(bufferCopy)
        totalBuffersAdded.incrementAndGet()
        totalBytesAdded.addAndGet(dataSize.toLong())

        // Store for looping if enabled
        if (loop) {
            try {
                // 重置audioData到原始状态
                audioData.position(originalPosition)
                audioData.limit(originalLimit)

                val originalCopy = ByteBuffer.allocate(dataSize)
                originalCopy.order(audioData.order())
                originalCopy.put(audioData)
                originalCopy.flip()

                synchronized(originalBuffers) {
                    originalBuffers.add(originalCopy)

                    // 限制循环缓冲区的数量，防止内存溢出
                    if (originalBuffers.size > maxQueueSize) {
                        originalBuffers.removeAt(0)
                    }
                }

            } catch (e: Exception) {
                LKLog.e(e) { "$TAG: 存储循环数据时发生错误" }
            }
        }

        Log.d(TAG, "添加音频数据: $dataSize bytes, 队列大小: ${audioDataQueue.size}, 总添加: ${totalBuffersAdded.get()}")
        return true
    }

    /**
     * 清除所有音频数据
     */
    fun clearAudioData() {
        audioDataQueue.clear()
        currentBuffer = null
        synchronized(originalBuffers) {
            originalBuffers.clear()
        }
        LKLog.d { "$TAG: 清除所有音频数据" }
    }

    /**
     * 获取队列中的缓冲区数量
     */
    fun getQueuedBufferCount(): Int = audioDataQueue.size

    /**
     * 获取统计信息
     */
    fun getStatistics(): String {
        val totalAdded = totalBuffersAdded.get()
        val totalProvided = totalBuffersProvided.get()
        val bytesAdded = totalBytesAdded.get()
        val bytesProvided = totalBytesProvided.get()
        val overflowErrors = bufferOverflowCount.get()
        val queueOverflows = queueOverflowCount.get()
        val queueSize = audioDataQueue.size
        val originalBufferCount = synchronized(originalBuffers) { originalBuffers.size }

        return """
            安全音频缓冲区提供器统计:
            - 运行状态: ${isRunning.get()}
            - 音频格式: $audioFormat, 声道: $channelCount, 采样率: $sampleRate
            - 循环模式: $loop
            - 队列大小: $queueSize / $maxQueueSize
            - 原始缓冲区: $originalBufferCount
            - 添加缓冲区: $totalAdded (${bytesAdded / 1024}KB)
            - 提供缓冲区: $totalProvided (${bytesProvided / 1024}KB)
            - 溢出错误: $overflowErrors
            - 队列溢出: $queueOverflows
            - 有更多数据: ${hasMoreData()}
        """.trimIndent()
    }

    private fun getNextBuffer(): ByteBuffer? {
        // 首先尝试从队列获取
        var buffer = audioDataQueue.poll()

        // 如果队列为空且开启了循环，从原始缓冲区获取
        if (buffer == null && loop) {
            synchronized(originalBuffers) {
                if (originalBuffers.isNotEmpty()) {
                    val originalBuffer = originalBuffers.random() // 随机选择一个缓冲区
                    buffer = ByteBuffer.allocate(originalBuffer.remaining())
                    buffer?.put(originalBuffer.duplicate())
                    buffer?.flip()
                }
            }
        }

        return buffer
    }
}

/**
 * 安全音频缓冲区提供器扩展函数
 */
fun io.livekit.android.room.participant.LocalParticipant.createSafeAudioTrackWithBuffer(
    name: String = "",
    audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    channelCount: Int = 2,
    sampleRate: Int = 44100,
    loop: Boolean = false,
    maxQueueSize: Int = 50
): Pair<io.livekit.android.room.track.LocalAudioTrack, SafeBufferAudioBufferProvider> {

    val safeProvider = SafeBufferAudioBufferProvider(
        audioFormat = audioFormat,
        channelCount = channelCount,
        sampleRate = sampleRate,
        loop = loop,
        maxQueueSize = maxQueueSize
    )

    val (audioTrack, mixer, monitor) = this.createEnhancedCustomAudioTrack(
        name = name,
        customAudioProvider = safeProvider,
        enableDebug = true
    )

    return Pair(audioTrack, safeProvider)
}
