package io.livekit.android.sample.basic

import android.content.Context
import android.util.Log
import io.livekit.android.audio.*
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.setAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import io.livekit.android.room.track.muteWithProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 远程音频拦截器测试示例
 *
 * 演示如何使用各种音频拦截器来处理服务端发来的音频数据，
 * 并提供可选的扬声器播放控制功能。
 */
class RemoteAudioInterceptorTestExample(
    private val context: Context
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val logTag = "RemoteAudioTest"

    /**
     * 示例1: 基础音频数据处理（不影响播放）
     * 适用场景：需要分析音频数据但保持正常播放
     */
    fun setupBasicAudioProcessing(participant: Participant, audioTrack: RemoteAudioTrack) {
        Log.d(logTag, "设置基础音频处理 for ${participant.identity}")

        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            // 这里可以进行各种音频分析
            val volume = calculateRMSVolume(audioData)
            val duration = (numberOfFrames * 1000) / sampleRate

            Log.d(logTag, "音频数据 from ${participant.identity}: " +
                    "volume=$volume, duration=${duration}ms, " +
                    "sampleRate=$sampleRate Hz, channels=$numberOfChannels")

            // 可以在这里添加：
            // - 音频级别检测
            // - 语音活动检测
            // - 音频质量分析
            // - 发送到云端分析
        }
    }

    /**
     * 示例2: 音频录制（保持正常播放）
     * 适用场景：需要录制远程音频用于回放或分析
     */
    fun setupAudioRecording(participant: Participant, audioTrack: RemoteAudioTrack, outputFile: File) {
        Log.d(logTag, "设置音频录制 for ${participant.identity}")

        var recordingStream: FileOutputStream? = null

        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            scope.launch {
                try {
                    if (recordingStream == null) {
                        recordingStream = FileOutputStream(outputFile)
                        Log.d(logTag, "开始录制音频到: ${outputFile.absolutePath}")
                    }

                    // 复制音频数据并写入文件
                    val audioBytes = ByteArray(audioData.remaining())
                    audioData.duplicate().get(audioBytes)
                    recordingStream?.write(audioBytes)

                } catch (e: Exception) {
                    Log.e(logTag, "录制音频失败", e)
                }
            }
        }
    }

    /**
     * 示例3: 静音特定参与者（但仍处理其音频数据）
     * 适用场景：需要临时静音某个参与者但继续分析其音频
     */
    fun setupSelectiveMuting(participant: Participant, audioTrack: RemoteAudioTrack) {
        Log.d(logTag, "设置选择性静音 for ${participant.identity}")

        audioTrack.muteWithProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            // 即使音频被静音，仍然可以处理数据
            val volume = calculateRMSVolume(audioData)
            Log.d(logTag, "处理静音音频 from ${participant.identity}: volume=$volume")

            // 可以在这里做一些处理，比如：
            // - 检测是否有重要内容需要取消静音
            // - 记录静音期间的音频活动
            // - 保存静音的音频用于后续回放
        }
    }

    /**
     * 示例4: 完全控制音频播放（带自定义播放控制器）
     * 适用场景：需要完全控制音频播放，比如音量调节、特效处理等
     */
    fun setupCustomPlaybackControl(participant: Participant, audioTrack: RemoteAudioTrack) {
        Log.d(logTag, "设置自定义播放控制 for ${participant.identity}")

        val playbackInterceptor = object : PlaybackControlledInterceptor(
            context = context,
            enablePlayback = true  // 启用自定义播放控制
        ) {
            override fun processAndControlPlayback(
                participant: Participant,
                trackSid: String,
                audioData: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                timestamp: Long,
                playbackController: AudioPlaybackController?
            ): Boolean {

                // 处理音频数据
                val volume = calculateRMSVolume(audioData)

                // 根据音量或其他条件决定是否播放
                val shouldPlay = volume > 0.1f  // 只播放音量足够的音频

                if (shouldPlay && playbackController != null) {
                    // 可以在这里对音频进行处理，比如音量调节
                    val processedAudio = applyVolumeGain(audioData, 0.8f)  // 降低音量到80%

                    // 通过自定义播放控制器播放
                    if (!playbackController.isReady()) {
                        playbackController.initialize()
                    }
                    playbackController.playAudioData(processedAudio)

                    Log.d(logTag, "自定义播放音频 from ${participant.identity}: volume=$volume")
                } else {
                    Log.d(logTag, "跳过低音量音频 from ${participant.identity}: volume=$volume")
                }

                // 返回false表示我们已经处理了播放，系统不需要再播放
                return false
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            interceptor = playbackInterceptor
        )
    }

    /**
     * 示例5: 高级音频拦截器（条件性播放控制）
     * 适用场景：根据复杂条件决定音频处理方式
     */
    fun setupAdvancedAudioInterceptor(participant: Participant, audioTrack: RemoteAudioTrack) {
        Log.d(logTag, "设置高级音频拦截器 for ${participant.identity}")

        val advancedInterceptor = object : RemoteAudioDataInterceptor {
            private var consecutiveLowVolumeCount = 0
            private var audioPlaybackController: AudioPlaybackController? = null

            override fun onRemoteAudioData(
                participant: Participant,
                trackSid: String,
                audioData: ByteBuffer,
                bitsPerSample: Int,
                sampleRate: Int,
                numberOfChannels: Int,
                numberOfFrames: Int,
                timestamp: Long
            ): RemoteAudioDataInterceptor.InterceptorResult {

                val volume = calculateRMSVolume(audioData)

                // 检测连续低音量
                if (volume < 0.05f) {
                    consecutiveLowVolumeCount++
                } else {
                    consecutiveLowVolumeCount = 0
                }

                return when {
                    // 情况1: 连续低音量超过阈值 -> 静音
                    consecutiveLowVolumeCount > 50 -> {
                        Log.d(logTag, "检测到连续低音量，自动静音 ${participant.identity}")
                        RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = false)
                    }

                    // 情况2: 高音量 -> 使用自定义播放控制
                    volume > 0.5f -> {
                        Log.d(logTag, "检测到高音量，使用自定义播放控制 ${participant.identity}")

                        if (audioPlaybackController == null) {
                            audioPlaybackController = AudioPlaybackController(context)
                            audioPlaybackController?.initialize()
                        }

                        // 降低音量防止过响
                        val adjustedAudio = applyVolumeGain(audioData, 0.6f)
                        audioPlaybackController?.playAudioData(adjustedAudio)

                        // 返回false表示我们已经处理了播放
                        RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = false)
                    }

                    // 情况3: 正常音量 -> 允许系统播放
                    else -> {
                        RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = true)
                    }
                }
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            interceptor = advancedInterceptor
        )
    }

    /**
     * 示例6: 多参与者音频混合器
     * 适用场景：需要对多个参与者的音频进行混合处理
     */
