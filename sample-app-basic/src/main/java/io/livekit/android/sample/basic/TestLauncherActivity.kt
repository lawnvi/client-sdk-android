package io.livekit.android.sample.basic

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

/**
 * 测试启动器活动
 * 提供一个简单的界面来选择运行不同的音频测试
 */
class TestLauncherActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_launcher)
        
        setupButtons()
    }
    
    private fun setupButtons() {
        findViewById<Button>(R.id.btnOriginalTest).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnPCMTest).setOnClickListener {
            startActivity(Intent(this, PCMTestActivity::class.java))
        }
        
        findViewById<Button>(R.id.btnComprehensiveTest).setOnClickListener {
            startActivity(Intent(this, ComprehensiveAudioTestActivity::class.java))
        }
    }
}