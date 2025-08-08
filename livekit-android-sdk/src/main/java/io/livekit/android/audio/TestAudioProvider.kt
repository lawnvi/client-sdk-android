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
import io.livekit.android.util.LKLog
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

/**
 * 测试音频提供器
 *
 * 生成各种测试音频信号，用于验证自定义音频输入功能。
 */
class TestAudioProvider(
    private val testType: TestSignalType = TestSignalType.SINE_WAVE,
    private val frequency: Double = 440.0, // A4音符
    private val amplitude: Double = 0.5,   // 音量 (0.0 - 1.0)
    private val durationSeconds: Double = 10.0 // 生成音频的总时长，0表示无限
) : CustomAudioBufferProvider {

    enum class TestSignalType {
        SINE_WAVE,      // 正弦波
        SQUARE_WAVE,    // 方波
        TRIANGLE_WAVE,  // 三角波
        SAWTOOTH_WAVE,  // 锯齿波
        WHITE_NOISE,    // 白噪声
        PINK_NOISE,     // 粉红噪声
        SILENCE,        // 静音
        BEEP_SEQUENCE   // 哔哔声序列
    }

    companion object {
        private const val TAG = "TestAudioProvider"
    }

    private val isRunning = AtomicBoolean(false)
    private var currentSampleIndex = 0.0
    private var totalSamplesGenerated = 0L
    private var startTime = 0L

    // 噪声生成器状态
    private val random = kotlin.random.Random.Default
    private val pinkNoiseState = FloatArray(7) // Pink noise filter state

    override fun start() {
        if (isRunning.compareAndSet(false, true)) {
            currentSampleIndex = 0.0
            totalSamplesGenerated = 0L
            startTime = System.currentTimeMillis()
            LKLog.d { "$TAG: 🎵 启动测试音频生成器 - 类型: $testType, 频率: ${frequency}Hz, 振幅: $amplitude, 时长: ${if(durationSeconds > 0) "${durationSeconds}s" else "无限"}" }
        }
    }

    override fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
            LKLog.d { "$TAG: ⏹️ 停止测试音频生成器 - 运行时间: ${String.format("%.1f", elapsedTime)}s, 生成样本: $totalSamplesGenerated" }
        }
    }

    override fun hasMoreData(): Boolean {
        if (!isRunning.get()) return false

        if (durationSeconds > 0) {
            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
            return elapsedTime < durationSeconds
        }

        return true
    }

    override fun getCaptureTimeNs(): Long = System.nanoTime()

    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        if (!hasMoreData()) {
            return null
        }

        return when (audioFormat) {
            AudioFormat.ENCODING_PCM_16BIT -> generatePCM16(requestedBytes, channelCount, sampleRate)
            AudioFormat.ENCODING_PCM_8BIT -> generatePCM8(requestedBytes, channelCount, sampleRate)
            AudioFormat.ENCODING_PCM_FLOAT -> generatePCMFloat(requestedBytes, channelCount, sampleRate)
            else -> {
                LKLog.w { "$TAG: 不支持的音频格式: $audioFormat，使用PCM16作为默认格式" }
                generatePCM16(requestedBytes, channelCount, sampleRate)
            }
        }
    }

    /**
     * 生成PCM 16位音频数据
     */
    private fun generatePCM16(requestedBytes: Int, channelCount: Int, sampleRate: Int): ByteBuffer {
        val samplesPerChannel = requestedBytes / (2 * channelCount) // 16位 = 2字节
        val buffer = ByteBuffer.allocate(requestedBytes)

        repeat(samplesPerChannel) {
            val sampleValue = generateSample(currentSampleIndex, sampleRate)
            val shortValue = (sampleValue * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            // 为所有声道生成相同的样本
            repeat(channelCount) {
                buffer.putShort(shortValue)
            }

            currentSampleIndex++
            totalSamplesGenerated++
        }

        buffer.flip()
        return buffer
    }

    /**
     * 生成PCM 8位音频数据
     */
    private fun generatePCM8(requestedBytes: Int, channelCount: Int, sampleRate: Int): ByteBuffer {
        val samplesPerChannel = requestedBytes / channelCount
        val buffer = ByteBuffer.allocate(requestedBytes)

        repeat(samplesPerChannel) {
            val sampleValue = generateSample(currentSampleIndex, sampleRate)
            val byteValue = ((sampleValue + 1.0) * 127.5).toInt().coerceIn(0, 255).toByte()

            repeat(channelCount) {
                buffer.put(byteValue)
            }

            currentSampleIndex++
            totalSamplesGenerated++
        }

        buffer.flip()
        return buffer
    }

    /**
     * 生成PCM Float音频数据
     */
    private fun generatePCMFloat(requestedBytes: Int, channelCount: Int, sampleRate: Int): ByteBuffer {
        val samplesPerChannel = requestedBytes / (4 * channelCount) // Float = 4字节
        val buffer = ByteBuffer.allocate(requestedBytes)

        repeat(samplesPerChannel) {
            val sampleValue = generateSample(currentSampleIndex, sampleRate).toFloat()

            repeat(channelCount) {
                buffer.putFloat(sampleValue)
            }

            currentSampleIndex++
            totalSamplesGenerated++
        }

        buffer.flip()
        return buffer
    }

    /**
     * 生成单个音频样本
     */
    private fun generateSample(sampleIndex: Double, sampleRate: Int): Double {
        val time = sampleIndex / sampleRate

        return when (testType) {
            TestSignalType.SINE_WAVE -> generateSineWave(time)
            TestSignalType.SQUARE_WAVE -> generateSquareWave(time)
            TestSignalType.TRIANGLE_WAVE -> generateTriangleWave(time)
            TestSignalType.SAWTOOTH_WAVE -> generateSawtoothWave(time)
            TestSignalType.WHITE_NOISE -> generateWhiteNoise()
            TestSignalType.PINK_NOISE -> generatePinkNoise()
            TestSignalType.SILENCE -> 0.0
            TestSignalType.BEEP_SEQUENCE -> generateBeepSequence(time)
        } * amplitude
    }

    private fun generateSineWave(time: Double): Double {
        return sin(2.0 * PI * frequency * time)
    }

    private fun generateSquareWave(time: Double): Double {
        return if (sin(2.0 * PI * frequency * time) >= 0) 1.0 else -1.0
    }

    private fun generateTriangleWave(time: Double): Double {
        val phase = (frequency * time) % 1.0
        return if (phase < 0.5) 4.0 * phase - 1.0 else 3.0 - 4.0 * phase
    }

    private fun generateSawtoothWave(time: Double): Double {
        val phase = (frequency * time) % 1.0
        return 2.0 * phase - 1.0
    }

    private fun generateWhiteNoise(): Double {
        return random.nextDouble(-1.0, 1.0)
    }

    private fun generatePinkNoise(): Double {
        // Pink noise using the Voss algorithm
        val white = random.nextDouble(-1.0, 1.0)

        pinkNoiseState[0] = (0.99886f * pinkNoiseState[0] + white * 0.0555179f).toFloat()
        pinkNoiseState[1] = (0.99332f * pinkNoiseState[1] + white * 0.0750759f).toFloat()
        pinkNoiseState[2] = (0.96900f * pinkNoiseState[2] + white * 0.1538520f).toFloat()
        pinkNoiseState[3] = (0.86650f * pinkNoiseState[3] + white * 0.3104856f).toFloat()
        pinkNoiseState[4] = (0.55000f * pinkNoiseState[4] + white * 0.5329522f).toFloat()
        pinkNoiseState[5] = (-0.7616f * pinkNoiseState[5] - white * 0.0168980f).toFloat()

        val pink = pinkNoiseState[0] + pinkNoiseState[1] + pinkNoiseState[2] +
                  pinkNoiseState[3] + pinkNoiseState[4] + pinkNoiseState[5] +
                  pinkNoiseState[6] + white * 0.5362f

        pinkNoiseState[6] = (white * 0.115926f).toFloat()

        return (pink * 0.11).coerceIn(-1.0, 1.0)
    }

    private fun generateBeepSequence(time: Double): Double {
        // 每2秒一个周期：1秒哔声，1秒静音
        val cycleTime = time % 2.0
        return if (cycleTime < 1.0) {
            sin(2.0 * PI * frequency * time)
        } else {
            0.0
        }
    }

    /**
     * 获取生成器状态信息
     */
    fun getGeneratorInfo(): String {
        val elapsedTime = if (startTime > 0) (System.currentTimeMillis() - startTime) / 1000.0 else 0.0
        val samplesPerSecond = if (elapsedTime > 0) totalSamplesGenerated / elapsedTime else 0.0

        return """
            测试音频生成器状态:
            - 信号类型: $testType
            - 频率: ${frequency}Hz
            - 振幅: $amplitude
            - 运行状态: ${if(isRunning.get()) "运行中" else "已停止"}
            - 运行时间: ${String.format("%.1f", elapsedTime)}s
            - 生成样本: $totalSamplesGenerated
            - 采样率: ${String.format("%.0f", samplesPerSecond)}/s
            - 剩余时间: ${if(durationSeconds > 0) String.format("%.1f", (durationSeconds - elapsedTime).coerceAtLeast(0.0)) + "s" else "无限"}
        """.trimIndent()
    }
}