//    fun setupMultiParticipantMixer(participants: List<Participant>) {
//        Log.d(logTag, "设置多参与者音频混合器")
//
//        val mixerController = AudioPlaybackController(context)
//        mixerController.initialize()
//
//        val audioBufferMap = mutableMapOf<String, ByteBuffer>()
//
//        participants.forEach { participant ->
//            val audioTrack = participant.audioTrackPublications.values
//                .firstOrNull { it.track is RemoteAudioTrack }?.track as? RemoteAudioTrack
//
//            audioTrack?.let { track ->
//                track.setAudioDataInterceptor(
//                    participant = participant,
//                    trackSid = track.sid.toString(),
//                    interceptor = object : RemoteAudioDataInterceptor {
//                        override fun onRemoteAudioData(
//                            participant: Participant,
//                            trackSid: String,
//                            audioData: ByteBuffer,
//                            bitsPerSample: Int,
//                            sampleRate: Int,
//                            numberOfChannels: Int,
//                            numberOfFrames: Int,
//                            timestamp: Long
//                        ): RemoteAudioDataInterceptor.InterceptorResult {
//
//                            // 收集每个参与者的音频数据
//                            audioBufferMap[participant.identity] = audioData.duplicate()
//
//                            // 如果收集到所有参与者的音频，进行混合
//                            if (audioBufferMap.size == participants.size) {
//                                val mixedAudio = mixAudioBuffers(audioBufferMap.values.toList())
//                                mixerController.playAudioData(mixedAudio)
//                                audioBufferMap.clear()
//                            }
//
//                            // 阻止个别参与者的音频直接播放
//                            return RemoteAudioDataInterceptor.InterceptorResult(allowPlayback = false)
//                        }
//                    }
//                )
//            }
//        }
//    }

    // 辅助方法：计算RMS音量
    private fun calculateRMSVolume(audioData: ByteBuffer): Float {
        val data = ByteArray(audioData.remaining())
        audioData.duplicate().get(data)

        var sum = 0L
        for (i in data.indices step 2) {
            if (i + 1 < data.size) {
                val sample = ((data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)).toShort()
                sum += sample * sample
            }
        }

        val mean = sum.toDouble() / (data.size / 2)
        return (kotlin.math.sqrt(mean) / Short.MAX_VALUE).toFloat()
    }

    // 辅助方法：应用音量增益
    private fun applyVolumeGain(audioData: ByteBuffer, gain: Float): ByteBuffer {
        val originalData = ByteArray(audioData.remaining())
        audioData.duplicate().get(originalData)

        val processedData = ByteArray(originalData.size)
        for (i in originalData.indices step 2) {
            if (i + 1 < originalData.size) {
                val sample = ((originalData[i].toInt() and 0xFF) or ((originalData[i + 1].toInt() and 0xFF) shl 8)).toShort()
                val adjustedSample = (sample * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())

                processedData[i] = (adjustedSample and 0xFF).toByte()
                processedData[i + 1] = ((adjustedSample shr 8) and 0xFF).toByte()
            }
        }

        return ByteBuffer.wrap(processedData)
    }

    // 辅助方法：混合多个音频缓冲区
    private fun mixAudioBuffers(buffers: List<ByteBuffer>): ByteBuffer {
        if (buffers.isEmpty()) return ByteBuffer.allocate(0)

        val maxSize = buffers.maxOf { it.remaining() }
        val mixedData = ByteArray(maxSize)

        for (i in 0 until maxSize step 2) {
            var mixedSample = 0
            var activeBuffers = 0

            buffers.forEach { buffer ->
                if (i + 1 < buffer.remaining()) {
                    val data = ByteArray(buffer.remaining())
                    buffer.duplicate().get(data)

                    val sample = ((data[i].toInt() and 0xFF) or ((data[i + 1].toInt() and 0xFF) shl 8)).toShort()
                    mixedSample += sample.toInt()
                    activeBuffers++
                }
            }

            if (activeBuffers > 0) {
                // 平均混合避免失真
                val averagedSample = (mixedSample / activeBuffers).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                mixedData[i] = (averagedSample and 0xFF).toByte()
                if (i + 1 < maxSize) {
                    mixedData[i + 1] = ((averagedSample shr 8) and 0xFF).toByte()
                }
            }
        }

        return ByteBuffer.wrap(mixedData)
    }
}
