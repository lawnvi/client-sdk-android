//package io.livekit.android.sample.basic
//
//import android.content.Context
//import android.media.AudioFormat
//import android.net.Uri
//import android.util.Log
//import io.livekit.android.audio.*
//import io.livekit.android.room.participant.LocalParticipant
//import io.livekit.android.room.participant.createAudioTrackWithBuffer
//import io.livekit.android.room.participant.createAudioTrackWithCustomSource
//import io.livekit.android.room.participant.createAudioTrackWithFile
//import io.livekit.android.room.track.LocalAudioTrackOptions
//import kotlinx.coroutines.*
//import java.io.File
//import java.nio.ByteBuffer
//import kotlin.math.*
//
///**
// * 自定义音频数据源测试示例
// *
// * 演示如何使用各种自定义音频数据源，包括：
// * 1. 缓冲区音频提供器
// * 2. 文件音频提供器
// * 3. 实时生成的音频数据
// * 4. 混合多种音频源
// */
//class CustomAudioSourceTestExample(
//    private val context: Context,
//    private val localParticipant: LocalParticipant
//) {
//    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//    private val logTag = "CustomAudioTest"
//
//    /**
//     * 示例1: 使用缓冲区提供音频数据
//     * 适用场景：动态生成音频或从多个源收集音频数据
//     */
//    fun testBufferAudioProvider() {
//        Log.d(logTag, "测试缓冲区音频提供器")
//
//        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
//            name = "buffer_audio_track",
//            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
//            channelCount = 2,
//            sampleRate = 44100,
//            microphoneGain = 0.0f,  // 完全禁用麦克风
//            customAudioGain = 1.0f,
//            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//        )
//
//        // 发布轨道
//        localParticipant.publishAudioTrack(audioTrack)
//
//        // 开始生成并推送音频数据
//        scope.launch {
//            generateSineWaveAudio(bufferProvider)
//        }
//    }
//
//    /**
//     * 示例2: 从文件读取音频数据
//     * 适用场景：播放预录制的音频文件
//     */
//    fun testFileAudioProvider(audioFile: File) {
//        Log.d(logTag, "测试文件音频提供器: ${audioFile.name}")
//
//        val audioTrack = localParticipant.createAudioTrackWithFile(
//            context = context,
//            name = "file_audio_track",
//            audioFileUri = Uri.fromFile(audioFile),
//            loop = true,  // 循环播放
//            microphoneGain = 0.0f,
//            customAudioGain = 0.8f,  // 稍微降低音量
//            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//        )
//
//        // 发布轨道
//        localParticipant.publishAudioTrack(audioTrack)
//
//        Log.d(logTag, "文件音频轨道已发布")
//    }
//
//    /**
//     * 示例3: 自定义音频生成器
//     * 适用场景：实时生成特定类型的音频信号
//     */
//    fun testCustomAudioGenerator() {
//        Log.d(logTag, "测试自定义音频生成器")
//
//        val customGenerator = object : CustomAudioBufferProvider {
//            private var isRunning = false
//            private var sampleIndex = 0L
//            private var frequency = 440.0 // A音
//
//            override fun start() {
//                isRunning = true
//                sampleIndex = 0
//                Log.d(logTag, "自定义音频生成器已启动")
//            }
//
//            override fun stop() {
//                isRunning = false
//                Log.d(logTag, "自定义音频生成器已停止")
//            }
//
//            override fun hasMoreData(): Boolean = isRunning
//
//            override fun provideAudioData(
//                requestedBytes: Int,
//                audioFormat: Int,
//                channelCount: Int,
//                sampleRate: Int
//            ): ByteBuffer? {
//                if (!isRunning) return null
//
//                val samplesPerChannel = requestedBytes / (channelCount * 2) // 16-bit = 2 bytes per sample
//                val audioData = ByteArray(requestedBytes)
//
//                for (i in 0 until samplesPerChannel) {
//                    // 生成复合波形：基频 + 泛音
//                    val time = sampleIndex.toDouble() / sampleRate
//                    val fundamental = sin(2.0 * PI * frequency * time)
//                    val harmonic2 = 0.3 * sin(2.0 * PI * frequency * 2 * time)
//                    val harmonic3 = 0.2 * sin(2.0 * PI * frequency * 3 * time)
//
//                    val sample = (fundamental + harmonic2 + harmonic3) * 0.3 // 限制音量
//                    val sampleInt = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
//
//                    // 立体声：左右声道相同
//                    for (channel in 0 until channelCount) {
//                        val byteIndex = (i * channelCount + channel) * 2
//                        if (byteIndex + 1 < audioData.size) {
//                            audioData[byteIndex] = (sampleInt and 0xFF).toByte()
//                            audioData[byteIndex + 1] = ((sampleInt shr 8) and 0xFF).toByte()
//                        }
//                    }
//
//                    sampleIndex++
//                }
//
//                return ByteBuffer.wrap(audioData)
//            }
//
//            // 动态改变频率
//            fun setFrequency(newFrequency: Double) {
//                frequency = newFrequency
//                Log.d(logTag, "音频频率改变为: ${frequency}Hz")
//            }
//        }
//
//        val audioTrack = localParticipant.createAudioTrackWithCustomSource(
//            name = "custom_generator_track",
//            customAudioProvider = customGenerator,
//            microphoneGain = 0.0f,
//            customAudioGain = 1.0f,
//            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//        )
//
//        // 发布轨道
//        localParticipant.publishAudioTrack(audioTrack)
//
//        // 演示动态频率变化
//        scope.launch {
//            val frequencies = listOf(440.0, 523.25, 659.25, 783.99) // A, C, E, G
//            frequencies.forEach { freq ->
//                customGenerator.setFrequency(freq)
//                delay(2000) // 每个音符播放2秒
//            }
//        }
//    }
//
//    /**
//     * 示例4: 混合多种音频源
//     * 适用场景：需要同时播放背景音乐和实时生成的音效
//     */
//    fun testMixedAudioSources(backgroundAudioFile: File) {
//        Log.d(logTag, "测试混合音频源")
//
//        // 创建背景音乐提供器
//        val fileProvider = FileAudioBufferProvider(context, Uri.fromFile(backgroundAudioFile), loop = true)
//
//        // 创建音效生成器
//        val effectGenerator = createBeepEffectGenerator()
//
//        // 创建混合提供器
//        val mixedProvider = object : CustomAudioBufferProvider {
//            private var isRunning = false
//
//            override fun start() {
//                isRunning = true
//                fileProvider.start()
//                effectGenerator.start()
//            }
//
//            override fun stop() {
//                isRunning = false
//                fileProvider.stop()
//                effectGenerator.stop()
//            }
//
//            override fun hasMoreData(): Boolean = isRunning && (fileProvider.hasMoreData() || effectGenerator.hasMoreData())
//
//            override fun provideAudioData(
//                requestedBytes: Int,
//                audioFormat: Int,
//                channelCount: Int,
//                sampleRate: Int
//            ): ByteBuffer? {
//                if (!isRunning) return null
//
//                // 获取背景音乐数据
//                val bgData = fileProvider.provideAudioData(requestedBytes, audioFormat, channelCount, sampleRate)
//                val effectData = effectGenerator.provideAudioData(requestedBytes, audioFormat, channelCount, sampleRate)
//
//                return if (bgData != null && effectData != null) {
//                    // 混合两个音频源
//                    mixAudioBuffers(bgData, effectData, 0.7f, 0.3f) // 背景70%，音效30%
//                } else {
//                    bgData ?: effectData
//                }
//            }
//        }
//
//        val audioTrack = localParticipant.createAudioTrackWithCustomSource(
//            name = "mixed_audio_track",
//            customAudioProvider = mixedProvider,
//            microphoneGain = 0.0f,
//            customAudioGain = 1.0f,
//            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//        )
//
//        // 发布轨道
//        localParticipant.publishAudioTrack(audioTrack)
//    }
//
//    /**
//     * 示例5: 动态音频数据推送
//     * 适用场景：从外部API或传感器获取音频数据
//     */
//    fun testDynamicAudioPushing() {
//        Log.d(logTag, "测试动态音频数据推送")
//
//        val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
//            name = "dynamic_audio_track",
//            audioFormat = AudioFormat.ENCODING_PCM_16BIT,
//            channelCount = 1, // 单声道
//            sampleRate = 16000, // 较低采样率，适合语音
//            microphoneGain = 0.0f,
//            customAudioGain = 1.0f,
//            mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//        )
//
//        // 发布轨道
//        localParticipant.publishAudioTrack(audioTrack)
//
//        // 模拟外部数据源
//        scope.launch {
//            simulateExternalAudioSource(bufferProvider)
//        }
//    }
//
//    /**
//     * 示例6: 音频质量和格式测试
//     * 适用场景：测试不同音频格式和质量设置
//     */
//    fun testAudioQualityVariations() {
//        Log.d(logTag, "测试不同音频质量配置")
//
//        val configurations = listOf(
//            Triple(8000, 1, "电话质量"),
//            Triple(16000, 1, "语音质量"),
//            Triple(44100, 2, "CD质量"),
//            Triple(48000, 2, "专业质量")
//        )
//
//        configurations.forEachIndexed { index, (sampleRate, channelCount, description) ->
//            scope.launch {
//                delay(index * 5000L) // 每5秒切换一种质量
//
//                Log.d(logTag, "切换到$description: ${sampleRate}Hz, ${channelCount}声道")
//
//                val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
//                    name = "quality_test_$index",
//                    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
//                    channelCount = channelCount,
//                    sampleRate = sampleRate,
//                    microphoneGain = 0.0f,
//                    customAudioGain = 1.0f,
//                    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
//                )
//
//                localParticipant.publishAudioTrack(audioTrack)
//
//                // 生成对应格式的测试音频
//                generateTestToneForQuality(bufferProvider, sampleRate, channelCount)
//            }
//        }
//    }
//
//    // 辅助方法：生成正弦波音频
//    private suspend fun generateSineWaveAudio(bufferProvider: BufferAudioBufferProvider) {
//        val sampleRate = 44100
//        val frequency = 440.0
//        val amplitude = 0.3f
//        val bufferDurationMs = 20
//        val samplesPerBuffer = (sampleRate * bufferDurationMs) / 1000
//        val bytesPerBuffer = samplesPerBuffer * 2 * 2 // 16-bit stereo
//
//        var sampleIndex = 0L
//
//        while (scope.isActive) {
//            val audioData = ByteArray(bytesPerBuffer)
//
//            for (i in 0 until samplesPerBuffer) {
//                val time = sampleIndex.toDouble() / sampleRate
//                val sample = sin(2.0 * PI * frequency * time) * amplitude
//                val sampleInt = (sample * Short.MAX_VALUE).toInt()
//
//                // 立体声：左右声道相同
//                val leftIndex = i * 4
//                val rightIndex = i * 4 + 2
//
//                if (leftIndex + 1 < audioData.size && rightIndex + 1 < audioData.size) {
//                    audioData[leftIndex] = (sampleInt and 0xFF).toByte()
//                    audioData[leftIndex + 1] = ((sampleInt shr 8) and 0xFF).toByte()
//                    audioData[rightIndex] = (sampleInt and 0xFF).toByte()
//                    audioData[rightIndex + 1] = ((sampleInt shr 8) and 0xFF).toByte()
//                }
//
//                sampleIndex++
//            }
//
//            bufferProvider.addAudioData(audioData)
//            delay(bufferDurationMs.toLong())
//        }
//    }
//
//    // 辅助方法：创建哔哔声效果生成器
//    private fun createBeepEffectGenerator(): CustomAudioBufferProvider {
//        return object : CustomAudioBufferProvider {
//            private var isRunning = false
//            private var beepTimer = 0L
//            private val beepInterval = 2000L // 每2秒一次哔声
//            private val beepDuration = 200L // 哔声持续200ms
//
//            override fun start() {
//                isRunning = true
//                beepTimer = System.currentTimeMillis()
//            }
//
//            override fun stop() {
//                isRunning = false
//            }
//
//            override fun hasMoreData(): Boolean = isRunning
//
//            override fun provideAudioData(
//                requestedBytes: Int,
//                audioFormat: Int,
//                channelCount: Int,
//                sampleRate: Int
//            ): ByteBuffer? {
//                if (!isRunning) return null
//
//                val currentTime = System.currentTimeMillis()
//                val timeSinceLastBeep = currentTime - beepTimer
//
//                val shouldBeep = (timeSinceLastBeep % beepInterval) < beepDuration
//
//                val audioData = ByteArray(requestedBytes)
//
//                if (shouldBeep) {
//                    // 生成1000Hz哔声
//                    val samplesPerChannel = requestedBytes / (channelCount * 2)
//                    for (i in 0 until samplesPerChannel) {
//                        val time = i.toDouble() / sampleRate
//                        val sample = sin(2.0 * PI * 1000.0 * time) * 0.5
//                        val sampleInt = (sample * Short.MAX_VALUE).toInt()
//
//                        for (channel in 0 until channelCount) {
//                            val byteIndex = (i * channelCount + channel) * 2
//                            if (byteIndex + 1 < audioData.size) {
//                                audioData[byteIndex] = (sampleInt and 0xFF).toByte()
//                                audioData[byteIndex + 1] = ((sampleInt shr 8) and 0xFF).toByte()
//                            }
//                        }
//                    }
//                }
//                // 如果不应该哔声，audioData保持为0（静音）
//
//                return ByteBuffer.wrap(audioData)
//            }
//        }
//    }
//
//    // 辅助方法：混合两个音频缓冲区
//    private fun mixAudioBuffers(buffer1: ByteBuffer, buffer2: ByteBuffer, gain1: Float, gain2: Float): ByteBuffer {
//        val size = minOf(buffer1.remaining(), buffer2.remaining())
//        val mixedData = ByteArray(size)
//
//        val data1 = ByteArray(buffer1.remaining())
//        val data2 = ByteArray(buffer2.remaining())
//        buffer1.duplicate().get(data1)
//        buffer2.duplicate().get(data2)
//
//        for (i in 0 until size step 2) {
//            if (i + 1 < size) {
//                val sample1 = ((data1[i].toInt() and 0xFF) or ((data1[i + 1].toInt() and 0xFF) shl 8)).toShort()
//                val sample2 = ((data2[i].toInt() and 0xFF) or ((data2[i + 1].toInt() and 0xFF) shl 8)).toShort()
//
//                val mixedSample = ((sample1 * gain1) + (sample2 * gain2)).toInt()
//                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
//
//                mixedData[i] = (mixedSample and 0xFF).toByte()
//                mixedData[i + 1] = ((mixedSample shr 8) and 0xFF).toByte()
//            }
//        }
//
//        return ByteBuffer.wrap(mixedData)
//    }
//
//    // 辅助方法：模拟外部音频源
//    private suspend fun simulateExternalAudioSource(bufferProvider: BufferAudioBufferProvider) {
//        var dataPacketCount = 0
//
//        while (scope.isActive) {
//            // 模拟接收到的音频数据包
//            val packetSize = 320 // 20ms @ 16kHz mono
//            val audioPacket = generateWhiteNoise(packetSize)
//
//            bufferProvider.addAudioData(audioPacket)
//            dataPacketCount++
//
//            if (dataPacketCount % 50 == 0) {
//                Log.d(logTag, "已推送 $dataPacketCount 个音频数据包")
//            }
//
//            delay(20) // 20ms间隔
//        }
//    }
//
//    // 辅助方法：生成白噪声
//    private fun generateWhiteNoise(sizeInBytes: Int): ByteArray {
//        val data = ByteArray(sizeInBytes)
//        for (i in data.indices step 2) {
//            val sample = ((Math.random() - 0.5) * 2 * Short.MAX_VALUE * 0.1).toInt() // 低音量白噪声
//            data[i] = (sample and 0xFF).toByte()
//            if (i + 1 < data.size) {
//                data[i + 1] = ((sample shr 8) and 0xFF).toByte()
//            }
//        }
//        return data
//    }
//
//    // 辅助方法：为特定质量生成测试音调
//    private suspend fun generateTestToneForQuality(
//        bufferProvider: BufferAudioBufferProvider,
//        sampleRate: Int,
//        channelCount: Int
//    ) {
//        val frequency = when (sampleRate) {
//            8000 -> 400.0   // 低频测试音
//            16000 -> 800.0  // 中频测试音
//            44100 -> 1000.0 // 标准测试音
//            48000 -> 1200.0 // 高频测试音
//            else -> 440.0
//        }
//
//        val bufferDurationMs = 20
//        val samplesPerBuffer = (sampleRate * bufferDurationMs) / 1000
//        val bytesPerBuffer = samplesPerBuffer * channelCount * 2
//
//        var sampleIndex = 0L
//        var duration = 0
//
//        while (scope.isActive && duration < 3000) { // 播放3秒
//            val audioData = ByteArray(bytesPerBuffer)
//
//            for (i in 0 until samplesPerBuffer) {
//                val time = sampleIndex.toDouble() / sampleRate
//                val sample = sin(2.0 * PI * frequency * time) * 0.3
//                val sampleInt = (sample * Short.MAX_VALUE).toInt()
//
//                for (channel in 0 until channelCount) {
//                    val byteIndex = (i * channelCount + channel) * 2
//                    if (byteIndex + 1 < audioData.size) {
//                        audioData[byteIndex] = (sampleInt and 0xFF).toByte()
//                        audioData[byteIndex + 1] = ((sampleInt shr 8) and 0xFF).toByte()
//                    }
//                }
//
//                sampleIndex++
//            }
//
//            bufferProvider.addAudioData(audioData)
//            delay(bufferDurationMs.toLong())
//            duration += bufferDurationMs
//        }
//    }
//}
