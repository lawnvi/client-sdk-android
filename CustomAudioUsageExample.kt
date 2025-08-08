/*
 * 增强版自定义音频输入使用示例
 * 
 * 展示如何使用修复后的CustomAudioMixer实现可靠的自定义音频输入功能
 */

import android.content.Context
import android.media.AudioFormat
import io.livekit.android.*
import io.livekit.android.audio.*
import io.livekit.android.room.*
import io.livekit.android.room.participant.*
import io.livekit.android.util.LKLog
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * 自定义音频使用示例类
 */
class CustomAudioUsageExample(private val context: Context) {
    
    private var room: Room? = null
    private var customAudioTrackManager = CustomAudioTrackManager()
    
    /**
     * 示例1: 创建简单的正弦波测试音频
     */
    suspend fun example1_CreateSineWaveAudio() {
        LKLog.d { "📱 示例1: 创建正弦波测试音频" }
        
        try {
            // 1. 连接到房间
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 2. 创建正弦波测试轨道
            val testTrack = localParticipant.createSineWaveTestTrack(
                frequency = 440.0, // A4音符
                amplitude = 0.5     // 中等音量
            )
            
            // 3. 发布音频轨道
            localParticipant.publishAudioTrack(testTrack.audioTrack)
            
            // 4. 管理轨道
            customAudioTrackManager.addTrack("sine-wave", testTrack)
            
            LKLog.i { "✅ 正弦波音频轨道创建并发布成功" }
            LKLog.d { testTrack.getStatusInfo() }
            
            // 5. 运行30秒后停止
            delay(30_000)
            customAudioTrackManager.stopTrack("sine-wave")
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例1执行失败" }
        }
    }
    
    /**
     * 示例2: 创建哔哔声序列音频
     */
    suspend fun example2_CreateBeepSequenceAudio() {
        LKLog.d { "📱 示例2: 创建哔哔声序列音频" }
        
        try {
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 创建哔哔声测试轨道
            val beepTrack = localParticipant.createBeepTestTrack(
                frequency = 800.0, // 较高的频率
                amplitude = 0.3     // 较低的音量
            )
            
            localParticipant.publishAudioTrack(beepTrack.audioTrack)
            customAudioTrackManager.addTrack("beep-sequence", beepTrack)
            
            LKLog.i { "✅ 哔哔声音频轨道创建成功" }
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例2执行失败" }
        }
    }
    
    /**
     * 示例3: 使用自定义音频提供器
     */
    suspend fun example3_CustomAudioProvider() {
        LKLog.d { "📱 示例3: 使用自定义音频提供器" }
        
        try {
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 创建自定义音频提供器
            val customProvider = object : CustomAudioBufferProvider {
                private var isRunning = false
                private var sampleIndex = 0.0
                
                override fun start() {
                    isRunning = true
                    LKLog.d { "自定义音频提供器启动" }
                }
                
                override fun stop() {
                    isRunning = false
                    LKLog.d { "自定义音频提供器停止" }
                }
                
                override fun hasMoreData(): Boolean = isRunning
                
                override fun getCaptureTimeNs(): Long = System.nanoTime()
                
                override fun provideAudioData(
                    requestedBytes: Int,
                    audioFormat: Int,
                    channelCount: Int,
                    sampleRate: Int
                ): ByteBuffer? {
                    if (!isRunning) return null
                    
                    // 生成混合音频：正弦波 + 低频调制
                    val samplesPerChannel = requestedBytes / (2 * channelCount) // 16位PCM
                    val buffer = ByteBuffer.allocate(requestedBytes)
                    
                    repeat(samplesPerChannel) {
                        // 主频率440Hz + 低频调制5Hz
                        val mainWave = kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * sampleIndex / sampleRate)
                        val modulation = kotlin.math.sin(2.0 * kotlin.math.PI * 5.0 * sampleIndex / sampleRate) * 0.3
                        val sample = (mainWave * (0.7 + modulation) * 0.5 * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        
                        repeat(channelCount) {
                            buffer.putShort(sample.toShort())
                        }
                        
                        sampleIndex++
                    }
                    
                    buffer.flip()
                    return buffer
                }
            }
            
            // 使用自定义提供器创建轨道
            val (audioTrack, mixer, monitor) = localParticipant.createEnhancedCustomAudioTrack(
                name = "custom-modulated-audio",
                customAudioProvider = customProvider,
                enableDebug = true
            )
            
            localParticipant.publishAudioTrack(audioTrack)
            
            LKLog.i { "✅ 自定义音频提供器轨道创建成功" }
            
            // 保存轨道引用
            val trackResult = TestAudioTrackResult(
                audioTrack = audioTrack,
                testProvider = TestAudioProvider(), // 占位符
                mixer = mixer,
                monitor = monitor
            )
            customAudioTrackManager.addTrack("custom-provider", trackResult)
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例3执行失败" }
        }
    }
    
    /**
     * 示例4: 切换不同的音频模式
     */
    suspend fun example4_SwitchAudioModes() {
        LKLog.d { "📱 示例4: 切换不同音频模式" }
        
        try {
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 模式1: 正弦波
            LKLog.d { "🎵 切换到正弦波模式" }
            val sineTrack = localParticipant.createSineWaveTestTrack()
            localParticipant.publishAudioTrack(sineTrack.audioTrack)
            customAudioTrackManager.addTrack("current", sineTrack)
            delay(10_000)
            
            // 模式2: 哔哔声
            LKLog.d { "🔔 切换到哔哔声模式" }
            val beepTrack = localParticipant.createBeepTestTrack()
            localParticipant.unpublishTrack(sineTrack.audioTrack)
            localParticipant.publishAudioTrack(beepTrack.audioTrack)
            customAudioTrackManager.addTrack("current", beepTrack)
            delay(10_000)
            
            // 模式3: 白噪声
            LKLog.d { "🌪️ 切换到白噪声模式" }
            val noiseTrack = localParticipant.createWhiteNoiseTestTrack()
            localParticipant.unpublishTrack(beepTrack.audioTrack)
            localParticipant.publishAudioTrack(noiseTrack.audioTrack)
            customAudioTrackManager.addTrack("current", noiseTrack)
            delay(10_000)
            
            // 停止所有音频
            LKLog.d { "🔇 停止所有自定义音频" }
            customAudioTrackManager.stopAllTracks()
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例4执行失败" }
        }
    }
    
