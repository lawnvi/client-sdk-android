package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.audio.*
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.participant.createEnhancedCustomAudioTrack
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.clearAudioDataInterceptor
import io.livekit.android.room.track.muteWithProcessor
import io.livekit.android.room.track.setAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 远程音频处理测试活动
 *
 * 专门测试远程音频数据的自定义处理功能：
 * - 输入：使用麦克风（正常音频输入）
 * - 输出：自定义处理服务端音频数据 + 同时播放到扬声器
 */
class RemoteAudioProcessingTestActivity : AppCompatActivity() {

    private lateinit var room: Room
    private lateinit var statusTextView: TextView
    private lateinit var audioStatsTextView: TextView

    // 测试状态
    private var isProcessingRemoteAudio = false
    private var recordingOutputStream: FileOutputStream? = null
    private var audioPacketCount = 0
    private var totalAudioBytes = 0L
    private var lastUpdateTime = System.currentTimeMillis()

    // 自定义音频输入
    private var customAudioTrack: LocalAudioTrack? = null
    private var audioBufferProvider: BufferAudioBufferProvider? = null
    private var inputPCMFile: File? = null
    private var pcmInputStream: FileInputStream? = null
    private var pcmFilePosition = 0L
    private var totalPCMPacketsSent = 0

    // 文件路径
    private lateinit var recordedAudioFile: File
    private lateinit var testLogFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_remote_audio_test)

        initViews()
        setupFiles()
        setupRoom()
    }

    private fun initViews() {
        statusTextView = findViewById(R.id.statusTextView)
        audioStatsTextView = findViewById(R.id.audioStatsTextView)

        findViewById<Button>(R.id.btnConnectRoom).setOnClickListener {
            connectToRoom()
        }

        findViewById<Button>(R.id.btnStartMicrophone).setOnClickListener {
            startMicrophone()
        }

        findViewById<Button>(R.id.btnStartProcessingWithPlayback).setOnClickListener {
            startRemoteAudioProcessingWithPlayback()
        }

        findViewById<Button>(R.id.btnStartProcessingOnly).setOnClickListener {
            startRemoteAudioProcessingOnly()
        }

        findViewById<Button>(R.id.btnStopProcessing).setOnClickListener {
            stopRemoteAudioProcessing()
        }

        findViewById<Button>(R.id.btnSetupCustomAudio).setOnClickListener {
            setupCustomAudioInput()
        }

        findViewById<Button>(R.id.btnSendPCMChunk).setOnClickListener {
            sendPCMChunk()
        }

        updateStatus("准备开始测试远程音频处理...")
        updateAudioStats("等待音频数据...")
    }

    private fun setupFiles() {
        val filesDir = getExternalFilesDir(null)
        recordedAudioFile = File(filesDir, "remote_audio_${System.currentTimeMillis()}.pcm")
        testLogFile = File(filesDir, "remote_audio_test_log.txt")

        // 设置输入PCM文件
        inputPCMFile = File(filesDir, "input.pcm")

        // 生成测试PCM文件（如果不存在）
        if (inputPCMFile?.exists() != true) {
            generateTestPCMFile()
        }
    }

    private fun generateTestPCMFile() {
        val file = inputPCMFile ?: return
        updateStatus("🎵 生成测试PCM文件...")

        PCMTestFileGenerator.generateSineWavePCM(
            outputFile = file,
            durationSeconds = 10,
            frequency = 440.0,
            sampleRate = 48000,  // 与音频轨道采样率一致
            channelCount = 2,
            amplitude = 0.5f
        )

        updateStatus("✅ 测试PCM文件已生成: ${file.length() / 1024}KB")
        logTestResult("生成测试PCM文件: ${file.absolutePath}")
    }

    private fun setupRoom() {
        room = LiveKit.create(this)

        lifecycleScope.launch {
            room.events.collect { event ->
                handleRoomEvent(event)
            }
        }
    }

    private fun connectToRoom() {
        lifecycleScope.launch {
            try {
                updateStatus("🌐 连接到房间...")

                val wsUrl = "wss://ls1.dearlink.com"
                val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NTQ2MzI3ODYsImlzcyI6ImxpdmVraXQtYXBpLWtleS0xIiwibmFtZSI6ImRldl91c2VyMSIsIm5iZiI6MTc1NDM3MzU4Niwic3ViIjoiZGV2X3VzZXIxIiwidmlkZW8iOnsicm9vbSI6Im15LWRldi1yb29tIiwicm9vbUpvaW4iOnRydWV9fQ.rAS9JGI3MyV9MjCNVCjsHuZs_A88TjR9aitx3iILfDg"

                room.connect(wsUrl, token)

            } catch (e: Exception) {
                updateStatus("❌ 连接失败: ${e.message}")
                Log.e("RemoteAudioTest", "Connection failed", e)
            }
        }
    }

    private fun startMicrophone() {
        lifecycleScope.launch {
            try {
                updateStatus("🎤 启动麦克风...")

                // 创建普通的麦克风音频轨道
                val audioTrack = room.localParticipant.createAudioTrack("microphone_track")

                // 发布音频轨道
                room.localParticipant.publishAudioTrack(audioTrack)

                updateStatus("✅ 麦克风已启动并发布")

            } catch (e: Exception) {
                updateStatus("❌ 麦克风启动失败: ${e.message}")
                Log.e("RemoteAudioTest", "Microphone start failed", e)
            }
        }
    }

    /**
     * 设置自定义音频输入（从PCM文件）
     */
        private fun setupCustomAudioInput() {
        lifecycleScope.launch {
            try {
                updateStatus("🎵 设置自定义音频输入...")

                // 1. 检查基础条件
                val file = inputPCMFile
                if (file == null || !file.exists()) {
                    updateStatus("❌ PCM文件不存在")
                    return@launch
                }

                // 2. 检查房间状态
                if (room.state != io.livekit.android.room.Room.State.CONNECTED) {
                    updateStatus("❌ 房间未连接，当前状态: ${room.state}")
                    return@launch
                }

                // 3. 先停止并移除之前的音频轨道（如果存在）
                customAudioTrack?.let { track ->
                    updateStatus("🔄 移除之前的自定义轨道...")
                    try {
                        room.localParticipant.unpublishTrack(track)
                        logTestResult("成功移除之前的轨道: ${track.name}")
                    } catch (e: Exception) {
                        logTestResult("移除轨道失败: ${e.message}")
                    }
                }

                // 4. 详细日志：创建轨道前的状态
                logTestResult("=== 创建自定义音频轨道 ===")
                logTestResult("PCM文件大小: ${file.length()} bytes")
                logTestResult("房间状态: ${room.state}")
                logTestResult("本地参与者ID: ${room.localParticipant.identity}")

                // 5. 创建增强版自定义音频轨道（修复版本）
                updateStatus("🎵 创建增强版自定义音频轨道...")
                
                // 创建缓冲区提供器
                val bufferProvider = BufferAudioBufferProvider(
                    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                    channelCount = 2,
                    sampleRate = 48000,
                    loop = true
                )
                
                // 使用增强版混音器
                val (audioTrack, mixer, monitor) = room.localParticipant.createEnhancedCustomAudioTrack(
                    name = "enhanced_pcm_track_${System.currentTimeMillis()}",
                    customAudioProvider = bufferProvider,
                    microphoneGain = 0.0f,  // 完全禁用麦克风
                    customAudioGain = 1.0f,
                    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY,  // 只使用自定义音频
                    enableDebug = true
                )

                // 6. 详细检查创建的轨道
                logTestResult("=== 轨道创建结果 ===")
                logTestResult("轨道名称: ${audioTrack.name}")
                logTestResult("轨道启用状态: ${audioTrack.enabled}")
                logTestResult("轨道种类: ${audioTrack.kind}")
                logTestResult("轨道静音状态: ${audioTrack.enabled}")
                logTestResult("缓冲提供器类型: ${bufferProvider.javaClass.simpleName}")
                logTestResult("增强版混音器状态: ${mixer.isActivated()}")
                logTestResult("监控器启动状态: ${if (monitor != null) "已启动" else "未启动"}")

                // 显示混音器详细状态
                logTestResult("=== 增强版混音器详细状态 ===")
                logTestResult(mixer.getStatusInfo())

                // 检查缓冲提供器状态
                val isProviderRunning = bufferProvider.hasMoreData()
                logTestResult("⚠️ 缓冲提供器运行状态: $isProviderRunning")

                if (!isProviderRunning) {
                    logTestResult("⚠️ 检测到提供器未运行，正在手动启动...")
                    updateStatus("⚠️ 提供器未启动，正在手动启动...")
                    try {
                        bufferProvider.start()
                        logTestResult("✅ 手动启动提供器成功")
                    } catch (e: Exception) {
                        logTestResult("❌ 手动启动提供器失败: ${e.message}")
                    }
                }

                // 7. 发布音频轨道
                updateStatus("📡 发布音频轨道...")
                try {
                    room.localParticipant.publishAudioTrack(audioTrack)
                    logTestResult("✅ 轨道发布成功")
                } catch (e: Exception) {
                    updateStatus("❌ 轨道发布失败: ${e.message}")
                    logTestResult("❌ 轨道发布失败: ${e.message}")
                    return@launch
                }

                // 8. 保存引用
                customAudioTrack = audioTrack
                audioBufferProvider = bufferProvider

                // 9. 准备PCM文件流
                pcmInputStream?.close()
                pcmInputStream = FileInputStream(file)
                pcmFilePosition = 0L
                totalPCMPacketsSent = 0

                // 10. 验证发布后的状态
                delay(1000) // 等待发布完成
                logTestResult("=== 发布后状态 ===")
                logTestResult("房间状态: ${room.state}")
                logTestResult("本地轨道数: ${room.localParticipant.audioTrackPublications.size}")
                room.localParticipant.audioTrackPublications.forEach { (sid, publication) ->
                    logTestResult("轨道 $sid: ${publication?.name}, 启用=${publication?.enabled}")
                }

                // 11. 立即发送测试数据
                updateStatus("🔧 发送测试音频数据...")
                delay(500)
                sendTestPCMChunk(bufferProvider)

                // 12. 最终状态检查
                delay(1000)
                val queueCount = bufferProvider.getQueuedBufferCount()
                val hasData = bufferProvider.hasMoreData()

                // 13. 获取增强版混音器调试信息
                val mixerDebugInfo = mixer.getStatusInfo()

                updateStatus("✅ 自定义音频输入设置完成")
                logTestResult("=== 最终状态 ===")
                logTestResult("缓冲区队列: $queueCount 个")
                logTestResult("有可用数据: $hasData")
                logTestResult("PCM文件: ${file.length() / 1024}KB")
                logTestResult("增强版混音器状态: $mixerDebugInfo")

            } catch (e: Exception) {
                updateStatus("❌ 设置自定义音频输入失败: ${e.message}")
                Log.e("RemoteAudioTest", "Setup custom audio failed", e)
                logTestResult("❌ 异常详情: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * 发送一个PCM数据块
     */
    private fun sendPCMChunk() {
        lifecycleScope.launch {
            try {
                val provider = audioBufferProvider
                val inputStream = pcmInputStream

                if (provider == null) {
                    updateStatus("⚠️ 请先设置自定义音频输入")
                    return@launch
                }

                if (inputStream == null) {
                    updateStatus("⚠️ PCM文件流未打开")
                    return@launch
                }

                // 读取一个音频数据块（约100ms的音频）
                val sampleRate = 48000  // 与轨道采样率一致
                val channelCount = 2
                val bytesPerSample = 2  // 16-bit
                val durationMs = 100  // 100毫秒
                val chunkSize = (sampleRate * channelCount * bytesPerSample * durationMs) / 1000

                val buffer = ByteArray(chunkSize)
                val bytesRead = inputStream.read(buffer)

                if (bytesRead > 0) {
                    // 发送音频数据
                    val audioData = if (bytesRead < chunkSize) {
                        buffer.copyOf(bytesRead)
                    } else {
                        buffer
                    }

                    // 分析音频数据
                    val audioVolume = calculateAudioVolume(audioData)
                    val audioSum = audioData.sum().toLong()

                    // 安全地添加音频数据，防止BufferOverflowException
                    try {
                        provider.addAudioData(audioData)
                        logTestResult("✅ 成功添加音频数据: ${audioData.size} bytes")
                    } catch (e: Exception) {
                        logTestResult("❌ 添加音频数据失败: ${e.message}")
                        updateStatus("❌ 添加音频数据失败: ${e.message}")
                        return@launch
                    }
                    totalPCMPacketsSent++
                    pcmFilePosition += bytesRead

                    val fileSize = inputPCMFile?.length() ?: 0
                    val progress = if (fileSize > 0) ((pcmFilePosition * 100) / fileSize).toInt() else 0

                    updateStatus("📤 已发送PCM数据块 #$totalPCMPacketsSent (${bytesRead}字节) - 进度: ${progress}% - 音量: ${String.format("%.3f", audioVolume)}")
                    logTestResult("发送PCM数据块: ${bytesRead} bytes, 包#$totalPCMPacketsSent, 音量: $audioVolume, 数据和: $audioSum")

                    // 更新统计信息

                    updateAudioStats(
                        "自定义音频输入统计:\n" +
                        "已发送包数: $totalPCMPacketsSent\n" +
                        "文件位置: ${pcmFilePosition / 1024}KB / ${fileSize / 1024}KB\n" +
                        "进度: ${progress}%\n" +
                        "本次数据: ${bytesRead} bytes (${durationMs}ms)\n" +
                        "音频音量: ${String.format("%.3f", audioVolume)}\n" +
                        "数据和: $audioSum\n" +
                        "采样率: ${sampleRate}Hz, 声道: $channelCount\n" +
                        "轨道状态: ${if (customAudioTrack?.enabled == true) "启用" else "禁用"}\n" +
                        "缓冲区队列: ${provider.getQueuedBufferCount()} 个待处理\n" +
                        "提供器状态: ${if (provider.hasMoreData()) "有数据" else "无数据"}"
                    )

                } else {
                    // 文件结束，重置到开头
                    inputStream.channel.position(0)
                    pcmFilePosition = 0L
                    updateStatus("🔄 PCM文件已读完，重置到开头")
                    logTestResult("PCM文件读取完毕，重置到开头")
                }

            } catch (e: Exception) {
                updateStatus("❌ 发送PCM数据失败: ${e.message}")
                Log.e("RemoteAudioTest", "Send PCM chunk failed", e)
            }
        }
    }

    /**
     * 发送测试PCM数据块来验证设置
     */
    private suspend fun sendTestPCMChunk(provider: BufferAudioBufferProvider) {
        try {
            // 生成一个简单的测试音频信号（1秒的440Hz正弦波）
            val sampleRate = 48000
            val channelCount = 2
            val durationMs = 1000  // 1秒
            val samples = (sampleRate * durationMs) / 1000
            val testData = ByteArray(samples * channelCount * 2) // 16-bit stereo

            for (i in 0 until samples) {
                val time = i.toDouble() / sampleRate
                val sample = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * time) * 0.3 * Short.MAX_VALUE).toInt()

                for (channel in 0 until channelCount) {
                    val byteIndex = (i * channelCount + channel) * 2
                    if (byteIndex + 1 < testData.size) {
                        testData[byteIndex] = (sample and 0xFF).toByte()
                        testData[byteIndex + 1] = ((sample shr 8) and 0xFF).toByte()
                    }
                }
            }

            val testVolume = calculateAudioVolume(testData)
            
            try {
                provider.addAudioData(testData)
                updateStatus("✅ 发送测试音频数据: ${testData.size} bytes, 音量: ${String.format("%.3f", testVolume)}")
                logTestResult("测试音频数据发送: ${testData.size} bytes, 音量: $testVolume")
                logTestResult("测试后缓冲区队列: ${provider.getQueuedBufferCount()} 个, 状态: ${if (provider.hasMoreData()) "有数据" else "无数据"}")
            } catch (e: Exception) {
                updateStatus("❌ 发送测试音频数据失败: ${e.message}")
                logTestResult("❌ 测试音频数据发送失败: ${e.message}")
                Log.e("RemoteAudioTest", "Failed to add test audio data", e)
            }

        } catch (e: Exception) {
            Log.e("RemoteAudioTest", "Failed to send test PCM chunk", e)
            updateStatus("❌ 发送测试音频失败: ${e.message}")
        }
    }

    /**
     * 开始远程音频处理 + 扬声器播放
     * 这是您需要的主要功能：自定义处理的同时也播放音频
     */
    private fun startRemoteAudioProcessingWithPlayback() {
        updateStatus("🚀 开始远程音频处理（带扬声器播放）...")

        room.remoteParticipants.values.forEach { participant ->
            setupRemoteAudioProcessingWithPlayback(participant)
        }

        if (room.remoteParticipants.isEmpty()) {
            updateStatus("⚠️ 没有远程参与者，等待其他参与者加入...")
        }
    }

    /**
     * 仅处理远程音频（不播放）
     * 用于对比测试
     */
    private fun startRemoteAudioProcessingOnly() {
        updateStatus("🚀 开始远程音频处理（仅处理，不播放）...")

        room.remoteParticipants.values.forEach { participant ->
            setupRemoteAudioProcessingOnly(participant)
        }

        if (room.remoteParticipants.isEmpty()) {
            updateStatus("⚠️ 没有远程参与者，等待其他参与者加入...")
        }
    }

    private fun stopRemoteAudioProcessing() {
        isProcessingRemoteAudio = false
        recordingOutputStream?.close()
        recordingOutputStream = null

        // 清除所有音频拦截器，恢复正常播放
        room.remoteParticipants.values.forEach { participant ->
            participant.audioTrackPublications.forEach { (_, publication) ->
                val audioTrack = publication as? RemoteAudioTrack
                audioTrack?.clearAudioDataInterceptor()
            }
        }

        updateStatus("⏹️ 远程音频处理已停止，恢复正常播放")
        logTestResult("远程音频处理停止")
    }

    /**
     * 设置远程音频处理 + 扬声器播放
     * 关键实现：使用AudioDataProcessor，它会处理音频但不影响正常播放
     */
    private fun setupRemoteAudioProcessingWithPlayback(participant: Participant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull { it.first.track is RemoteAudioTrack }?.first?.track as? RemoteAudioTrack

        if (audioTrack == null) {
            updateStatus("⚠️ 参与者 ${participant.identity} 没有音频轨道")
            return
        }

        updateStatus("🎧 设置 ${participant.identity} 的音频处理（带播放）...")

        // 使用AudioDataProcessor - 这会处理音频数据但保持正常播放
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            // 自定义处理音频数据
            processRemoteAudioData(participant, audioData, sampleRate, numberOfChannels, "处理+播放")

            // 音频会自动继续播放到扬声器（AudioDataProcessor的特性）
        }

        isProcessingRemoteAudio = true
        logTestResult("开始处理 ${participant.identity} 的音频（带扬声器播放）")
    }

        /**
     * 设置仅处理远程音频（不播放）
     * 用于对比测试
     */
    private fun setupRemoteAudioProcessingOnly(participant: Participant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull { it.first.track is RemoteAudioTrack }?.first?.track as? RemoteAudioTrack

        if (audioTrack == null) {
            updateStatus("⚠️ 参与者 ${participant.identity} 没有音频轨道")
            return
        }

        updateStatus("🎧 设置 ${participant.identity} 的音频处理（仅处理）...")

        // 使用muteWithProcessor - 专门用于静音但仍处理音频数据
        audioTrack.muteWithProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            // 自定义处理音频数据
            processRemoteAudioData(participant, audioData, sampleRate, numberOfChannels, "仅处理")
        }

        isProcessingRemoteAudio = true
        logTestResult("开始处理 ${participant.identity} 的音频（仅处理，不播放）- 使用muteWithProcessor")
    }

    /**
     * 处理远程音频数据的核心逻辑
     */
    private fun processRemoteAudioData(
        participant: Participant,
        audioData: ByteBuffer,
        sampleRate: Int,
        numberOfChannels: Int,
        mode: String
    ) {
        try {
            // 1. 录制音频到文件
            if (recordingOutputStream == null) {
                recordingOutputStream = FileOutputStream(recordedAudioFile)
                Log.d("RemoteAudioTest", "开始录制音频到: ${recordedAudioFile.absolutePath}")
            }

            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            recordingOutputStream?.write(audioBytes)

            // 2. 分析音频数据
            val volume = calculateAudioVolume(audioBytes)
            val duration = (audioBytes.size / (numberOfChannels * 2 * sampleRate / 1000.0)).toFloat()

            // 3. 更新统计信息
            audioPacketCount++
            totalAudioBytes += audioBytes.size

            // 4. 定期更新UI（避免过于频繁的更新）
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 500) { // 每500ms更新一次
                updateAudioStats(
                    "模式: $mode\n" +
                    "参与者: ${participant.identity}\n" +
                    "音频包数: $audioPacketCount\n" +
                    "总数据量: ${totalAudioBytes / 1024}KB\n" +
                    "采样率: ${sampleRate}Hz\n" +
                    "声道数: $numberOfChannels\n" +
                    "当前音量: ${String.format("%.3f", volume)}\n" +
                    "包时长: ${String.format("%.1f", duration)}ms"
                )
                lastUpdateTime = currentTime
            }

            // 5. 详细日志（每100个包记录一次）
            if (audioPacketCount % 100 == 0) {
                val logMessage = "[$mode] 处理音频包 #$audioPacketCount: " +
                        "${audioBytes.size} bytes, volume=$volume, " +
                        "$sampleRate Hz, $numberOfChannels channels"
                Log.d("RemoteAudioTest", logMessage)
                logTestResult(logMessage)
            }

        } catch (e: Exception) {
            Log.e("RemoteAudioTest", "Error processing remote audio", e)
            updateStatus("❌ 音频处理错误: ${e.message}")
        }
    }

    private fun calculateAudioVolume(audioBytes: ByteArray): Float {
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

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                updateStatus("✅ 已连接到房间")
                logTestResult("房间连接成功")
            }
            is RoomEvent.ParticipantConnected -> {
                updateStatus("👤 远程参与者已连接: ${event.participant.identity}")
                logTestResult("参与者连接: ${event.participant.identity}")

                // 如果已经开始处理，自动为新参与者设置处理
                if (isProcessingRemoteAudio) {
                    setupRemoteAudioProcessingWithPlayback(event.participant)
                }
            }
            is RoomEvent.ParticipantDisconnected -> {
                updateStatus("👋 远程参与者已断开: ${event.participant.identity}")
                logTestResult("参与者断开: ${event.participant.identity}")
            }
            is RoomEvent.TrackSubscribed -> {
                if (event.publication.track is RemoteAudioTrack) {
                    updateStatus("🎵 订阅了 ${event.participant.identity} 的音频轨道")
                    logTestResult("订阅音频轨道: ${event.participant.identity}")

                    // 如果已经开始处理，自动为新音频轨道设置处理
                    if (isProcessingRemoteAudio) {
                        setupRemoteAudioProcessingWithPlayback(event.participant)
                    }
                }
            }
            is RoomEvent.Disconnected -> {
                updateStatus("⚠️ 房间连接断开: ${event.reason}")
                logTestResult("房间连接断开: ${event.reason}")
            }
            else -> {
                // 处理其他事件
            }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
            Log.d("RemoteAudioTest", message)
        }
    }

    private fun updateAudioStats(stats: String) {
        runOnUiThread {
            audioStatsTextView.text = stats
        }
    }

    private fun logTestResult(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val logEntry = "[$timestamp] $message\n"

        try {
            testLogFile.appendText(logEntry)
        } catch (e: Exception) {
            Log.e("RemoteAudioTest", "Failed to write log", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRemoteAudioProcessing()
        pcmInputStream?.close()
    }
}
