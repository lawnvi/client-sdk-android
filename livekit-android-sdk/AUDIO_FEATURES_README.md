# LiveKit Android SDK 音频增强功能

本文档介绍了为 LiveKit Android SDK 新增的两个主要音频功能。

## 功能概览

### 1. 自定义音频输入 (Custom Audio Input)
允许从本地音频文件或缓冲区发布音频到房间，支持与麦克风音频混合。

### 2. 远程音频数据处理 (Remote Audio Data Processing)  
拦截从服务端接收到的音频数据，支持自定义处理而不直接播放到扬声器。

---

## 🎵 自定义音频输入功能

### 核心能力
- ✅ 从音频文件播放（MP3、WAV、OPUS、AAC等）
- ✅ 从内存缓冲区流式传输
- ✅ 程序化音频生成
- ✅ 与麦克风音频混合（三种模式）
- ✅ 独立音量控制
- ✅ 循环播放支持

### 快速使用

```kotlin
// 1. 从文件播放背景音乐
val audioTrack = localParticipant.createAudioTrackWithFile(
    context = context,
    audioFileUri = musicFileUri,
    loop = true,
    microphoneGain = 0.7f,      // 麦克风音量 70%
    customAudioGain = 0.3f,     // 背景音乐 30%
    mixMode = CustomAudioMixer.MixMode.ADDITIVE
)

// 2. 从缓冲区流式传输
val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer()
bufferProvider.addAudioData(yourAudioData)

// 3. 发布音频轨道
localParticipant.publishAudioTrack(audioTrack)
```

### 混音模式
- **ADDITIVE** - 自定义音频与麦克风混合
- **REPLACE** - 优先使用自定义音频，无自定义音频时使用麦克风
- **CUSTOM_ONLY** - 仅使用自定义音频，忽略麦克风

### 详细文档
📖 [自定义音频源使用指南](./CUSTOM_AUDIO_GUIDE.md)

---

## 🎧 远程音频数据处理功能

### 核心能力
- ✅ 拦截远程参与者的音频数据
- ✅ 自定义音频处理（录制、分析、过滤）
- ✅ 控制音频播放（静音、修改、自定义播放）
- ✅ 实时音频数据访问
- ✅ 保持或禁用扬声器输出

### 快速使用

```kotlin
// 1. 简单音频数据处理（不影响播放）
remoteAudioTrack.setAudioDataProcessor(participant, trackSid) { 
    participant, trackSid, audioData, bitsPerSample, sampleRate, channels, frames, timestamp ->
    // 处理音频数据：录制、分析等
    processAudioData(audioData, participant)
}

// 2. 静音参与者但仍处理其数据
remoteAudioTrack.muteWithProcessor(participant, trackSid) { 
    participant, trackSid, audioData, bitsPerSample, sampleRate, channels, frames, timestamp ->
    // 音频被静音但仍可处理
    recordMutedAudio(audioData, participant)
}

// 3. 完全控制音频播放
val interceptor = object : RemoteAudioDataInterceptor {
    override fun onRemoteAudioData(...): InterceptorResult {
        // 自定义处理逻辑
        val processedAudio = enhanceAudio(audioData)
        
        return InterceptorResult(
            allowPlayback = shouldPlay,
            modifiedAudioData = processedAudio
        )
    }
}
remoteAudioTrack.setAudioDataInterceptor(participant, trackSid, interceptor)
```

### 应用场景
- 🎙️ **会议录制** - 分别录制每个参与者的音频
- 🤖 **语音识别** - 实时转录参与者发言
- 🔇 **智能静音** - 基于内容自动静音
- 📊 **音频分析** - 音量监控、质量评估
- 🎛️ **音频增强** - 降噪、回声消除

### 详细文档
📖 [远程音频数据处理指南](./REMOTE_AUDIO_PROCESSING_GUIDE.md)

---

## 🚀 完整示例

### 自定义音频输入示例
```kotlin
// 详细示例请参考：
📁 CustomAudioExample.kt          // 完整功能演示
📁 QuickStartCustomAudio.kt       // 快速开始示例
```

### 远程音频处理示例
```kotlin
// 详细示例请参考：
📁 RemoteAudioInterceptionExample.kt      // 完整功能演示
📁 QuickStartRemoteAudioProcessing.kt     // 快速开始示例
```

---

## 📋 权限要求

在 `AndroidManifest.xml` 中添加：

```xml
<!-- 录音权限（麦克风输入） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 文件访问权限（如需从文件播放音频） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />

<!-- 文件写入权限（如需保存音频） -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## 🏗️ 架构设计

### 自定义音频输入架构
```
[音频源] → [CustomAudioBufferProvider] → [CustomAudioMixer] → [LocalAudioTrack] → [发布到房间]
    ↓              ↓                           ↓
 文件/缓冲区    提供音频数据              与麦克风混合
```

### 远程音频处理架构
```
[远程音频] → [RemoteAudioTrack] → [InterceptingAudioTrackSink] → [处理器] → [扬声器/自定义处理]
```

---

## ⚡ 性能优化建议

### 自定义音频输入
- 使用适当的缓冲区大小
- 避免频繁的格式转换
- 在后台线程处理大文件

### 远程音频处理  
- 快速复制音频数据，避免阻塞音频线程
- 在协程中进行耗时处理
- 限制缓冲区大小防止内存泄漏

---

## 🔧 故障排除

### 常见问题

1. **音频播放没有声音**
   - 检查权限是否已授予
   - 确认音频文件格式受支持
   - 检查音量设置

2. **音频处理延迟过高**
   - 减少缓冲区大小
   - 优化处理算法
   - 使用后台线程处理

3. **内存使用过高**
   - 及时清理不需要的缓冲区
   - 限制音频队列长度
   - 避免保存过多音频数据

### 调试技巧
```kotlin
// 启用详细日志
Log.d("AudioFeatures", "音频数据: ${audioData.remaining()} bytes, $sampleRate Hz")

// 监控音频状态
if (audioTrack.state == Track.State.LIVE) {
    Log.d("AudioFeatures", "音频轨道正常工作")
}
```

---

## 📚 相关资源

- [LiveKit 官方文档](https://livekit.io/docs)
- [Android 音频开发指南](https://developer.android.com/guide/topics/media/audio-app/index.html)
- [WebRTC 音频处理](https://webrtc.org/getting-started/audio-capture-processing)

---

## 🤝 贡献

如果您发现问题或有改进建议，欢迎提交 Issue 或 Pull Request。

## 📄 许可证

本功能基于 Apache License 2.0 开源许可证。