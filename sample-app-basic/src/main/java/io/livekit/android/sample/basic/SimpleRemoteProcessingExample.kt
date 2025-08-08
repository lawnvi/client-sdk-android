package io.livekit.android.sample.basic

import android.util.Log
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.setAudioDataProcessor
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 简单的远程音频处理示例
 * 
 * 专门演示如何在保持扬声器播放的同时处理远程音频数据
 */
class SimpleRemoteProcessingExample {
    
    private var recordingStream: FileOutputStream? = null
    private var audioPacketCount = 0
    
    /**
     * 核心功能：处理远程音频数据 + 保持扬声器播放
     * 
     * 这个方法实现了您需要的效果：
     * 1. 自定义处理服务端发来的音频数据
     * 2. 同时保持音频正常播放到扬声器
     * 3. 可以确认收到了服务端的数据
     */
    fun setupRemoteAudioProcessingWithPlayback(
        participant: Participant, 
        audioTrack: RemoteAudioTrack,
        outputFile: File
    ) {
        Log.d("SimpleExample", "设置远程音频处理 for ${participant.identity}")
        
        // 使用setAudioDataProcessor - 关键点：这个方法会处理数据但不影响正常播放
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            
            // 您的自定义处理逻辑
            processAudioData(participant, audioData, sampleRate, numberOfChannels, outputFile)
            
            // 音频会自动继续播放到扬声器 - 这是setAudioDataProcessor的特性
        }
    }
    
    /**
     * 自定义音频数据处理逻辑
     */
    private fun processAudioData(
        participant: Participant,
        audioData: ByteBuffer,
        sampleRate: Int,
        numberOfChannels: Int,
        outputFile: File
    ) {
        try {
            // 1. 录制音频数据
            if (recordingStream == null) {
                recordingStream = FileOutputStream(outputFile)
            }
            
            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            recordingStream?.write(audioBytes)
            
            // 2. 分析音频数据
            val volume = calculateVolume(audioBytes)
            audioPacketCount++
            
            // 3. 每100个包打印一次统计信息
            if (audioPacketCount % 100 == 0) {
                Log.d("SimpleExample", 
                    "处理来自 ${participant.identity} 的音频: " +
                    "包#$audioPacketCount, ${audioBytes.size} bytes, " +
                    "volume=$volume, $sampleRate Hz, $numberOfChannels channels"
                )
            }
            
            // 4. 在这里可以添加您的自定义处理逻辑：
            // - 发送到语音识别服务
            // - 音频分析和统计
            // - 实时音频处理
            // - 保存特定的音频片段
            // 等等...
            
        } catch (e: Exception) {
            Log.e("SimpleExample", "Error processing audio", e)
        }
    }
    
    /**
     * 计算音频音量
     */
    private fun calculateVolume(audioBytes: ByteArray): Float {
        var sum = 0L
        for (i in audioBytes.indices step 2) {
            if (i + 1 < audioBytes.size) {
                val sample = (audioBytes[i].toInt() and 0xFF) or 
                           ((audioBytes[i + 1].toInt() and 0xFF) shl 8)
                sum += sample * sample
            }
        }
        val mean = sum.toDouble() / (audioBytes.size / 2)
        return (kotlin.math.sqrt(mean) / Short.MAX_VALUE).toFloat()
    }
    
    /**
     * 停止处理并清理资源
     */
    fun stop() {
        recordingStream?.close()
        recordingStream = null
        Log.d("SimpleExample", "音频处理已停止，总共处理了 $audioPacketCount 个音频包")
    }
}

/*
使用示例：

// 1. 创建处理器实例
val processor = SimpleRemoteProcessingExample()

// 2. 当有远程参与者加入时，设置音频处理
room.events.collect { event ->
    when (event) {
        is RoomEvent.TrackSubscribed -> {
            if (event.publication.track is RemoteAudioTrack) {
                val participant = event.participant
                val audioTrack = event.publication.track as RemoteAudioTrack
                val outputFile = File(context.getExternalFilesDir(null), "remote_audio.pcm")
                
                // 关键调用：设置处理 + 保持播放
                processor.setupRemoteAudioProcessingWithPlayback(participant, audioTrack, outputFile)
            }
        }
    }
}

// 3. 停止时清理
processor.stop()
*/