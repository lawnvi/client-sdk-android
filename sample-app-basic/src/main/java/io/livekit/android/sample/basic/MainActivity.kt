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

package io.livekit.android.sample.basic

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.audio.CustomAudioInputCallback
import io.livekit.android.audio.CustomAudioInputSource
import io.livekit.android.audio.setCustomPcmFileInput
import io.livekit.android.audio.setCustomStreamInput
import io.livekit.android.audio.clearCustomAudioInput
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.util.LKLog
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private var room: Room? = null
    private var audioTrack: LocalAudioTrack? = null
    private var currentAudioCallback: CustomAudioInputCallback? = null
    private var isMicrophoneEnabled = false
    private var selectedPcmFilePath: String? = null

    // UI Views
    private lateinit var urlEditText: android.widget.EditText
    private lateinit var tokenEditText: android.widget.EditText
    private lateinit var connectButton: android.widget.Button
    private lateinit var statusTextView: android.widget.TextView
    private lateinit var audioSourceSpinner: android.widget.Spinner
    private lateinit var replaceMicRadio: android.widget.RadioButton
    private lateinit var mixWithMicRadio: android.widget.RadioButton
    private lateinit var startCustomAudioButton: android.widget.Button
    private lateinit var stopCustomAudioButton: android.widget.Button
    private lateinit var toggleMicButton: android.widget.Button
    private lateinit var audioStatusTextView: android.widget.TextView
    private lateinit var selectPcmFileButton: android.widget.Button

    // 音频源选项
    private enum class AudioSource(val displayName: String) {
        MICROPHONE("麦克风"),
        PCM_FILE("PCM 文件"),
        SINE_WAVE("正弦波")
    }

    // 文件选择器
    private val selectPcmFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                // 获取文件路径
