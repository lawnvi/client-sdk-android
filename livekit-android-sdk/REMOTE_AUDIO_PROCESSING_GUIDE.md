# 远程音频数据处理指南

本指南介绍如何在 LiveKit Android SDK 中拦截和处理从服务端接收到的音频数据，实现自定义音频处理而不直接播放到扬声器。

## 概述

远程音频数据处理功能允许您：

1. **音频数据拦截** - 获取来自其他参与者的原始音频数据
2. **自定义处理** - 对音频数据进行录制、分析、过滤等处理
3. **播放控制** - 决定是否将音频播放到扬声器
4. **数据修改** - 修改音频数据后再播放
5. **静音处理** - 静音特定参与者但仍能访问其音频数据

## 核心组件

### 1. RemoteAudioDataInterceptor
主要的音频数据拦截器接口：

```kotlin
interface RemoteAudioDataInterceptor {
    fun onRemoteAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ): InterceptorResult
}
```

### 2. AudioDataProcessor
简化的处理器，只处理数据不影响播放：

```kotlin
abstract class AudioDataProcessor : RemoteAudioDataInterceptor {
    abstract fun processAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    )
}
```

### 3. AudioPlaybackController
自定义音频播放控制器：

```kotlin
class AudioPlaybackController(
    private val context: Context,
    private val sampleRate: Int = 48000,
    private val channelConfig: Int = AudioFormat.CHANNEL_OUT_STEREO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
)
```

## 使用方法

### 1. 简单音频数据处理

```kotlin
import io.livekit.android.room.track.setAudioDataProcessor

// 处理音频数据但不影响正常播放
remoteAudioTrack.setAudioDataProcessor(
    participant = participant,
    trackSid = track.sid
) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
    // 处理音频数据
    Log.d("Audio", "接收到来自 ${participant.identity} 的音频: ${audioData.remaining()} 字节")
    
    // 您的处理逻辑：
    // - 保存到文件
    // - 音频分析
    // - 发送到语音识别服务
    // - 等等
}
```

### 2. 静音参与者但仍处理数据

```kotlin
import io.livekit.android.room.track.muteWithProcessor

// 静音参与者但仍能访问其音频数据
remoteAudioTrack.muteWithProcessor(
    participant = participant,
    trackSid = track.sid
) { participant, trackSid, audioData, bitsPerSample, sampleRate, numberOfChannels, numberOfFrames, timestamp ->
    // 音频被静音（不播放到扬声器）但您仍可以处理
    analyzeAudioData(audioData, participant)
    saveToFile(audioData, participant.identity)
}
```

### 3. 完全控制音频播放

```kotlin
import io.livekit.android.room.track.setAudioDataInterceptor

val interceptor = object : RemoteAudioDataInterceptor {
    override fun onRemoteAudioData(
        participant: Participant,
        trackSid: String,
        audioData: ByteBuffer,
        bitsPerSample: Int,
        sampleRate: Int,
        numberOfChannels: Int,
        numberOfFrames: Int,
        timestamp: Long
    ): RemoteAudioDataInterceptor.InterceptorResult {
        
        // 处理音频数据
        val processedAudio = applyNoiseReduction(audioData)
        
        // 决定是否播放以及播放什么
        val shouldPlay = checkParticipantPermissions(participant)
        
        return RemoteAudioDataInterceptor.InterceptorResult(
            allowPlayback = shouldPlay,
            modifiedAudioData = processedAudio // 可选：播放修改后的音频
        )
    }
}

remoteAudioTrack.setAudioDataInterceptor(
    participant = participant,
    trackSid = track.sid,
    interceptor = interceptor
)
```

### 4. 自定义音频播放控制

```kotlin
val playbackInterceptor = object : PlaybackControlledInterceptor(context, enablePlayback = true) {
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
        // 应用自定义音频处理
        val enhancedAudio = enhanceAudio(audioData)
        
        // 通过自定义播放器播放
        playbackController?.playAudioData(enhancedAudio)
        
        // 返回 false 因为我们自己处理播放
        return false
    }
}

remoteAudioTrack.setAudioDataInterceptor(
    participant = participant,
    trackSid = track.sid,
    interceptor = playbackInterceptor
)
```

## 实际应用场景

### 场景1：音频录制

```kotlin
class AudioRecorder {
    private val audioBuffer = mutableListOf<ByteArray>()
    
    fun startRecording(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { _, _, audioData, _, _, _, _, _ ->
            // 录制音频到缓冲区
            val audioBytes = ByteArray(audioData.remaining())
            audioData.duplicate().get(audioBytes)
            audioBuffer.add(audioBytes)
        }
    }
    
    fun saveRecording(outputFile: File) {
        FileOutputStream(outputFile).use { output ->
            audioBuffer.forEach { buffer ->
                output.write(buffer)
            }
        }
    }
}
```

### 场景2：实时语音识别

```kotlin
class SpeechRecognitionProcessor {
    private val speechRecognizer = initializeSpeechRecognizer()
    
    fun setupSpeechRecognition(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { participant, _, audioData, _, sampleRate, channels, _, _ ->
            // 发送音频到语音识别服务
            speechRecognizer.processAudio(audioData, sampleRate, channels) { transcript ->
                onTranscriptionReceived(participant, transcript)
            }
        }
    }
    
    private fun onTranscriptionReceived(participant: Participant, transcript: String) {
        Log.d("Speech", "${participant.identity} 说: $transcript")
        // 处理转录结果
    }
}
```

### 场景3：音频质量监控

