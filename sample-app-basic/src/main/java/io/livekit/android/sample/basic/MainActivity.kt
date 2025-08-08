package io.livekit.android.sample.basic

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.examples.SimplePCMExample
import io.livekit.android.room.participant.LocalParticipant_Factory
import io.livekit.android.room.track.LocalAudioTrack
import io.livekit.android.room.track.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val room = LiveKit.create(this)
        val context = this
        val diagnosticExample = DiagnosticAudioExample(context, room)
        val fixedExample = FixedCustomAudioExample(context, room)
        val independentExample = IndependentAudioExample(context, room)

        // 准备文件路径
        val inputFile = File(context.getExternalFilesDir(null), "input.pcm")   // 您的输入 PCM 文件
        val outputFile = File(context.getExternalFilesDir(null), "output.pcm") // 录制的输出文件

        // 生成测试PCM文件（如果不存在）
        if (!inputFile.exists()) {
            println("🎵 生成测试PCM文件...")
            independentExample.generateTestSineWave(inputFile, 3)
        }

        println(">>>>>>>>>>>>>>>>>>> input file: ${inputFile.absolutePath}")
        println(">>>>>>>>>>>>>>>>>>> output file: ${outputFile.absolutePath}")

        lifecycleScope.launch {
            // 连接到房间
            println("🌐 开始连接房间...")
            room.connect("wss://ls1.dearlink.com", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3NTQ2MzI3ODYsImlzcyI6ImxpdmVraXQtYXBpLWtleS0xIiwibmFtZSI6ImRldl91c2VyMSIsIm5iZiI6MTc1NDM3MzU4Niwic3ViIjoiZGV2X3VzZXIxIiwidmlkZW8iOnsicm9vbSI6Im15LWRldi1yb29tIiwicm9vbUpvaW4iOnRydWV9fQ.rAS9JGI3MyV9MjCNVCjsHuZs_A88TjR9aitx3iILfDg")

            room.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> {
                        println("已连接到room")
                        // 运行独立的自定义音频示例（完全不依赖麦克风）
                        launch {
                            println("🚀 开始独立自定义音频输入测试（完全不依赖麦克风）...")
                            independentExample.start(inputFile)
                        }
                    }
                    is RoomEvent.ParticipantConnected -> {
                        println("远程参与者已连接: ${event.participant.name ?: event.participant.identity}")
//                        example.startExample(inputFile, outputFile)
                    }
                    is RoomEvent.ParticipantDisconnected -> {
                        println("远程参与者已断开: ${event.participant.name ?: event.participant.identity}")
                    }
                    is RoomEvent.TrackPublished -> {
                        println("远程轨道已发布: ${event.publication.track?.name} by ${event.participant.name}")
                        if (event.publication.track != null && event.publication.source == Track.Source.MICROPHONE) {
                            println("✅ 正在处理来自 ${event.participant.name ?: event.participant.identity} 的音频\"")
                        }
                        // 开始示例
//                        example.startExample(inputFile, outputFile)
                    }
                    is RoomEvent.TrackUnpublished -> {
                        println("远程轨道已取消发布: ${event.publication.track?.name} by ${event.participant.name}")
                    }
                    is RoomEvent.TrackSubscribed -> {
                        println("已订阅远程轨道: ${event.publication.track?.name} by ${event.participant.name}")
                    }
                    is RoomEvent.TrackUnsubscribed -> {
                        println("已取消订阅远程轨道: ${event.publications.name} by ${event.participant.name}")
                    }
                    is RoomEvent.Disconnected -> {
                        println("房间连接断开: ${event.reason}")
                    }
                    is RoomEvent.Reconnecting -> {
                        println("正在重连...")
                    }
                    is RoomEvent.Reconnected -> {
                        println("重连成功")
                    }
                    is RoomEvent.LocalTrackSubscribed -> {
                        println("本地被订阅")
//                        example.startExample(inputFile, outputFile)
                    }
                    else -> {
                        // 其他事件
                        println("房间事件: $event")
                    }
                }
            }

            // 运行一段时间后停止
            delay(120000) // 120 秒
            independentExample.stop()
        }
    }
}
