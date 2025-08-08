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
import io.livekit.android.room.participant.createEnhancedCustomAudioTrack
import io.livekit.android.room.participant.createSineWaveTestTrack
import io.livekit.android.room.participant.createBeepTestTrack
import io.livekit.android.room.participant.CustomAudioTrackManager
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.RemoteAudioTrack
import io.livekit.android.room.track.clearAudioDataInterceptor
import io.livekit.android.room.track.setAudioDataProcessor
import io.livekit.android.room.track.muteWithProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 增强版自定义音频测试活动
 *
 * 使用新的EnhancedCustomAudioMixer来解决自定义音频输入问题
 */
class EnhancedCustomAudioTestActivity : AppCompatActivity() {

    private lateinit var room: Room
    private lateinit var statusTextView: TextView
    private lateinit var audioStatsTextView: TextView

    // 测试状态
    private var isProcessingRemoteAudio = false
    private var recordingOutputStream: FileOutputStream? = null
    private var audioPacketCount = 0
    private var totalAudioBytes = 0L
    private var lastUpdateTime = System.currentTimeMillis()

    // 增强版自定义音频组件
    private var customAudioTrackManager = CustomAudioTrackManager()
    private var currentTestTrack: io.livekit.android.room.participant.TestAudioTrackResult? = null
    private var enhancedMixer: EnhancedCustomAudioMixer? = null
    private var customAudioMonitor: CustomAudioDebugUtils.CustomAudioProviderMonitor? = null

    // PCM文件相关
    private var inputPCMFile: File? = null
    private var pcmInputStream: FileInputStream? = null
    private var pcmFilePosition = 0L
    private var totalPCMPacketsSent = 0

