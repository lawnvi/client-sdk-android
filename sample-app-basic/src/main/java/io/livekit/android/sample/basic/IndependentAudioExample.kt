package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.StandaloneCustomAudioGenerator
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createStandaloneAudioTrackWithBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

/**
 * 完全独立于麦克风的自定义音频输入示例
 *
 * 这个示例展示了如何在不依赖麦克风的情况下实现自定义音频输入
 */
class IndependentAudioExample(
    private val context: Context,
    private val room: Room
) {
    companion object {
        private const val TAG = "IndependentAudio"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var standaloneGenerator: StandaloneCustomAudioGenerator? = null
    private var isRunning = false
    private var bytesSent = 0L
    private var startTime = 0L

    suspend fun start(pcmFile: File) {
        Log.d(TAG, "🚀 开始独立自定义音频输入测试")
        Log.d(TAG, "💡 此示例完全不依赖麦克风，使用手动音频回调驱动")

        if (!pcmFile.exists()) {
            Log.e(TAG, "❌ PCM文件不存在: ${pcmFile.absolutePath}")
            return
        }

        Log.d(TAG, "📁 PCM文件大小: ${pcmFile.length()} bytes")

        val localParticipant = room.localParticipant
        Log.d(TAG, "👤 本地参与者: ${localParticipant.identity}")

        try {
            // 创建独立的自定义音频轨道
            Log.d(TAG, "🎵 创建独立自定义音频轨道...")
            val (audioTrack, bufferProvider, generator) = localParticipant.createStandaloneAudioTrackWithBuffer(
                name = "independent_custom_audio",
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                channelCount = 2,
                sampleRate = 44100,
                loop = false
            )

            standaloneGenerator = generator

            Log.d(TAG, "✅ 独立音频轨道已创建: ${audioTrack.name}")
            Log.d(TAG, "🔧 轨道状态: enabled=${audioTrack.enabled}")
            Log.d(TAG, "🎛️ 生成器状态: ${generator.getStatusInfo()}")

            // 发布音频轨道
            Log.d(TAG, "📡 发布音频轨道...")
            localParticipant.publishAudioTrack(audioTrack)
            Log.d(TAG, "✅ 音频轨道发布成功")

            // 启用轨道
            audioTrack.enabled = true
            Log.d(TAG, "✅ 音频轨道已启用")

            // 等待发布完成
            delay(1000)

            // 验证房间状态
            Log.d(TAG, "📊 房间信息:")
            Log.d(TAG, "  - 房间状态: ${room.state}")
            Log.d(TAG, "  - 本地轨道数: ${localParticipant.audioTrackPublications.size}")
            Log.d(TAG, "  - 远程参与者数: ${room.remoteParticipants.size}")

            // 开始独立音频生成
            Log.d(TAG, "🎵 启动独立音频生成器...")
            generator.start()

            // 开始发送音频数据
            startTime = System.currentTimeMillis()
            scope.launch {
                sendAudioData(pcmFile, bufferProvider)
            }

            // 监控状态
            scope.launch {
                monitorStatus()
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 创建独立音频轨道失败", e)
        }
    }

    private suspend fun sendAudioData(pcmFile: File, bufferProvider: io.livekit.android.audio.BufferAudioBufferProvider) {
        Log.d(TAG, "🔄 开始发送PCM音频数据...")

        isRunning = true
        FileInputStream(pcmFile).use { inputStream ->
            // 使用10ms的音频帧
            val bufferSize = 44100 * 2 * 2 * 10 / 1000  // sampleRate * channels * bytesPerSample * ms / 1000
            val buffer = ByteArray(bufferSize)

            Log.d(TAG, "📊 音频帧大小: $bufferSize bytes (10ms)")

            var chunkCount = 0
            var loopCount = 0

            while (isRunning && chunkCount < 5000) { // 限制最大包数
                val bytesRead = inputStream.read(buffer)

                if (bytesRead > 0) {
                    val audioData = buffer.copyOf(bytesRead)

                    // 添加音频数据到提供器
                    bufferProvider.addAudioData(audioData)

                    bytesSent += bytesRead
                    chunkCount++

                    if (chunkCount % 200 == 0) { // 每200个包打印一次
                        val queueSize = bufferProvider.getQueuedBufferCount()
                        Log.d(TAG, "📈 已发送 $chunkCount 个包, ${bytesSent / 1024}KB, 队列大小: $queueSize")
                    }

                    delay(10) // 10ms延迟匹配音频帧率
                } else {
                    // 文件结束，循环播放
                    loopCount++
                    Log.d(TAG, "🔄 循环播放第 $loopCount 次")
                    inputStream.channel.position(0) // 重置到文件开头

                    if (loopCount >= 5) { // 最多循环5次
                        Log.d(TAG, "✅ 播放完成 (循环 $loopCount 次，总共${bytesSent / 1024}KB)")
                        break
                    }
                }
            }
        }

        isRunning = false
        Log.d(TAG, "⏹️ 音频数据发送结束")
    }

    private suspend fun monitorStatus() {
        while (isRunning) {
            delay(10000) // 每10秒打印状态

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            val kbps = if (elapsed > 0) (bytesSent / 1024) / elapsed else 0

            Log.d(TAG, "📊 状态报告:")
            Log.d(TAG, "  - 运行时间: ${elapsed}秒")
            Log.d(TAG, "  - 已发送: ${bytesSent / 1024}KB")
            Log.d(TAG, "  - 平均速率: ${kbps}KB/s")
            Log.d(TAG, "  - 房间连接: ${room.state}")
            Log.d(TAG, "  - 生成器状态: ${standaloneGenerator?.isActive()}")

            // 检查轨道状态
            room.localParticipant.audioTrackPublications.forEach { (_, pub) ->
                Log.d(TAG, "  - 轨道 ${pub?.name}: enabled=${pub?.enabled}")
            }

            // 显示生成器详细状态
            standaloneGenerator?.let { generator ->
                Log.d(TAG, "🎛️ ${generator.getStatusInfo()}")
            }
        }
    }

    fun stop() {
        Log.d(TAG, "⏹️ 停止独立自定义音频输入")
        isRunning = false

        // 停止音频生成器
        standaloneGenerator?.stop()

        val elapsed = (System.currentTimeMillis() - startTime) / 1000
        Log.d(TAG, "📊 最终统计:")
        Log.d(TAG, "  - 总运行时间: ${elapsed}秒")
        Log.d(TAG, "  - 总发送数据: ${bytesSent / 1024}KB")
        Log.d(TAG, "  - 平均速率: ${if (elapsed > 0) (bytesSent / 1024) / elapsed else 0}KB/s")
    }

    /**
     * 生成测试用的正弦波PCM数据
     */
    fun generateTestSineWave(outputFile: File, durationSeconds: Int = 5) {
        Log.d(TAG, "🎶 生成测试正弦波文件: ${outputFile.absolutePath}")

        val sampleRate = 44100
        val channels = 2
        val frequency = 440.0 // A音
        val amplitude = 0.3f

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { output ->
            repeat(durationSeconds * sampleRate) { i ->
                val sample = (amplitude * Short.MAX_VALUE * kotlin.math.sin(2.0 * kotlin.math.PI * frequency * i / sampleRate)).toInt()
                val clampedSample = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

                // 写入立体声数据
                repeat(channels) {
                    output.write(clampedSample.toInt() and 0xFF)
                    output.write((clampedSample.toInt() shr 8) and 0xFF)
                }
            }
        }

        Log.d(TAG, "✅ 测试文件生成完成: ${outputFile.length()} bytes")
    }

    /**
     * 获取当前状态信息
     */
    fun getStatusInfo(): String {
        val elapsed = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000 else 0
        val kbps = if (elapsed > 0) (bytesSent / 1024) / elapsed else 0

        return """
🎵 独立自定义音频输入状态
运行状态: ${if (isRunning) "🟢 运行中" else "🔴 已停止"}
运行时间: ${elapsed}秒
已发送: ${bytesSent / 1024}KB
发送速率: ${kbps}KB/s
房间状态: ${room.state}
远程参与者: ${room.remoteParticipants.size}
本地轨道: ${room.localParticipant.audioTrackPublications.size}
生成器活跃: ${standaloneGenerator?.isActive() ?: false}
        """.trimIndent()
    }
}
