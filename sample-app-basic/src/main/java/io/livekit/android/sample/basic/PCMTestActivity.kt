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
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.examples.DebugPCMExample
import io.livekit.android.room.Room
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class PCMTestActivity : AppCompatActivity() {
    
    private lateinit var room: Room
    private lateinit var debugExample: DebugPCMExample
    
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var createTestFileButton: Button
    
    private val requestPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            var hasDenied = false
            for (grant in grants.entries) {
                if (!grant.value) {
                    Toast.makeText(this, "Missing permission: ${grant.key}", Toast.LENGTH_SHORT).show()
                    hasDenied = true
                }
            }
            
            if (!hasDenied) {
                initializeAudio()
            }
        }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pcm_test)
        
        initViews()
        requestNeededPermissions()
    }
    
    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        createTestFileButton = findViewById(R.id.createTestFileButton)
        
        startButton.setOnClickListener { startTest() }
        stopButton.setOnClickListener { stopTest() }
        createTestFileButton.setOnClickListener { createTestFile() }
        
        // 初始状态
        startButton.isEnabled = false
        stopButton.isEnabled = false
        updateStatus("等待权限...")
    }
    
    private fun requestNeededPermissions() {
        val neededPermissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        ).filter { 
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED 
        }.toTypedArray()
        
        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        } else {
            initializeAudio()
        }
    }
    
    private fun initializeAudio() {
        try {
            room = LiveKit.create(applicationContext)
            debugExample = DebugPCMExample(this, room)
            
            startButton.isEnabled = true
            createTestFileButton.isEnabled = true
            updateStatus("准备就绪，点击'创建测试文件'然后'开始测试'")
            
            Log.d("PCMTest", "音频初始化完成")
            
        } catch (e: Exception) {
            Log.e("PCMTest", "音频初始化失败", e)
            updateStatus("初始化失败: ${e.message}")
        }
    }
    
    private fun createTestFile() {
        try {
            val testFile = File(filesDir, "test_input.pcm")
            debugExample.createTestFile(testFile, durationSeconds = 10)
            
            updateStatus("测试文件已创建: ${testFile.absolutePath}\n文件大小: ${testFile.length()} 字节")
            Toast.makeText(this, "测试文件创建成功", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e("PCMTest", "创建测试文件失败", e)
            updateStatus("创建测试文件失败: ${e.message}")
        }
    }
    
    private fun startTest() {
        lifecycleScope.launch {
            try {
                updateStatus("连接房间...")
                
                // 使用您的LiveKit服务器URL和Token
                val url = "wss://your-livekit-server.com"  // 替换为您的服务器
                val token = "your-token"  // 替换为您的token
                
                // 连接到房间
                room.connect(url, token)
                updateStatus("房间连接成功，开始音频测试...")
                
                // 准备文件
                val inputFile = File(filesDir, "test_input.pcm")
                val outputFile = File(filesDir, "recorded_output.pcm")
                
                if (!inputFile.exists()) {
                    updateStatus("输入文件不存在，请先创建测试文件")
                    return@launch
                }
                
                // 开始测试
                debugExample.startTest(inputFile, outputFile)
                
                startButton.isEnabled = false
                stopButton.isEnabled = true
                
                // 启动状态更新协程
                launch {
                    while (stopButton.isEnabled) {
                        val status = debugExample.getStatus()
                        updateStatus("测试运行中...\n$status")
                        delay(2000)  // 每2秒更新一次状态
                    }
                }
                
                // 运行30秒后自动停止（用于测试）
                delay(30000)
                if (stopButton.isEnabled) {
                    stopTest()
                }
                
            } catch (e: Exception) {
                Log.e("PCMTest", "开始测试失败", e)
                updateStatus("测试失败: ${e.message}")
                startButton.isEnabled = true
                stopButton.isEnabled = false
            }
        }
    }
    
    private fun stopTest() {
        try {
            debugExample.stop()
            room.disconnect()
            
            startButton.isEnabled = true
            stopButton.isEnabled = false
            
            // 显示结果
            val outputFile = File(filesDir, "recorded_output.pcm")
            val fileSize = if (outputFile.exists()) outputFile.length() else 0
            
            val result = """
                测试完成!
                
                输出文件: ${outputFile.absolutePath}
                文件大小: $fileSize 字节
                
                ${if (fileSize > 0) "✅ 成功录制音频数据!" else "⚠️ 没有录制到音频数据"}
                
                ${if (fileSize == 0L) """
                可能原因:
                1. 房间中没有其他参与者
                2. 其他参与者没有开启麦克风
                3. 网络连接问题
                
                建议:
                1. 使用另一个设备或浏览器加入同一房间
                2. 确保其他参与者开启了麦克风
                3. 检查LiveKit服务器URL和Token是否正确
                """ else ""}
            """.trimIndent()
            
            updateStatus(result)
            
            Log.d("PCMTest", "测试停止")
            
        } catch (e: Exception) {
            Log.e("PCMTest", "停止测试失败", e)
            updateStatus("停止失败: ${e.message}")
        }
    }
    
    private fun updateStatus(text: String) {
        runOnUiThread {
            statusText.text = text
            Log.d("PCMTest", "状态: $text")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::debugExample.isInitialized) {
                debugExample.stop()
            }
            if (::room.isInitialized) {
                room.disconnect()
            }
        } catch (e: Exception) {
            Log.e("PCMTest", "清理资源失败", e)
        }
    }
}