```kotlin
class AudioQualityMonitor {
    fun setupQualityMonitoring(participant: Participant, audioTrack: RemoteAudioTrack) {
        audioTrack.setAudioDataProcessor(
            participant = participant,
            trackSid = audioTrack.sid
        ) { participant, _, audioData, bitsPerSample, sampleRate, channels, frames, _ ->
            val audioLevel = calculateAudioLevel(audioData, bitsPerSample)
            val quality = assessAudioQuality(audioData, sampleRate, channels)
            
            Log.d("Quality", "${participant.identity} - 音量: $audioLevel, 质量: $quality")
            
            // 根据质量调整或发送反馈
            if (quality < 0.5) {
                notifyPoorAudioQuality(participant)
            }
        }
    }
}
```

### 场景4：会议录制与回放

```kotlin
class ConferenceRecorder {
    private val participantRecordings = mutableMapOf<String, MutableList<AudioFrame>>()
    
    fun startConferenceRecording(participants: List<Participant>) {
        participants.forEach { participant ->
            val audioTrack = participant.audioTrackPublications.values
                .firstOrNull()?.track as? RemoteAudioTrack
            
            audioTrack?.setAudioDataProcessor(
                participant = participant,
                trackSid = audioTrack.sid
            ) { participant, _, audioData, bitsPerSample, sampleRate, channels, frames, timestamp ->
                val frame = AudioFrame(
                    data = audioData.duplicate(),
                    timestamp = timestamp,
                    sampleRate = sampleRate,
                    channels = channels
                )
                
                participantRecordings.getOrPut(participant.identity.value) { mutableListOf() }
                    .add(frame)
            }
        }
    }
    
    fun saveConferenceRecording(outputDir: File) {
        participantRecordings.forEach { (identity, frames) ->
            val participantFile = File(outputDir, "$identity.wav")
            saveAudioFramesToWav(frames, participantFile)
        }
    }
}
```

## 音频数据格式

### 常见格式参数
- **bitsPerSample**: 通常为 16 (16-bit PCM)
- **sampleRate**: 常见值为 16000, 44100, 48000 Hz
- **numberOfChannels**: 1 (单声道) 或 2 (立体声)
- **audioFormat**: AudioFormat.ENCODING_PCM_16BIT (最常见)

### 音频数据处理

```kotlin
fun processAudioBuffer(audioData: ByteBuffer, bitsPerSample: Int): DoubleArray {
    val samples = mutableListOf<Double>()
    
    when (bitsPerSample) {
        16 -> {
            while (audioData.hasRemaining()) {
                val sample = audioData.short.toDouble() / Short.MAX_VALUE
                samples.add(sample)
            }
        }
        8 -> {
            while (audioData.hasRemaining()) {
                val sample = ((audioData.get().toInt() and 0xFF) - 128).toDouble() / 127.0
                samples.add(sample)
            }
        }
    }
    
    return samples.toDoubleArray()
}
```

## 性能优化建议

### 1. 异步处理
```kotlin
private val audioProcessingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

audioTrack.setAudioDataProcessor(participant, trackSid) { participant, _, audioData, _, _, _, _, _ ->
    // 快速复制数据，避免阻塞音频线程
    val audioCopy = ByteArray(audioData.remaining())
    audioData.duplicate().get(audioCopy)
    
    // 在后台线程处理
    audioProcessingScope.launch {
        processAudioInBackground(audioCopy, participant)
    }
}
```

### 2. 缓冲区管理
```kotlin
class AudioBufferManager(private val maxBufferSize: Int = 1000) {
    private val audioBuffers = LinkedList<ByteArray>()
    
    fun addAudioData(audioData: ByteBuffer) {
        val audioBytes = ByteArray(audioData.remaining())
        audioData.duplicate().get(audioBytes)
        
        synchronized(audioBuffers) {
            audioBuffers.offer(audioBytes)
            
            // 限制缓冲区大小
            while (audioBuffers.size > maxBufferSize) {
                audioBuffers.poll()
            }
        }
    }
}
```

### 3. 内存优化
```kotlin
// 重用缓冲区对象
private val reusableBuffer = ByteArray(8192)

audioTrack.setAudioDataProcessor(participant, trackSid) { _, _, audioData, _, _, _, _, _ ->
    val dataSize = audioData.remaining()
    if (dataSize <= reusableBuffer.size) {
        audioData.duplicate().get(reusableBuffer, 0, dataSize)
        processAudio(reusableBuffer, dataSize)
    }
}
```

## 权限要求

在 `AndroidManifest.xml` 中添加必要权限：

```xml
<!-- 音频录制权限 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 如果需要保存音频文件 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- Android 10+ 的存储权限 -->
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
```

## 错误处理

```kotlin
try {
    remoteAudioTrack.setAudioDataInterceptor(participant, trackSid, interceptor)
} catch (e: SecurityException) {
    Log.e("Audio", "权限不足", e)
} catch (e: IllegalStateException) {
    Log.e("Audio", "音频轨道状态无效", e)
} catch (e: Exception) {
    Log.e("Audio", "设置音频拦截器失败", e)
}
```

## 清理资源

```kotlin
// 移除音频拦截器
remoteAudioTrack.clearAudioDataInterceptor()

// 清理自定义播放控制器
playbackController.release()

// 取消协程
audioProcessingScope.cancel()
```

## 完整示例

参见以下文件中的完整示例代码：
- `RemoteAudioInterceptionExample.kt` - 详细使用示例
- `QuickStartRemoteAudioProcessing.kt` - 快速开始示例

通过这些功能，您可以实现复杂的音频处理需求，从简单的音频录制到高级的实时音频分析和处理。