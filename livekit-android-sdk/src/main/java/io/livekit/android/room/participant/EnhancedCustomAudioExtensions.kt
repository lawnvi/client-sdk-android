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

package io.livekit.android.room.participant

import android.media.AudioFormat
import io.livekit.android.audio.*
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.util.LKLog

/**
 * 增强版自定义音频扩展函数
 * 
 * 提供更可靠和易用的自定义音频输入功能。
 */

/**
 * 创建带增强版混音器的自定义音频轨道
 * 
 * @param name 轨道名称
 * @param customAudioProvider 自定义音频提供器
 * @param options 音频轨道选项
 * @param microphoneGain 麦克风增益 (0.0 = 静音, 1.0 = 正常音量)
 * @param customAudioGain 自定义音频增益
 * @param mixMode 混音模式
 * @param enableDebug 是否启用调试日志
 * @return Triple包含: LocalAudioTrack, EnhancedCustomAudioMixer, 调试监控器
 */
fun LocalParticipant.createEnhancedCustomAudioTrack(
    name: String = "",
    customAudioProvider: CustomAudioBufferProvider,
    options: LocalAudioTrackOptions = LocalAudioTrackOptions(),
    microphoneGain: Float = 0.0f,  // 默认静音麦克风
    customAudioGain: Float = 1.0f,
    mixMode: CustomAudioMixer.MixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY,
    enableDebug: Boolean = true
): Triple<LocalAudioTrack, EnhancedCustomAudioMixer, CustomAudioDebugUtils.CustomAudioProviderMonitor?> {
    
    LKLog.d { "创建增强版自定义音频轨道 - 名称: $name, 模式: $mixMode, 调试: $enableDebug" }
    
    // 创建音频轨道
    val audioTrack = createAudioTrack(name, options)
    
    // 创建增强版混音器
    val enhancedMixer = EnhancedCustomAudioMixer(
        customAudioProvider = customAudioProvider,
        microphoneGain = microphoneGain,
        customAudioGain = customAudioGain,
        mixMode = mixMode
    )
    
    // 设置混音器到音频轨道
    audioTrack.setAudioBufferCallback(enhancedMixer)
    
    // 启动混音器
    val startSuccess = enhancedMixer.start()
    if (!startSuccess) {
        LKLog.e { "启动增强版混音器失败" }
        throw IllegalStateException("无法启动自定义音频混音器")
    }
    
    // 创建调试监控器（如果启用）
    val monitor = if (enableDebug) {
        CustomAudioDebugUtils.CustomAudioProviderMonitor(customAudioProvider).apply {
            startMonitoring()
        }
    } else {
        null
    }
    
    LKLog.i { "✅ 增强版自定义音频轨道创建成功" }
    LKLog.d { enhancedMixer.getStatusInfo() }
    
    return Triple(audioTrack, enhancedMixer, monitor)
}

/**
 * 创建测试音频轨道 - 用于验证自定义音频输入功能
 * 
 * @param name 轨道名称
 * @param testSignalType 测试信号类型
 * @param frequency 测试信号频率
 * @param amplitude 测试信号振幅
 * @param durationSeconds 测试时长（0表示无限）
 * @param enableDebug 是否启用调试
 * @return 测试音频轨道组件
 */
fun LocalParticipant.createTestAudioTrack(
    name: String = "test-audio",
    testSignalType: TestAudioProvider.TestSignalType = TestAudioProvider.TestSignalType.SINE_WAVE,
    frequency: Double = 440.0,
    amplitude: Double = 0.5,
    durationSeconds: Double = 30.0,
    enableDebug: Boolean = true
): TestAudioTrackResult {
    
    LKLog.d { "创建测试音频轨道 - 信号: $testSignalType, 频率: ${frequency}Hz, 时长: ${durationSeconds}s" }
    
    // 创建测试音频提供器
    val testProvider = TestAudioProvider(
        testType = testSignalType,
        frequency = frequency,
        amplitude = amplitude,
        durationSeconds = durationSeconds
    )
    
    // 创建增强版自定义音频轨道
    val (audioTrack, mixer, monitor) = createEnhancedCustomAudioTrack(
        name = name,
        customAudioProvider = testProvider,
        enableDebug = enableDebug
    )
    
    return TestAudioTrackResult(
        audioTrack = audioTrack,
        testProvider = testProvider,
        mixer = mixer,
        monitor = monitor
    )
}

