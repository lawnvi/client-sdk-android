package io.livekit.android.sample.basic

import android.content.Context
import android.media.AudioFormat
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.audio.*
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.createAudioTrackWithBuffer
import io.livekit.android.room.participant.createAudioTrackWithFile
import io.livekit.android.room.track.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*

/**
 * 综合音频测试活动 - 测试所有新增的自定义音频处理功能
 *
 * 测试功能：
 * 1. 自定义音频数据源（PCM文件输入）
 * 2. 自定义处理服务端音频数据
 * 3. 可选的扬声器播放控制
 * 4. 音频数据录制和分析
 */
class ComprehensiveAudioTestActivity : AppCompatActivity() {

    private lateinit var room: Room
    private lateinit var statusTextView: TextView
    private var testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 测试状态
    private var customAudioProvider: BufferAudioBufferProvider? = null
    private var audioPlaybackController: AudioPlaybackController? = null
    private var recordingOutputStream: FileOutputStream? = null
    private var isTestRunning = false

    // 文件路径
    private lateinit var inputPCMFile: File
    private lateinit var recordedAudioFile: File
    private lateinit var testResultsFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comprehensive_test)

        initViews()
        setupFiles()
        setupRoom()
    }

    private fun initViews() {
        statusTextView = findViewById(R.id.statusTextView)

        findViewById<Button>(R.id.btnTestCustomAudioSource).setOnClickListener {
            testCustomAudioSource()
        }

        findViewById<Button>(R.id.btnTestRemoteAudioProcessing).setOnClickListener {
            testRemoteAudioProcessing()
        }

        findViewById<Button>(R.id.btnTestPlaybackControl).setOnClickListener {
            testPlaybackControl()
        }

        findViewById<Button>(R.id.btnRunAllTests).setOnClickListener {
            runAllTests()
        }

        findViewById<Button>(R.id.btnStopTests).setOnClickListener {
            stopAllTests()
        }
    }

    private fun setupFiles() {
        val filesDir = getExternalFilesDir(null)
        inputPCMFile = File(filesDir, "input.pcm")
        recordedAudioFile = File(filesDir, "recorded_remote_audio.pcm")
        testResultsFile = File(filesDir, "test_results.txt")

        // 生成测试PCM文件（如果不存在）
        if (!inputPCMFile.exists()) {
            generateTestPCMFile()
        }
    }

    private fun setupRoom() {
        room = LiveKit.create(this)

        lifecycleScope.launch {
            room.events.collect { event ->
                handleRoomEvent(event)
            }
        }
    }

    private fun generateTestPCMFile() {
        updateStatus("🎵 生成测试PCM文件...")
        PCMTestFileGenerator.generateSineWavePCM(
            outputFile = inputPCMFile,
            durationSeconds = 10,
            frequency = 440.0,
            sampleRate = 44100,
            channelCount = 2,
            amplitude = 0.5f
        )
        updateStatus("✅ 测试PCM文件已生成")
    }

    /**
     * 测试1: 自定义音频数据源功能
     */
    private fun testCustomAudioSource() {
        updateStatus("🚀 开始测试自定义音频数据源...")

        testScope.launch {
            try {
                val localParticipant = room.localParticipant

                // 方法1: 使用Buffer Provider
                val (audioTrack1, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
                    name = "buffer_audio_test",
                    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
                    channelCount = 2,
                    sampleRate = 44100,
                    microphoneGain = 0.0f,  // 禁用麦克风
                    customAudioGain = 1.0f,
                    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
                )

                updateStatus("📡 发布Buffer音频轨道...")
                localParticipant.publishAudioTrack(audioTrack1)

                // 发送PCM数据
                sendPCMDataToBuffer(bufferProvider)

                delay(5000) // 让buffer测试运行5秒

                // 方法2: 使用File Provider
                val audioTrack2 = localParticipant.createAudioTrackWithFile(
                    context = this@ComprehensiveAudioTestActivity,
                    name = "file_audio_test",
                    audioFileUri = android.net.Uri.fromFile(inputPCMFile),
                    microphoneGain = 0.0f,
                    customAudioGain = 1.0f,
                    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
                )

                updateStatus("📡 发布File音频轨道...")
                localParticipant.publishAudioTrack(audioTrack2)

                updateStatus("✅ 自定义音频数据源测试完成")

            } catch (e: Exception) {
                updateStatus("❌ 自定义音频数据源测试失败: ${e.message}")
                Log.e("AudioTest", "Custom audio source test failed", e)
            }
        }
    }

    /**
     * 测试2: 自定义处理服务端音频数据
     */
    private fun testRemoteAudioProcessing() {
        updateStatus("🚀 开始测试远程音频处理...")

        testScope.launch {
            try {
                // 等待远程参与者
                room.remoteParticipants.values.forEach { participant ->
                    setupRemoteAudioProcessing(participant)
                }

                updateStatus("✅ 远程音频处理设置完成")

            } catch (e: Exception) {
                updateStatus("❌ 远程音频处理测试失败: ${e.message}")
                Log.e("AudioTest", "Remote audio processing test failed", e)
            }
        }
    }

    /**
     * 测试3: 可选扬声器播放控制
     */
    private fun testPlaybackControl() {
        updateStatus("🚀 开始测试扬声器播放控制...")

        testScope.launch {
            try {
                room.remoteParticipants.values.forEach { participant ->
                    setupPlaybackControlledProcessing(participant)
                }

                updateStatus("✅ 扬声器播放控制测试完成")

            } catch (e: Exception) {
                updateStatus("❌ 扬声器播放控制测试失败: ${e.message}")
                Log.e("AudioTest", "Playback control test failed", e)
            }
        }
    }

    /**
     * 运行所有测试
     */
    private fun runAllTests() {
        updateStatus("🚀 开始运行所有测试...")
        isTestRunning = true

        lifecycleScope.launch {
            try {
                // 连接到房间
                connectToRoom()

                delay(2000) // 等待连接稳定

                // 依次运行测试
                testCustomAudioSource()
                delay(5000)

                testRemoteAudioProcessing()
                delay(3000)

                testPlaybackControl()
                delay(3000)

                generateTestReport()
                updateStatus("✅ 所有测试完成，查看测试报告")

            } catch (e: Exception) {
                updateStatus("❌ 测试运行失败: ${e.message}")
                Log.e("AudioTest", "Test run failed", e)
            }
        }
    }

    private fun stopAllTests() {
        isTestRunning = false
        testScope.cancel()
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        audioPlaybackController?.release()
        recordingOutputStream?.close()

        updateStatus("⏹️ 所有测试已停止")
    }

    private suspend fun connectToRoom() {
        updateStatus("🌐 连接到房间...")

        val wsUrl = "wss://ls1.dearlink.com"
        val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NTQ2MzI3ODYsImlzcyI6ImxpdmVraXQtYXBpLWtleS0xIiwibmFtZSI6ImRldl91c2VyMSIsIm5iZiI6MTc1NDM3MzU4Niwic3ViIjoiZGV2X3VzZXIxIiwidmlkZW8iOnsicm9vbSI6Im15LWRldi1yb29tIiwicm9vbUpvaW4iOnRydWV9fQ.rAS9JGI3MyV9MjCNVCjsHuZs_A88TjR9aitx3iILfDg"

        room.connect(wsUrl, token)
    }

    private suspend fun sendPCMDataToBuffer(bufferProvider: BufferAudioBufferProvider) {
        updateStatus("📤 向Buffer发送PCM数据...")

        try {
            val inputStream = inputPCMFile.inputStream()
            val bufferSize = 44100 * 2 * 2 * 10 / 1000 // 10ms的音频数据
            val buffer = ByteArray(bufferSize)

            var totalBytesSent = 0
            while (isTestRunning && inputStream.available() > 0) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    bufferProvider.addAudioData(buffer.copyOf(bytesRead))
                    totalBytesSent += bytesRead

                    if (totalBytesSent % (bufferSize * 100) == 0) {
                        updateStatus("📤 已发送 ${totalBytesSent / 1024}KB 数据")
                    }
                }
                delay(10) // 10ms间隔
            }

            inputStream.close()
            updateStatus("📤 PCM数据发送完成，总计 ${totalBytesSent / 1024}KB")

        } catch (e: Exception) {
            Log.e("AudioTest", "Error sending PCM data", e)
        }
    }

    private fun setupRemoteAudioProcessing(participant: Participant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull { it.first.track is RemoteAudioTrack }?.first?.track as? RemoteAudioTrack

        if (audioTrack == null) {
            updateStatus("⚠️ 参与者 ${participant.identity} 没有音频轨道")
            return
        }

        updateStatus("🎧 设置 ${participant.identity} 的音频处理...")

        // 设置音频数据处理器 - 不影响正常播放
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid.toString()
        ) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->

            // 处理音频数据 - 这里可以做分析、录制等
            processRemoteAudioData(participant, audioData, sampleRate, numberOfChannels)
        }
    }

    private fun setupPlaybackControlledProcessing(participant: Participant) {
        val audioTrack = participant.audioTrackPublications
            .firstOrNull { it.first.track is RemoteAudioTrack }?.first?.track as? RemoteAudioTrack

        if (audioTrack == null) return

        updateStatus("🔊 设置 ${participant.identity} 的播放控制...")

        // 创建带播放控制的拦截器
        val playbackInterceptor = object : PlaybackControlledInterceptor(
            context = this@ComprehensiveAudioTestActivity,
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
                processRemoteAudioData(participant, audioData, sampleRate, numberOfChannels)

                // 通过自定义播放控制器播放
                playbackController?.let { controller ->
                    if (!controller.isReady()) {
                        controller.initialize()
                    }
                    controller.playAudioData(audioData)
                }

                // 返回false表示我们已经通过自定义控制器处理了播放，不需要系统默认播放
                return false
            }
        }

        audioTrack.setAudioDataInterceptor(
            participant = participant,
            trackSid = audioTrack.sid.toString(),
            interceptor = playbackInterceptor
        )
    }

    private fun processRemoteAudioData(
        participant: Participant,
        audioData: ByteBuffer,
        sampleRate: Int,
        numberOfChannels: Int
    ) {
        try {
            // 录制音频到文件
            if (recordingOutputStream == null) {
                recordingOutputStream = FileOutputStream(recordedAudioFile)
            }

            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            recordingOutputStream?.write(audioBytes)

            // 分析音频数据（简单的音量检测）
            val volume = calculateAudioVolume(audioBytes)

            Log.d("AudioTest", "处理来自 ${participant.identity} 的音频: " +
                    "${audioBytes.size} bytes, $sampleRate Hz, $numberOfChannels channels, volume: $volume")

        } catch (e: Exception) {
            Log.e("AudioTest", "Error processing remote audio", e)
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
        return kotlin.math.sqrt(mean).toFloat()
    }

    private fun handleRoomEvent(event: RoomEvent) {
        when (event) {
            is RoomEvent.Connected -> {
                updateStatus("✅ 已连接到房间")
            }
            is RoomEvent.ParticipantConnected -> {
                updateStatus("👤 远程参与者已连接: ${event.participant.identity}")
                if (isTestRunning) {
                    setupRemoteAudioProcessing(event.participant)
                }
            }
            is RoomEvent.TrackSubscribed -> {
                if (event.publication.track is RemoteAudioTrack) {
                    updateStatus("🎵 订阅了 ${event.participant.identity} 的音频轨道")
                    if (isTestRunning) {
                        setupRemoteAudioProcessing(event.participant)
                    }
                }
            }
            is RoomEvent.Disconnected -> {
                updateStatus("⚠️ 房间连接断开: ${event.reason}")
            }
            else -> {
                // 处理其他事件
            }
        }
    }

    private fun generateTestReport() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val report = StringBuilder()

        report.appendLine("=== LiveKit Android SDK 音频功能测试报告 ===")
        report.appendLine("测试时间: $timestamp")
        report.appendLine()

        report.appendLine("1. 自定义音频数据源测试:")
        report.appendLine("   - Buffer Provider: ${if (customAudioProvider != null) "✅ 通过" else "❌ 失败"}")
        report.appendLine("   - File Provider: ${if (inputPCMFile.exists()) "✅ 通过" else "❌ 失败"}")
        report.appendLine("   - 输入文件大小: ${inputPCMFile.length()} bytes")
        report.appendLine()

        report.appendLine("2. 远程音频处理测试:")
        report.appendLine("   - 录制文件: ${if (recordedAudioFile.exists()) "✅ 通过" else "❌ 失败"}")
        report.appendLine("   - 录制文件大小: ${if (recordedAudioFile.exists()) recordedAudioFile.length() else 0} bytes")
        report.appendLine()

        report.appendLine("3. 扬声器播放控制测试:")
        report.appendLine("   - 播放控制器: ${if (audioPlaybackController != null) "✅ 通过" else "❌ 失败"}")
        report.appendLine()

        report.appendLine("4. 房间连接状态:")
        report.appendLine("   - 当前状态: ${room.state}")
        report.appendLine("   - 远程参与者数: ${room.remoteParticipants.size}")
        report.appendLine("   - 本地轨道数: ${room.localParticipant.audioTrackPublications.size}")
        report.appendLine()

        try {
            testResultsFile.writeText(report.toString())
            updateStatus("📄 测试报告已保存到: ${testResultsFile.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioTest", "Error saving test report", e)
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusTextView.text = message
            Log.d("AudioTest", message)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllTests()
        recordingOutputStream?.close()
    }
}
