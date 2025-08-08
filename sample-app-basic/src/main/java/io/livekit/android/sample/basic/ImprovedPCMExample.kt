package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.CustomAudioMixer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 改进的PCM音频示例
 * 修复了原有的问题，确保自定义音频输入正常工作
 */
class ImprovedPCMExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    private var outputStream: FileOutputStream? = null

    /**
     * 开始示例：从 PCM 文件播放，录制远程音频
     */
    suspend fun startExample(inputPCMFile: File, outputPCMFile: File) {
        try {
            Log.d("ImprovedPCM", "🚀 开始音频示例")

            // 首先检查输入文件
            if (!inputPCMFile.exists()) {
                Log.e("ImprovedPCM", "❌ 输入文件不存在: ${inputPCMFile.absolutePath}")
                return
            }

            Log.d("ImprovedPCM", "📁 输入文件大小: ${inputPCMFile.length()} bytes")

            // 1. 设置音频输入（从 PCM 文件播放）
            setupAudioInput(inputPCMFile)

            // 2. 设置音频输出（录制远程音频到文件）
            setupAudioOutput(outputPCMFile)

            Log.d("ImprovedPCM", "✅ 示例启动成功")
            Log.d("ImprovedPCM", "📥 输入文件: ${inputPCMFile.absolutePath}")
            Log.d("ImprovedPCM", "📤 输出文件: ${outputPCMFile.absolutePath}")

        } catch (e: Exception) {
            Log.e("ImprovedPCM", "❌ 启动示例失败", e)
        }
    }

    /**
     * 设置音频输入：从 PCM 文件读取并发布到房间
     */
    private suspend fun setupAudioInput(inputFile: File) {
        val localParticipant = room.localParticipant

        // 创建音频轨道 - 使用CUSTOM_ONLY模式，避免麦克风干扰
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
            name = "pcm_file_input",
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,              // 立体声
            sampleRate = 44100,            // 标准采样率
            microphoneGain = 0.0f,         // 完全禁用麦克风
            customAudioGain = 1.0f,        // 自定义音频全音量
            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY  // 只使用自定义音频
        )

        Log.d("ImprovedPCM", "🎵 音频轨道已创建: ${audioTrack.name}")

        // 发布音频轨道
        val published = localParticipant.publishAudioTrack(audioTrack)
        Log.d("ImprovedPCM", "📡 音频轨道发布结果: $published")

        // 开始读取文件并推送音频数据
        scope.launch {
            try {
                isRunning = true
                Log.d("ImprovedPCM", "🔄 开始读取和发送音频数据...")

                FileInputStream(inputFile).use { inputStream ->
                    // 计算每次读取的数据量（约10ms的音频数据）
                    val channelCount = 2
                    val sampleRate = 44100
                    val bytesPerSample = 2  // 16-bit
                    val bufferDurationMs = 10  // 10毫秒
                    val samplesPerBuffer = (sampleRate * bufferDurationMs) / 1000
                    val bufferSize = samplesPerBuffer * channelCount * bytesPerSample

                    Log.d("ImprovedPCM", "📊 缓冲区大小: $bufferSize bytes (${bufferDurationMs}ms)")

                    val buffer = ByteArray(bufferSize)
                    var totalBytesRead = 0
                    var loopCount = 0

                    while (isRunning && isActive) {
                        val bytesRead = inputStream.read(buffer)

                        if (bytesRead > 0) {
                            val audioData = if (bytesRead == buffer.size) {
                                buffer
                            } else {
                                buffer.copyOf(bytesRead)
                            }

                            // 添加音频数据到缓冲区
                            bufferProvider.addAudioData(audioData)
                            totalBytesRead += bytesRead

                            if (totalBytesRead % (sampleRate * 4) == 0) { // 每秒打印一次
                                val seconds = totalBytesRead / (sampleRate * channelCount * bytesPerSample)
                                Log.d("ImprovedPCM", "📈 已发送 ${seconds} 秒音频数据")
                            }

                            // 控制播放速度
                            delay(bufferDurationMs.toLong())

                        } else {
                            // 文件结束，检查是否需要循环播放
                            loopCount++
                            Log.d("ImprovedPCM", "🔁 文件播放完毕，开始第 ${loopCount + 1} 次循环")

                            // 重新开始播放
                            inputStream.channel.position(0)

                            // 可以选择停止循环播放
                            if (loopCount >= 3) {
                                Log.d("ImprovedPCM", "⏹️ 达到最大循环次数，停止播放")
                                isRunning = false
                            }
                        }
                    }
                }

                Log.d("ImprovedPCM", "✅ 音频发送完成")

            } catch (e: Exception) {
                Log.e("ImprovedPCM", "❌ 读取音频文件失败", e)
            }
        }

        Log.d("ImprovedPCM", "✅ 音频输入设置完成")
    }

    /**
     * 设置音频输出：录制远程音频到文件
     */
    private fun setupAudioOutput(outputFile: File) {
        // 确保输出目录存在
        outputFile.parentFile?.mkdirs()

        try {
            // 创建输出流
            outputStream = FileOutputStream(outputFile)
            Log.d("ImprovedPCM", "📁 输出文件已创建: ${outputFile.absolutePath}")

            // 监听所有远程参与者的音频
            room.remoteParticipants.values.forEach { participant ->
                setupParticipantAudioCapture(participant)
            }

        } catch (e: Exception) {
            Log.e("ImprovedPCM", "❌ 设置音频输出失败", e)
        }

        Log.d("ImprovedPCM", "✅ 音频输出设置完成")
    }

    private fun setupParticipantAudioCapture(participant: io.livekit.android.room.participant.RemoteParticipant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull()?.first?.track as? io.livekit.android.room.track.RemoteAudioTrack

        audioTrack?.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, _, audioData, _, _, _, _, _ ->
            // 将音频数据写入文件
            scope.launch {
                try {
                    val outputFile = File("")
                    val audioBytes = ByteArray(audioData.remaining())
                    audioData.duplicate().get(audioBytes)
                    outputStream?.write(audioBytes)

                    // 每10MB打印一次进度
                    if (audioBytes.size > 0 && (outputFile.length() % (10 * 1024 * 1024)) < audioBytes.size) {
                        Log.d("ImprovedPCM", "💾 录制进度: ${outputFile.length() / 1024}KB from ${participant.identity}")
                    }

                } catch (e: Exception) {
                    Log.e("ImprovedPCM", "❌ 写入音频文件失败", e)
                }
            }
        }

        Log.d("ImprovedPCM", "🎧 已设置 ${participant.identity} 的音频录制")
    }

    /**
     * 停止示例
     */
    fun stop() {
        Log.d("ImprovedPCM", "⏹️ 停止音频示例")
        isRunning = false

        try {
            outputStream?.close()
            outputStream = null
            Log.d("ImprovedPCM", "📁 输出文件已关闭")
        } catch (e: Exception) {
            Log.e("ImprovedPCM", "❌ 关闭输出流失败", e)
        }

        Log.d("ImprovedPCM", "✅ 示例已停止")
    }

    /**
     * 获取当前状态信息
     */
    fun getStatus(): String {
        val outputSize = outputStream?.let {
            try {
                val file = File(it.fd.toString())
                "${file.length() / 1024}KB"
            } catch (e: Exception) {
                "未知"
            }
        } ?: "未开始"

        return """
            |运行状态: ${if (isRunning) "🟢 运行中" else "🔴 已停止"}
            |远程参与者: ${room.remoteParticipants.size}
            |录制文件大小: $outputSize
        """.trimMargin()
    }
}