    // 文件路径
    private lateinit var recordedAudioFile: File
    private lateinit var testLogFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_custom_audio_test)

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

        findViewById<Button>(R.id.btnCreateSineWave).setOnClickListener {
            createSineWaveTest()
        }

        findViewById<Button>(R.id.btnCreateBeepSequence).setOnClickListener {
            createBeepSequenceTest()
        }

        findViewById<Button>(R.id.btnCreateWhiteNoise).setOnClickListener {
            createWhiteNoiseTest()
        }

        findViewById<Button>(R.id.btnCreatePCMFromFile).setOnClickListener {
            createPCMFromFileTest()
        }

        findViewById<Button>(R.id.btnStopCustomAudio).setOnClickListener {
            stopCustomAudio()
        }

        findViewById<Button>(R.id.btnSwitchAudioMode).setOnClickListener {
            switchAudioMode()
        }

        findViewById<Button>(R.id.btnShowDebugInfo).setOnClickListener {
            showDebugInfo()
        }

        findViewById<Button>(R.id.btnStartRemoteProcessing).setOnClickListener {
            startRemoteAudioProcessingWithPlayback()
        }

        findViewById<Button>(R.id.btnStopRemoteProcessing).setOnClickListener {
            stopRemoteAudioProcessing()
        }

        updateStatus("🚀 增强版自定义音频测试准备就绪")
        updateAudioStats("等待音频数据...")
    }

    private fun setupFiles() {
        val filesDir = getExternalFilesDir(null)
        recordedAudioFile = File(filesDir, "enhanced_audio_${System.currentTimeMillis()}.pcm")
        testLogFile = File(filesDir, "enhanced_audio_test_log.txt")

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
            sampleRate = 48000,
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
                Log.e("EnhancedCustomAudioTest", "Connection failed", e)
            }
        }
    }

    /**
     * 创建正弦波测试音频
     */
    private fun createSineWaveTest() {
        lifecycleScope.launch {
            try {
                updateStatus("🎵 创建正弦波测试音频...")

                if (room.state != Room.State.CONNECTED) {
                    updateStatus("❌ 房间未连接")
                    return@launch
                }

                // 停止之前的音频
                stopCustomAudio()

                // 创建正弦波测试轨道
                val testTrack = room.localParticipant.createSineWaveTestTrack(
                    frequency = 440.0, // A4音符
                    amplitude = 0.5
                )

                // 发布音频轨道
                room.localParticipant.publishAudioTrack(testTrack.audioTrack)

                // 保存引用
                currentTestTrack = testTrack
                enhancedMixer = testTrack.mixer
                customAudioMonitor = testTrack.monitor
                customAudioTrackManager.addTrack("sine-wave", testTrack)

                updateStatus("✅ 正弦波测试音频创建成功")
                logTestResult("正弦波测试音频创建: 440Hz, 0.5振幅")

                // 显示状态信息
                showDebugInfo()

            } catch (e: Exception) {
                updateStatus("❌ 创建正弦波测试失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "Sine wave test failed", e)
                logTestResult("正弦波测试失败: ${e.message}")
            }
        }
    }

    /**
     * 创建哔哔声序列测试音频
     */
    private fun createBeepSequenceTest() {
        lifecycleScope.launch {
            try {
                updateStatus("🔔 创建哔哔声序列测试...")

                if (room.state != Room.State.CONNECTED) {
                    updateStatus("❌ 房间未连接")
                    return@launch
                }

                stopCustomAudio()

                val testTrack = room.localParticipant.createBeepTestTrack(
                    frequency = 800.0, // 较高频率
                    amplitude = 0.3     // 较低音量
                )

                room.localParticipant.publishAudioTrack(testTrack.audioTrack)

                currentTestTrack = testTrack
                enhancedMixer = testTrack.mixer
                customAudioMonitor = testTrack.monitor
                customAudioTrackManager.addTrack("beep-sequence", testTrack)

                updateStatus("✅ 哔哔声序列测试创建成功")
                logTestResult("哔哔声序列测试创建: 800Hz, 0.3振幅")

                showDebugInfo()

            } catch (e: Exception) {
                updateStatus("❌ 创建哔哔声测试失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "Beep test failed", e)
            }
        }
    }

    /**
     * 创建白噪声测试音频
     */
    private fun createWhiteNoiseTest() {
        lifecycleScope.launch {
            try {
                updateStatus("🌪️ 创建白噪声测试...")

                if (room.state != Room.State.CONNECTED) {
                    updateStatus("❌ 房间未连接")
                    return@launch
                }

                stopCustomAudio()

                // 创建白噪声提供器
                val whiteNoiseProvider = TestAudioProvider(
                    testType = TestAudioProvider.TestSignalType.WHITE_NOISE,
                    amplitude = 0.1, // 低音量白噪声
                    durationSeconds = 0.0 // 无限时长
                )

                val (audioTrack, mixer, monitor) = room.localParticipant.createEnhancedCustomAudioTrack(
                    name = "white-noise-test",
                    customAudioProvider = whiteNoiseProvider,
                    enableDebug = true
                )

                room.localParticipant.publishAudioTrack(audioTrack)

                // 创建TestAudioTrackResult
                val testTrack = io.livekit.android.room.participant.TestAudioTrackResult(
                    audioTrack = audioTrack,
                    testProvider = whiteNoiseProvider,
                    mixer = mixer,
                    monitor = monitor
                )

                currentTestTrack = testTrack
                enhancedMixer = mixer
                customAudioMonitor = monitor
                customAudioTrackManager.addTrack("white-noise", testTrack)

                updateStatus("✅ 白噪声测试创建成功")
                logTestResult("白噪声测试创建: 0.1振幅")

                showDebugInfo()

            } catch (e: Exception) {
                updateStatus("❌ 创建白噪声测试失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "White noise test failed", e)
            }
        }
    }

    /**
     * 从PCM文件创建自定义音频
     */
    private fun createPCMFromFileTest() {
        lifecycleScope.launch {
            try {
                updateStatus("📁 从PCM文件创建自定义音频...")

                val file = inputPCMFile
                if (file == null || !file.exists()) {
                    updateStatus("❌ PCM文件不存在")
                    return@launch
                }

                if (room.state != Room.State.CONNECTED) {
                    updateStatus("❌ 房间未连接")
                    return@launch
                }

                stopCustomAudio()

                // 创建基于文件的音频提供器
                val fileBasedProvider = object : CustomAudioBufferProvider {
                    private var isRunning = false
                    private var fileInputStream: FileInputStream? = null
                    private var filePosition = 0L
                    private val totalFileSize = file.length()

                    override fun start() {
                        isRunning = true
                        fileInputStream?.close()
                        fileInputStream = FileInputStream(file)
                        filePosition = 0L
                        Log.d("PCMFileProvider", "开始从文件读取: ${file.absolutePath}")
                    }

                    override fun stop() {
                        isRunning = false
                        fileInputStream?.close()
                        fileInputStream = null
                        Log.d("PCMFileProvider", "停止文件读取")
                    }

                    override fun hasMoreData(): Boolean = isRunning && filePosition < totalFileSize

                    override fun getCaptureTimeNs(): Long = System.nanoTime()

                    override fun provideAudioData(
                        requestedBytes: Int,
                        audioFormat: Int,
                        channelCount: Int,
                        sampleRate: Int
                    ): ByteBuffer? {
                        if (!isRunning || fileInputStream == null) return null

                        try {
                            val buffer = ByteArray(requestedBytes)
                            val bytesRead = fileInputStream!!.read(buffer)

                            if (bytesRead > 0) {
                                filePosition += bytesRead
                                val result = if (bytesRead < requestedBytes) {
                                    ByteBuffer.wrap(buffer.copyOf(bytesRead))
                                } else {
                                    ByteBuffer.wrap(buffer)
                                }
                                return result
                            } else {
                                // 文件结束，重新开始
                                fileInputStream!!.channel.position(0)
                                filePosition = 0L
                                return provideAudioData(requestedBytes, audioFormat, channelCount, sampleRate)
                            }
                        } catch (e: Exception) {
                            Log.e("PCMFileProvider", "读取文件错误", e)
                            return null
                        }
                    }
                }

                // 使用增强版混音器
                val (audioTrack, mixer, monitor) = room.localParticipant.createEnhancedCustomAudioTrack(
                    name = "pcm-file-test",
                    customAudioProvider = fileBasedProvider,
                    enableDebug = true
                )

                room.localParticipant.publishAudioTrack(audioTrack)

                // 创建TestAudioTrackResult包装器
                val testTrack = io.livekit.android.room.participant.TestAudioTrackResult(
                    audioTrack = audioTrack,
                    testProvider = TestAudioProvider(), // 占位符
                    mixer = mixer,
                    monitor = monitor
                )

                currentTestTrack = testTrack
                enhancedMixer = mixer
                customAudioMonitor = monitor
                customAudioTrackManager.addTrack("pcm-file", testTrack)

                updateStatus("✅ PCM文件音频创建成功: ${file.length() / 1024}KB")
                logTestResult("PCM文件音频创建: ${file.absolutePath}")

                showDebugInfo()

            } catch (e: Exception) {
                updateStatus("❌ 创建PCM文件音频失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "PCM file test failed", e)
            }
        }
    }

    /**
     * 停止当前自定义音频
     */
    private fun stopCustomAudio() {
        try {
            currentTestTrack?.stop()
            customAudioTrackManager.stopAllTracks()

            currentTestTrack = null
            enhancedMixer = null
            customAudioMonitor = null

            updateStatus("⏹️ 自定义音频已停止")
            logTestResult("停止自定义音频")

        } catch (e: Exception) {
            Log.e("EnhancedCustomAudioTest", "Stop custom audio failed", e)
        }
    }

    /**
     * 切换音频模式演示
     */
    private fun switchAudioMode() {
        lifecycleScope.launch {
            try {
                updateStatus("🔄 开始音频模式切换演示...")

                // 每种模式运行10秒
                val modes = listOf(
                    "sine-wave" to { createSineWaveTest() },
                    "beep" to { createBeepSequenceTest() },
                    "white-noise" to { createWhiteNoiseTest() }
                )

                for ((modeName, createFunction) in modes) {
                    updateStatus("🎵 切换到 $modeName 模式")
                    createFunction()
                    delay(10_000) // 运行10秒
                }

                stopCustomAudio()
                updateStatus("✅ 音频模式切换演示完成")

            } catch (e: Exception) {
                updateStatus("❌ 音频模式切换失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "Audio mode switch failed", e)
            }
        }
    }

    /**
     * 显示详细调试信息
     */
    private fun showDebugInfo() {
        lifecycleScope.launch {
            try {
                val debugInfo = StringBuilder()

                // 混音器状态
                enhancedMixer?.let { mixer ->
                    debugInfo.append("=== 增强版混音器状态 ===\n")
                    debugInfo.append(mixer.getStatusInfo())
                    debugInfo.append("\n\n")
                }

                // 测试提供器状态
                currentTestTrack?.let { track ->
                    if (track.testProvider != null) {
                        debugInfo.append("=== 测试音频提供器状态 ===\n")
                        debugInfo.append(track.testProvider.getGeneratorInfo())
                        debugInfo.append("\n\n")
                    }
                }

                // 轨道管理器状态
                debugInfo.append("=== 轨道管理器状态 ===\n")
                debugInfo.append(customAudioTrackManager.getAllTracksStatus())
                debugInfo.append("\n\n")

                // 系统音频信息
                debugInfo.append("=== 系统音频信息 ===\n")
                debugInfo.append(CustomAudioDebugUtils.checkSystemAudioInfo())

                updateAudioStats(debugInfo.toString())
                logTestResult("显示调试信息:\n$debugInfo")

            } catch (e: Exception) {
                updateStatus("❌ 获取调试信息失败: ${e.message}")
                Log.e("EnhancedCustomAudioTest", "Show debug info failed", e)
            }
        }
    }

    /**
     * 开始远程音频处理（保持原有功能）
     */
    private fun startRemoteAudioProcessingWithPlayback() {
        updateStatus("🎧 开始远程音频处理（带扬声器播放）...")

        room.remoteParticipants.values.forEach { participant ->
            setupRemoteAudioProcessingWithPlayback(participant)
        }

        if (room.remoteParticipants.isEmpty()) {
            updateStatus("⚠️ 没有远程参与者，等待其他参与者加入...")
        }
    }

    private fun setupRemoteAudioProcessingWithPlayback(participant: Participant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull { it.first.track is RemoteAudioTrack }?.first?.track as? RemoteAudioTrack

        if (audioTrack == null) {
            updateStatus("⚠️ 参与者 ${participant.identity} 没有音频轨道")
            return
        }

        updateStatus("🎧 设置 ${participant.identity} 的音频处理...")

        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
            processRemoteAudioData(participant, audioData, sampleRate, numberOfChannels)
        }

        isProcessingRemoteAudio = true
        logTestResult("开始处理 ${participant.identity} 的音频（带扬声器播放）")
    }

    private fun stopRemoteAudioProcessing() {
        isProcessingRemoteAudio = false
        recordingOutputStream?.close()
        recordingOutputStream = null

        room.remoteParticipants.values.forEach { participant ->
            participant.audioTrackPublications.forEach { (_, publication) ->
                val audioTrack = publication as? RemoteAudioTrack
                audioTrack?.clearAudioDataInterceptor()
            }
        }

        updateStatus("⏹️ 远程音频处理已停止")
        logTestResult("远程音频处理停止")
    }

    private fun processRemoteAudioData(
        participant: Participant,
        audioData: ByteBuffer,
        sampleRate: Int,
        numberOfChannels: Int
    ) {
        try {
            if (recordingOutputStream == null) {
                recordingOutputStream = FileOutputStream(recordedAudioFile)
            }

            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            recordingOutputStream?.write(audioBytes)

            val volume = calculateAudioVolume(audioBytes)
            audioPacketCount++
            totalAudioBytes += audioBytes.size

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime > 500) {
                updateAudioStats(
                    "远程音频处理:\n" +
                    "参与者: ${participant.identity}\n" +
                    "音频包数: $audioPacketCount\n" +
                    "总数据量: ${totalAudioBytes / 1024}KB\n" +
                    "采样率: ${sampleRate}Hz\n" +
                    "声道数: $numberOfChannels\n" +
                    "当前音量: ${String.format("%.3f", volume)}\n" +
                    "录制文件: ${recordedAudioFile.length() / 1024}KB"
                )
                lastUpdateTime = currentTime
            }

        } catch (e: Exception) {
            Log.e("EnhancedCustomAudioTest", "Error processing remote audio", e)
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
            }
            is RoomEvent.ParticipantDisconnected -> {
                updateStatus("👋 远程参与者已断开: ${event.participant.identity}")
                logTestResult("参与者断开: ${event.participant.identity}")
            }
            is RoomEvent.TrackSubscribed -> {
                if (event.publication.track is RemoteAudioTrack) {
                    updateStatus("🎵 订阅了 ${event.participant.identity} 的音频轨道")
                    logTestResult("订阅音频轨道: ${event.participant.identity}")
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
            Log.d("EnhancedCustomAudioTest", message)
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
            Log.e("EnhancedCustomAudioTest", "Failed to write log", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCustomAudio()
        stopRemoteAudioProcessing()
        pcmInputStream?.close()
        customAudioTrackManager.stopAllTracks()
    }
}