    /**
     * 示例5: 监控和调试自定义音频
     */
    suspend fun example5_MonitorAndDebug() {
        LKLog.d { "📱 示例5: 监控和调试自定义音频" }
        
        try {
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 创建带调试的音频轨道
            val testTrack = localParticipant.createTestAudioTrack(
                name = "debug-test",
                testSignalType = TestAudioProvider.TestSignalType.SINE_WAVE,
                frequency = 440.0,
                amplitude = 0.5,
                durationSeconds = 0.0, // 无限时长
                enableDebug = true
            )
            
            localParticipant.publishAudioTrack(testTrack.audioTrack)
            customAudioTrackManager.addTrack("debug", testTrack)
            
            // 定期打印状态信息
            repeat(12) { // 2分钟，每10秒一次
                delay(10_000)
                
                LKLog.i { "📊 状态报告 #${it + 1}:" }
                LKLog.i { testTrack.getStatusInfo() }
                LKLog.i { customAudioTrackManager.getAllTracksStatus() }
                LKLog.i { CustomAudioDebugUtils.checkSystemAudioInfo() }
            }
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例5执行失败" }
        }
    }
    
    /**
     * 示例6: 混合麦克风和自定义音频
     */
    suspend fun example6_MixMicrophoneAndCustomAudio() {
        LKLog.d { "📱 示例6: 混合麦克风和自定义音频" }
        
        try {
            val room = connectToRoom()
            val localParticipant = room.localParticipant
            
            // 创建测试音频提供器
            val testProvider = TestAudioProviderFactory.createSineWaveProvider(
                frequency = 220.0, // 低音A
                amplitude = 0.3
            )
            
            // 创建加法混音模式的音频轨道
            val (audioTrack, mixer, monitor) = localParticipant.createEnhancedCustomAudioTrack(
                name = "mixed-audio",
                customAudioProvider = testProvider,
                microphoneGain = 0.7f,  // 降低麦克风音量
                customAudioGain = 0.5f, // 中等自定义音频音量
                mixMode = CustomAudioMixer.MixMode.ADDITIVE, // 加法混音
                enableDebug = true
            )
            
            localParticipant.publishAudioTrack(audioTrack)
            
            LKLog.i { "✅ 混合音频轨道创建成功 - 麦克风 + 自定义音频" }
            LKLog.d { mixer.getStatusInfo() }
            
            // 保存轨道
            val trackResult = TestAudioTrackResult(audioTrack, testProvider, mixer, monitor)
            customAudioTrackManager.addTrack("mixed", trackResult)
            
        } catch (e: Exception) {
            LKLog.e(e) { "❌ 示例6执行失败" }
        }
    }
    
    /**
     * 连接到LiveKit房间
     */
    private suspend fun connectToRoom(): Room {
        if (room?.state == ConnectionState.CONNECTED) {
            return room!!
        }
        
        // 这里需要你的实际LiveKit服务器信息
        val url = "wss://your-livekit-server.com"
        val token = "your-access-token"
        
        val newRoom = LiveKit.create(context).connect(url, token)
        room = newRoom
        
        LKLog.i { "🌐 已连接到LiveKit房间" }
        return newRoom
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        customAudioTrackManager.stopAllTracks()
        room?.disconnect()
        LKLog.d { "🧹 资源清理完成" }
    }
}

/**
 * 使用示例的快速启动函数
 */
suspend fun runCustomAudioExamples(context: Context) {
    val example = CustomAudioUsageExample(context)
    
    try {
        // 运行示例1: 正弦波音频
        example.example1_CreateSineWaveAudio()
        
        // 运行示例2: 哔哔声音频
        example.example2_CreateBeepSequenceAudio()
        
        // 运行示例3: 自定义提供器
        example.example3_CustomAudioProvider()
        
        // 根据需要运行其他示例...
        
    } finally {
        example.cleanup()
    }
}

/**
 * 简化的快速测试函数
 */
suspend fun quickCustomAudioTest(context: Context) {
    val example = CustomAudioUsageExample(context)
    
    try {
        // 创建和发布测试音频
        LKLog.i { "🚀 开始快速自定义音频测试" }
        
        val room = LiveKit.create(context).connect("wss://your-server.com", "your-token")
        val localParticipant = room.localParticipant
        
        // 创建440Hz正弦波测试
        val testTrack = localParticipant.createSineWaveTestTrack(
            frequency = 440.0,
            amplitude = 0.5
        )
        
        localParticipant.publishAudioTrack(testTrack.audioTrack)
        
        LKLog.i { "✅ 自定义音频测试启动成功" }
        LKLog.i { testTrack.getStatusInfo() }
        
        // 运行测试
        delay(60_000) // 运行1分钟
        
        testTrack.stop()
        LKLog.i { "🏁 自定义音频测试完成" }
        
    } catch (e: Exception) {
        LKLog.e(e) { "❌ 快速测试失败" }
    } finally {
        example.cleanup()
    }
}