//                val filePath = getFilePathFromUri(uri)
                val filePath = "${baseContext.getExternalFilesDir(null)?.path}/input.pcm"
                if (filePath != null) {
                    selectedPcmFilePath = filePath
                    audioStatusTextView.text = "已选择 PCM 文件: ${File(filePath).name}"
                    Toast.makeText(this, "已选择 PCM 文件", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "无法访问所选文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupUI()
    }

    private fun initViews() {
        urlEditText = findViewById(R.id.urlEditText)
        tokenEditText = findViewById(R.id.tokenEditText)
        connectButton = findViewById(R.id.connectButton)
        statusTextView = findViewById(R.id.statusTextView)
        audioSourceSpinner = findViewById(R.id.audioSourceSpinner)
        replaceMicRadio = findViewById(R.id.replaceMicRadio)
        mixWithMicRadio = findViewById(R.id.mixWithMicRadio)
        startCustomAudioButton = findViewById(R.id.startCustomAudioButton)
        stopCustomAudioButton = findViewById(R.id.stopCustomAudioButton)
        toggleMicButton = findViewById(R.id.toggleMicButton)
        audioStatusTextView = findViewById(R.id.audioStatusTextView)
        selectPcmFileButton = findViewById(R.id.selectPcmFileButton)
    }

    private fun setupUI() {
        // 设置音频源下拉列表
        val audioSources = AudioSource.values().map { it.displayName }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, audioSources)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audioSourceSpinner.adapter = adapter

        // 设置按钮点击事件
        connectButton.setOnClickListener {
            if (room?.state == Room.State.DISCONNECTED) {
                disconnectFromRoom()
            } else {
                connectToRoom()
            }
        }

        startCustomAudioButton.setOnClickListener {
            startCustomAudio()
        }

        stopCustomAudioButton.setOnClickListener {
            stopCustomAudio()
        }

        toggleMicButton.setOnClickListener {
            toggleMicrophone()
        }

        selectPcmFileButton.setOnClickListener {
            selectPcmFile()
        }

        updateAudioStatus("当前音频源: 麦克风")
    }

    private fun selectPcmFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"  // 允许选择任何文件类型
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        selectPcmFileLauncher.launch(intent)
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            // 尝试获取真实文件路径
            val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val columnIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    it.getString(columnIndex)
                } else {
                    null
                }
            } ?: uri.path
        } catch (e: Exception) {
            LKLog.w(e) { "Failed to get file path from URI" }
            uri.path
        }
    }

    private fun connectToRoom() {
        val url = urlEditText.text.toString().trim()
        val token = tokenEditText.text.toString().trim()

        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "请输入 URL 和 Token", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                statusTextView.text = "连接中..."
                connectButton.isEnabled = false

                room = LiveKit.create(
                    appContext = applicationContext,
                )

                room?.connect(url, token)

                // 创建并发布音频轨道
                audioTrack = room?.localParticipant?.createAudioTrack()
                audioTrack?.let {
                    room?.localParticipant?.publishAudioTrack(it)
                }

                statusTextView.text = "已连接"
                connectButton.text = "断开连接"
                connectButton.isEnabled = true

                LKLog.d { "Successfully connected to room" }

            } catch (e: Exception) {
                LKLog.e(e) { "Failed to connect to room" }
                statusTextView.text = "连接失败: ${e.message}"
                connectButton.isEnabled = true
                Toast.makeText(this@MainActivity, "连接失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disconnectFromRoom() {
        lifecycleScope.launch {
            try {
                stopCustomAudio()
                audioTrack = null
                room?.disconnect()
                room = null

                statusTextView.text = "已断开连接"
                connectButton.text = "连接"
                connectButton.isEnabled = true

                LKLog.d { "Disconnected from room" }

            } catch (e: Exception) {
                LKLog.e(e) { "Error during disconnect" }
            }
        }
    }

    private fun startCustomAudio() {
        val currentTrack = audioTrack
        if (currentTrack == null) {
            Toast.makeText(this, "请先连接到房间", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // 停止之前的自定义音频
            stopCustomAudio()

            val selectedSource = AudioSource.values()[audioSourceSpinner.selectedItemPosition]
            val replaceOriginal = replaceMicRadio.isChecked

            when (selectedSource) {
                AudioSource.MICROPHONE -> {
                    // 使用麦克风，清除自定义音频输入
                    currentTrack.clearCustomAudioInput()
                    updateAudioStatus("当前音频源: 麦克风")
                }

                AudioSource.PCM_FILE -> {
                    if (selectedPcmFilePath == null) {
                        Toast.makeText(this, "请先选择 PCM 文件", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val audioConfig = CustomAudioInputSource.AudioConfig(
                        sampleRate = 48000,
                        channelCount = 1,
                        audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    )

                    currentAudioCallback = currentTrack.setCustomPcmFileInput(
                        filePath = selectedPcmFilePath!!,
                        audioConfig = audioConfig,
                        replaceOriginal = replaceOriginal,
                        enableLooping = true
                    )

                    currentAudioCallback?.start()
                    updateAudioStatus("当前音频源: PCM 文件 (${if (replaceOriginal) "替换" else "混合"})")
                }

                AudioSource.SINE_WAVE -> {
                    val audioConfig = CustomAudioInputSource.AudioConfig(
                        sampleRate = 48000,
                        channelCount = 1,
                        audioFormat = AudioFormat.ENCODING_PCM_16BIT
                    )

                    // 生成 3 秒的正弦波音频数据 (523Hz - C5 音符)
                    val frequency = 523.0
                    val durationSeconds = 3
                    val sampleRate = audioConfig.sampleRate
                    val numSamples = sampleRate * durationSeconds
                    val amplitude = 16384  // 50% 音量

                    val audioData = ByteArray(numSamples * 2)  // 16-bit = 2 bytes per sample

                    for (i in 0 until numSamples) {
                        val sample = (amplitude * sin(2.0 * Math.PI * frequency * i / sampleRate)).toInt().toShort()
                        audioData[i * 2] = (sample.toInt() and 0xFF).toByte()         // 低字节
                        audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()  // 高字节
                    }

                    currentAudioCallback = currentTrack.setCustomStreamInput(
                        inputStream = ByteArrayInputStream(audioData),
                        audioConfig = audioConfig,
                        replaceOriginal = replaceOriginal,
                        enableLooping = true
                    )

                    currentAudioCallback?.start()
                    updateAudioStatus("当前音频源: 正弦波 (${if (replaceOriginal) "替换" else "混合"})")
                }
            }

            startCustomAudioButton.isEnabled = false
            stopCustomAudioButton.isEnabled = true

        } catch (e: Exception) {
            LKLog.e(e) { "Failed to start custom audio" }
            Toast.makeText(this, "启动自定义音频失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopCustomAudio() {
        currentAudioCallback?.close()
        currentAudioCallback = null
        audioTrack?.clearCustomAudioInput()

        startCustomAudioButton.isEnabled = true
        stopCustomAudioButton.isEnabled = false

        updateAudioStatus("当前音频源: 麦克风")
    }

    private fun toggleMicrophone() {
        lifecycleScope.launch {
            try {
                if (isMicrophoneEnabled) {
                    room?.localParticipant?.setMicrophoneEnabled(false)
                    toggleMicButton.text = "启用麦克风"
                    isMicrophoneEnabled = false
                } else {
                    room?.localParticipant?.setMicrophoneEnabled(true)
                    toggleMicButton.text = "禁用麦克风"
                    isMicrophoneEnabled = true
                }
            } catch (e: Exception) {
                LKLog.e(e) { "Failed to toggle microphone" }
                Toast.makeText(this@MainActivity, "麦克风操作失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateAudioStatus(status: String) {
        audioStatusTextView.text = status
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCustomAudio()
        lifecycleScope.launch {
            room?.disconnect()
        }
    }
}
