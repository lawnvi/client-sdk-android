package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.CustomAudioMixer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * 调试版本的PCM示例，用于确认数据流
 */
class DebugPCMExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var bytesSent = 0L
    private var startTime = 0L

    suspend fun start(pcmFile: File) {
        Log.d("DebugPCM", "=== 开始调试 ===")

        if (!pcmFile.exists()) {
            Log.e("DebugPCM", "❌ PCM文件不存在: ${pcmFile.absolutePath}")
            return
        }

        Log.d("DebugPCM", "📁 PCM文件大小: ${pcmFile.length()} bytes")

        val localParticipant = room.localParticipant
        Log.d("DebugPCM", "👤 本地参与者: ${localParticipant.identity}")

        // 创建音频轨道
        Log.d("DebugPCM", "🎵 创建音频轨道...")
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
            name = "debug_pcm_track",
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,
            sampleRate = 44100,
            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
        )

        Log.d("DebugPCM", "✅ 音频轨道已创建: ${audioTrack.name}")
        Log.d("DebugPCM", "🔧 轨道状态: enabled=${audioTrack.enabled}, muted=${audioTrack.enabled}")

        // 发布音频轨道
        Log.d("DebugPCM", "📡 发布音频轨道...")
        val published = localParticipant.publishAudioTrack(audioTrack)
        Log.d("DebugPCM", "📡 发布结果: $published")

        // 等待一下让发布完成
        delay(1000)

        Log.d("DebugPCM", "📊 房间信息:")
        Log.d("DebugPCM", "  - 房间状态: ${room.state}")
        Log.d("DebugPCM", "  - 本地轨道数: ${localParticipant.audioTrackPublications.size}")
        Log.d("DebugPCM", "  - 远程参与者数: ${room.remoteParticipants.size}")

        // 开始发送音频数据
        startTime = System.currentTimeMillis()
        scope.launch {
            sendAudioData(pcmFile, bufferProvider)
        }

        // 监控状态
        scope.launch {
            monitorStatus()
        }
    }

    private suspend fun sendAudioData(pcmFile: File, bufferProvider: io.livekit.android.audio.BufferAudioBufferProvider) {
        Log.d("DebugPCM", "🔄 开始发送音频数据...")

        isRunning = true
        FileInputStream(pcmFile).use { inputStream ->
            val bufferSize = 44100 * 2 * 2 * 10 / 1000  // 10ms音频数据
            val buffer = ByteArray(bufferSize)

            Log.d("DebugPCM", "📊 缓冲区大小: $bufferSize bytes (10ms)")

            var chunkCount = 0
            while (isRunning) {
                val bytesRead = inputStream.read(buffer)

                if (bytesRead > 0) {
                    val audioData = buffer.copyOf(bytesRead)

                    // 检查缓冲区状态
                    val queuedBefore = bufferProvider.getQueuedBufferCount()
                    bufferProvider.addAudioData(audioData)
                    val queuedAfter = bufferProvider.getQueuedBufferCount()

                    bytesSent += bytesRead
                    chunkCount++

                    if (chunkCount % 100 == 0) { // 每100个包打印一次
                        Log.d("DebugPCM", "📈 已发送 $chunkCount 个包, ${bytesSent / 1024}KB, 队列: $queuedBefore->$queuedAfter")
                    }

                    delay(10) // 10ms
                } else {
                    // 文件结束，停止发送
                    Log.d("DebugPCM", "✅ 文件播放完毕，停止发送 (总共${bytesSent / 1024}KB)")
                    isRunning = false
                }
            }
        }

        Log.d("DebugPCM", "⏹️ 音频发送结束")
    }

    private suspend fun monitorStatus() {
        while (isRunning) {
            delay(5000) // 每5秒打印状态

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val kbps = if (elapsed > 0) (bytesSent / 1024) / elapsed else 0

            Log.d("DebugPCM", "📊 状态报告:")
            Log.d("DebugPCM", "  - 运行时间: ${elapsed}秒")
            Log.d("DebugPCM", "  - 已发送: ${bytesSent / 1024}KB")
            Log.d("DebugPCM", "  - 平均速率: ${kbps}KB/s")
            Log.d("DebugPCM", "  - 房间连接: ${room.state}")
            Log.d("DebugPCM", "  - 远程参与者: ${room.remoteParticipants.size}")

            // 检查轨道状态
            room.localParticipant.audioTrackPublications.forEach { (_, pub) ->
                Log.d("DebugPCM", "  - 轨道 ${pub?.name}: subscribed=, muted=${pub?.enabled}")
            }
        }
    }

    fun stop() {
        Log.d("DebugPCM", "⏹️ 停止调试")
        isRunning = false

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        Log.d("DebugPCM", "📊 最终统计:")
        Log.d("DebugPCM", "  - 总运行时间: ${elapsed}秒")
        Log.d("DebugPCM", "  - 总发送数据: ${bytesSent / 1024}KB")
    }

    /**
     * 获取当前状态
     */
    fun getStatus(): String {
        val elapsed = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000 else 0
        val kbps = if (elapsed > 0) (bytesSent / 1024) / elapsed else 0

        return """
运行状态: ${if (isRunning) "🟢 运行中" else "🔴 已停止"}
运行时间: ${elapsed}秒
已发送: ${bytesSent / 1024}KB
发送速率: ${kbps}KB/s
房间状态: ${room.state}
远程参与者: ${room.remoteParticipants.size}
本地轨道: ${room.localParticipant.audioTrackPublications.size}
        """.trimIndent()
    }
}