/**
 * 测试音频提供器工厂
 */
object TestAudioProviderFactory {

    /**
     * 创建440Hz正弦波测试音频
     */
    fun createSineWaveProvider(frequency: Double = 440.0, amplitude: Double = 0.5): TestAudioProvider {
        return TestAudioProvider(
            testType = TestAudioProvider.TestSignalType.SINE_WAVE,
            frequency = frequency,
            amplitude = amplitude
        )
    }

    /**
     * 创建哔哔声序列测试音频
     */
    fun createBeepProvider(frequency: Double = 800.0, amplitude: Double = 0.3): TestAudioProvider {
        return TestAudioProvider(
            testType = TestAudioProvider.TestSignalType.BEEP_SEQUENCE,
            frequency = frequency,
            amplitude = amplitude
        )
    }

    /**
     * 创建白噪声测试音频
     */
    fun createWhiteNoiseProvider(amplitude: Double = 0.1): TestAudioProvider {
        return TestAudioProvider(
            testType = TestAudioProvider.TestSignalType.WHITE_NOISE,
            amplitude = amplitude
        )
    }

    /**
     * 创建静音测试音频
     */
    fun createSilenceProvider(): TestAudioProvider {
        return TestAudioProvider(
            testType = TestAudioProvider.TestSignalType.SILENCE
        )
    }
}