/**
 * 测试音频轨道结果
 */
data class TestAudioTrackResult(
    val audioTrack: LocalAudioTrack,
    val testProvider: TestAudioProvider,
    val mixer: EnhancedCustomAudioMixer,
    val monitor: CustomAudioDebugUtils.CustomAudioProviderMonitor?
) {
    /**
     * 停止测试音频
     */
    fun stop() {
        mixer.stop()
        monitor?.stopMonitoring()
        LKLog.d { "测试音频轨道已停止" }
    }
    
    /**
     * 获取状态信息
     */
    fun getStatusInfo(): String {
        return """
            ${mixer.getStatusInfo()}
            
            ${testProvider.getGeneratorInfo()}
        """.trimIndent()
    }
}

/**
 * 为LocalAudioTrack添加调试功能
 */
fun LocalAudioTrack.enableAudioDebug(): CustomAudioDebugUtils.AudioBufferAnalyzer {
    val analyzer = CustomAudioDebugUtils.AudioBufferAnalyzer()
    
    // 添加调试回调（这需要扩展现有的回调机制）
    // 注意：这里只是示例，实际实现可能需要修改SDK内部结构
    LKLog.d { "为音频轨道 '${this.name}' 启用调试分析" }
    
    return analyzer
}

/**
 * 创建简单的正弦波测试轨道
 */
fun LocalParticipant.createSineWaveTestTrack(
    frequency: Double = 440.0,
    amplitude: Double = 0.5
): TestAudioTrackResult {
    return createTestAudioTrack(
        name = "sine-wave-test",
        testSignalType = TestAudioProvider.TestSignalType.SINE_WAVE,
        frequency = frequency,
        amplitude = amplitude,
        durationSeconds = 0.0 // 无限时长
    )
}

/**
 * 创建哔哔声测试轨道
 */
fun LocalParticipant.createBeepTestTrack(
    frequency: Double = 800.0,
    amplitude: Double = 0.3
): TestAudioTrackResult {
    return createTestAudioTrack(
        name = "beep-test",
        testSignalType = TestAudioProvider.TestSignalType.BEEP_SEQUENCE,
        frequency = frequency,
        amplitude = amplitude,
        durationSeconds = 0.0
    )
}

/**
 * 创建白噪声测试轨道
 */
fun LocalParticipant.createWhiteNoiseTestTrack(
    amplitude: Double = 0.1
): TestAudioTrackResult {
    return createTestAudioTrack(
        name = "white-noise-test",
        testSignalType = TestAudioProvider.TestSignalType.WHITE_NOISE,
        amplitude = amplitude,
        durationSeconds = 0.0
    )
}

/**
 * 音频轨道管理器 - 便于管理多个自定义音频轨道
 */
class CustomAudioTrackManager {
    private val activeTracks = mutableMapOf<String, TestAudioTrackResult>()
    
    /**
     * 添加音频轨道
     */
    fun addTrack(trackId: String, trackResult: TestAudioTrackResult) {
        stopTrack(trackId) // 停止现有轨道
        activeTracks[trackId] = trackResult
        LKLog.d { "添加音频轨道: $trackId" }
    }
    
    /**
     * 停止指定轨道
     */
    fun stopTrack(trackId: String) {
        activeTracks[trackId]?.let { track ->
            track.stop()
            activeTracks.remove(trackId)
            LKLog.d { "停止音频轨道: $trackId" }
        }
    }
    
    /**
     * 停止所有轨道
     */
    fun stopAllTracks() {
        activeTracks.forEach { (id, track) ->
            track.stop()
            LKLog.d { "停止音频轨道: $id" }
        }
        activeTracks.clear()
    }
    
    /**
     * 获取活跃轨道列表
     */
    fun getActiveTrackIds(): List<String> = activeTracks.keys.toList()
    
    /**
     * 获取轨道状态信息
     */
    fun getTrackStatus(trackId: String): String? {
        return activeTracks[trackId]?.getStatusInfo()
    }
    
    /**
     * 获取所有轨道状态
     */
    fun getAllTracksStatus(): String {
        if (activeTracks.isEmpty()) {
            return "没有活跃的自定义音频轨道"
        }
        
        return activeTracks.map { (id, track) ->
            "轨道 $id:\n${track.getStatusInfo()}"
        }.joinToString("\n\n")
    }
}