package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.util.Log
import io.livekit.android.audio.CustomAudioMixer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.track.LocalAudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 诊断性音频测试示例
 * 
 * 该示例旨在逐步检查每个组件是否正常工作：
 * 1. 检查音频回调是否被调用
 * 2. 检查自定义音频混音器是否工作
 * 3. 检查音频数据是否正确流动
 */
class DiagnosticAudioExample(
    private val context: Context,
    private val room: Room
) {
    companion object {
        private const val TAG = "DiagnosticAudio"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var debugger: AudioCallbackDebugger? = null
    private var audioTrack: LocalAudioTrack? = null
    private var isRunning = false

    suspend fun runDiagnostic(pcmFile: File) {
        Log.d(TAG, "🔧 开始诊断性音频测试")
        
        // 第1步：检查基础音频回调
        Log.d(TAG, "📍 步骤1: 测试基础音频回调")
        testBasicAudioCallback()
        
        delay(3000) // 等待3秒观察回调
        
        // 第2步：测试自定义音频混音器
        Log.d(TAG, "📍 步骤2: 测试自定义音频混音器")
        testCustomAudioMixer(pcmFile)
        
        delay(10000) // 运行10秒
        
        // 第3步：输出诊断结果
        Log.d(TAG, "📍 步骤3: 诊断结果")
        outputDiagnosticResults()
        
        cleanup()
    }
    
    private suspend fun testBasicAudioCallback() {
        try {
            val localParticipant = room.localParticipant
            
            // 创建普通音频轨道
            audioTrack = localParticipant.createAudioTrack("diagnostic_track")
            
            // 设置调试回调
            debugger = AudioCallbackDebugger()
            audioTrack?.setAudioBufferCallback(debugger)
            
            Log.d(TAG, "✅ 音频轨道已创建: ${audioTrack?.name}")
            
            // 发布轨道
            localParticipant.publishAudioTrack(audioTrack!!)
            Log.d(TAG, "✅ 音频轨道已发布")
            
            // 启用轨道
            audioTrack?.enabled = true
            Log.d(TAG, "✅ 音频轨道已启用: ${audioTrack?.enabled}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 基础音频回调测试失败", e)
        }
    }
    
    private suspend fun testCustomAudioMixer(pcmFile: File) {
        try {
            // 停止之前的测试
            audioTrack?.enabled = false
            
            val localParticipant = room.localParticipant
            
            // 创建带自定义混音器的音频轨道
            Log.d(TAG, "🎵 创建自定义音频轨道...")
            val (customAudioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
                name = "diagnostic_custom_track",
                audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                channelCount = 2,
                sampleRate = 44100,
                mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
            )
            
            // 替换音频轨道
            localParticipant.unpublishTrack(audioTrack!!)
            audioTrack = customAudioTrack
            
            Log.d(TAG, "✅ 自定义音频轨道已创建")
            
            // 发布新轨道
            localParticipant.publishAudioTrack(audioTrack!!)
            Log.d(TAG, "✅ 自定义音频轨道已发布")
            
            // 启用轨道
            audioTrack?.enabled = true
            Log.d(TAG, "✅ 自定义音频轨道已启用: ${audioTrack?.enabled}")
            
            // 开始发送音频数据
            if (pcmFile.exists()) {
                scope.launch {
                    sendAudioData(pcmFile, bufferProvider)
                }
            } else {
                Log.w(TAG, "⚠️ PCM文件不存在，跳过音频数据发送")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 自定义音频混音器测试失败", e)
        }
    }
    
    private suspend fun sendAudioData(pcmFile: File, bufferProvider: io.livekit.android.audio.BufferAudioBufferProvider) {
        Log.d(TAG, "🔄 开始发送PCM音频数据...")
        isRunning = true
        
        try {
            pcmFile.inputStream().use { inputStream ->
                val bufferSize = 1764 // 10ms of 44.1kHz stereo 16-bit audio
                val buffer = ByteArray(bufferSize)
                var chunkCount = 0
                
                while (isRunning && chunkCount < 500) { // 限制发送数量
                    val bytesRead = inputStream.read(buffer)
                    
                    if (bytesRead > 0) {
                        bufferProvider.addAudioData(buffer.copyOf(bytesRead))
                        chunkCount++
                        
                        if (chunkCount % 50 == 0) {
                            val queueSize = bufferProvider.getQueuedBufferCount()
                            Log.d(TAG, "📤 已发送 $chunkCount 个音频包，队列大小: $queueSize")
                        }
                        
                        delay(10) // 10ms间隔
                    } else {
                        // 文件结束，重新开始
                        inputStream.channel.position(0)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 发送音频数据失败", e)
        }
        
        isRunning = false
        Log.d(TAG, "⏹️ 音频数据发送完成")
    }
    
    private fun outputDiagnosticResults() {
        Log.d(TAG, "📊 ===== 诊断结果 =====")
        
        // 基础信息
        Log.d(TAG, "🏠 房间状态: ${room.state}")
        Log.d(TAG, "👤 本地参与者: ${room.localParticipant.identity}")
        Log.d(TAG, "📡 已发布轨道数: ${room.localParticipant.audioTrackPublications.size}")
        Log.d(TAG, "🎯 远程参与者数: ${room.remoteParticipants.size}")
        
        // 音频轨道状态
        audioTrack?.let { track ->
            Log.d(TAG, "🎵 音频轨道信息:")
            Log.d(TAG, "  - 名称: ${track.name}")
            Log.d(TAG, "  - 已启用: ${track.enabled}")
            Log.d(TAG, "  - SID: ${track.sid}")
            Log.d(TAG, "  - 流状态: ${track.streamState}")
        }
        
        // 音频回调统计
        debugger?.let { debug ->
            Log.d(TAG, "🎤 音频回调统计: ${debug.getStatus()}")
        }
        
        Log.d(TAG, "========================")
        
        // 问题诊断
        diagnosePotentialIssues()
    }
    
    private fun diagnosePotentialIssues() {
        Log.d(TAG, "🔍 问题诊断:")
        
        // 检查音频回调
        val callbackStatus = debugger?.getStatus() ?: "未知"
        if (callbackStatus.contains("0 calls")) {
            Log.w(TAG, "⚠️ 音频回调从未被调用 - 可能的原因:")
            Log.w(TAG, "   1. 麦克风权限未授予")
            Log.w(TAG, "   2. 音频设备模块配置问题")
            Log.w(TAG, "   3. WebRTC音频引擎未启动")
        } else {
            Log.d(TAG, "✅ 音频回调正常工作")
        }
        
        // 检查音频轨道状态
        audioTrack?.let { track ->
            if (!track.enabled) {
                Log.w(TAG, "⚠️ 音频轨道未启用")
            }
            if (track.sid.isEmpty()) {
                Log.w(TAG, "⚠️ 音频轨道未获得SID - 可能未正确发布")
            }
        }
        
        // 检查房间连接
        if (room.state.toString() != "CONNECTED") {
            Log.w(TAG, "⚠️ 房间未连接: ${room.state}")
        }
        
        // 检查权限
        val hasRecordPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        if (hasRecordPermission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "⚠️ 缺少录音权限")
        } else {
            Log.d(TAG, "✅ 录音权限已授予")
        }
    }
    
    private fun cleanup() {
        Log.d(TAG, "🧹 清理资源...")
        isRunning = false
        audioTrack?.enabled = false
        debugger = null
    }
    
    fun stop() {
        Log.d(TAG, "⏹️ 停止诊断")
        cleanup()
    }
}