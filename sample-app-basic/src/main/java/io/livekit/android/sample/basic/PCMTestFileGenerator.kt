package io.livekit.android.sample.basic

import android.media.AudioFormat
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/**
 * 简单的PCM测试文件生成器
 * 用于生成不同频率的正弦波音频文件，方便测试自定义音频输入功能
 */
object PCMTestFileGenerator {
    
    /**
     * 生成PCM测试文件
     * 
     * @param outputFile 输出文件
     * @param durationSeconds 音频时长（秒）
     * @param frequency 正弦波频率（Hz），默认440Hz（A音）
     * @param sampleRate 采样率，默认44100Hz
     * @param channelCount 声道数，默认2（立体声）
     * @param amplitude 振幅，0.0-1.0，默认0.3
     */
    fun generateSineWavePCM(
        outputFile: File,
        durationSeconds: Int = 5,  // 改短一些
        frequency: Double = 440.0,
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        amplitude: Float = 0.3f
    ) {
        val totalSamples = sampleRate * durationSeconds
        val totalBytes = totalSamples * channelCount * 2 // 16-bit = 2 bytes per sample
        
        // 确保输出目录存在
        outputFile.parentFile?.mkdirs()
        
        FileOutputStream(outputFile).use { fos ->
            val buffer = ByteBuffer.allocate(4096) // 4KB buffer
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            
            var sampleIndex = 0
            
            while (sampleIndex < totalSamples) {
                buffer.clear()
                
                // 填充缓冲区
                while (buffer.remaining() >= (channelCount * 2) && sampleIndex < totalSamples) {
                    // 计算正弦波样本值
                    val time = sampleIndex.toDouble() / sampleRate
                    val sample = (sin(2.0 * PI * frequency * time) * amplitude * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    
                    // 写入所有声道（立体声情况下左右声道相同）
                    repeat(channelCount) {
                        buffer.putShort(sample)
                    }
                    
                    sampleIndex++
                }
                
                buffer.flip()
                fos.write(buffer.array(), 0, buffer.limit())
            }
        }
        
        println("✅ PCM测试文件已生成: ${outputFile.absolutePath}")
        println("📊 文件信息:")
        println("   - 时长: ${durationSeconds}秒")
        println("   - 频率: ${frequency}Hz")
        println("   - 采样率: ${sampleRate}Hz")
        println("   - 声道数: $channelCount")
        println("   - 文件大小: ${outputFile.length() / 1024}KB")
    }
    
    /**
     * 生成包含多个频率的复合音频
     */
    fun generateMultiTonePCM(
        outputFile: File,
        durationSeconds: Int = 10,
        frequencies: List<Double> = listOf(440.0, 554.37, 659.25), // A, C#, E (A大调)
        sampleRate: Int = 44100,
        channelCount: Int = 2,
        amplitude: Float = 0.2f
    ) {
        val totalSamples = sampleRate * durationSeconds
        
        outputFile.parentFile?.mkdirs()
        
        FileOutputStream(outputFile).use { fos ->
            val buffer = ByteBuffer.allocate(4096)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            
            var sampleIndex = 0
            
            while (sampleIndex < totalSamples) {
                buffer.clear()
                
                while (buffer.remaining() >= (channelCount * 2) && sampleIndex < totalSamples) {
                    val time = sampleIndex.toDouble() / sampleRate
                    
                    // 混合多个频率
                    var mixedSample = 0.0
                    frequencies.forEach { freq ->
                        mixedSample += sin(2.0 * PI * freq * time)
                    }
                    mixedSample /= frequencies.size // 平均化防止过载
                    
                    val sample = (mixedSample * amplitude * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    
                    repeat(channelCount) {
                        buffer.putShort(sample)
                    }
                    
                    sampleIndex++
                }
                
                buffer.flip()
                fos.write(buffer.array(), 0, buffer.limit())
            }
        }
        
        println("✅ 多音调PCM文件已生成: ${outputFile.absolutePath}")
        println("📊 包含频率: ${frequencies.joinToString(", ")}Hz")
    }
    
    /**
     * 生成静音PCM文件（用于测试）
     */
    fun generateSilencePCM(
        outputFile: File,
        durationSeconds: Int = 5,
        sampleRate: Int = 44100,
        channelCount: Int = 2
    ) {
        val totalBytes = sampleRate * durationSeconds * channelCount * 2
        
        outputFile.parentFile?.mkdirs()
        
        FileOutputStream(outputFile).use { fos ->
            val silenceBuffer = ByteArray(4096) // 已经是全零
            var bytesWritten = 0
            
            while (bytesWritten < totalBytes) {
                val bytesToWrite = minOf(silenceBuffer.size, totalBytes - bytesWritten)
                fos.write(silenceBuffer, 0, bytesToWrite)
                bytesWritten += bytesToWrite
            }
        }
        
        println("✅ 静音PCM文件已生成: ${outputFile.absolutePath}")
    }
}