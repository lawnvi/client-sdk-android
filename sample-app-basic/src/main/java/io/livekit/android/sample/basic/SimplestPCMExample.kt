package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
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
 * 最简单的PCM音频输入示例
 */
class SimplestPCMExample(
    private val context: Context,
    private val room: Room
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    suspend fun start(pcmFile: File) {
        if (!pcmFile.exists()) return
        
        val localParticipant = room.localParticipant

        // 创建音频轨道
        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
            channelCount = 2,
            sampleRate = 44100,
            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
        )

        // 发布音频轨道
        localParticipant.publishAudioTrack(audioTrack)

        // 读取并发送PCM数据
        scope.launch {
            isRunning = true
            FileInputStream(pcmFile).use { inputStream ->
                // 使用更小的缓冲区避免溢出 (约10ms的音频数据)
                val bufferSize = 44100 * 2 * 2 * 10 / 1000  // sampleRate * channels * bytesPerSample * ms / 1000
                val buffer = ByteArray(bufferSize)
                
                while (isRunning && isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead > 0) {
                        bufferProvider.addAudioData(buffer.copyOf(bytesRead))
                        delay(10) // 10ms延迟匹配缓冲区大小
                    } else {
                        inputStream.channel.position(0) // 循环播放
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
    }
}