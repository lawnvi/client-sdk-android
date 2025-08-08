# 自定义音频源使用指南

本指南介绍如何在 LiveKit Android SDK 中使用自定义音频源功能，支持从本地音频文件或缓冲区发布音频到房间。

## 概述

LiveKit Android SDK 的自定义音频源功能允许您：

1. **从音频文件播放** - 支持 MP3、WAV、OPUS、AAC 等格式
2. **从缓冲区流式传输** - 实时音频数据处理
3. **程序化音频生成** - 合成音频、音效等
4. **灵活的混音模式** - 与麦克风音频混合或替换

## 核心组件

### 1. CustomAudioBufferProvider
音频数据提供者接口，定义了如何获取音频数据。

```kotlin
interface CustomAudioBufferProvider {
    fun provideAudioData(requestedBytes: Int, audioFormat: Int, channelCount: Int, sampleRate: Int): ByteBuffer?
    fun start()
    fun stop()
    fun hasMoreData(): Boolean
    fun getCaptureTimeNs(): Long
}
```

### 2. 预置实现

#### FileAudioBufferProvider
从本地音频文件读取数据：

```kotlin
val fileProvider = FileAudioBufferProvider(
    context = context,
    audioFileUri = audioFileUri,
    loop = true // 是否循环播放
)
```

#### BufferAudioBufferProvider
从内存缓冲区提供数据：

```kotlin
val bufferProvider = BufferAudioBufferProvider(
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    channelCount = 2,
    sampleRate = 44100,
    loop = false
)
```

### 3. CustomAudioMixer
音频混合器，提供三种混音模式：

- **ADDITIVE** - 加法混合（默认）：自定义音频与麦克风音频叠加
- **REPLACE** - 替换模式：优先使用自定义音频，无自定义音频时使用麦克风
- **CUSTOM_ONLY** - 仅自定义音频：完全忽略麦克风输入

## 使用方法

### 1. 从音频文件播放

```kotlin
import io.livekit.android.room.participant.createAudioTrackWithFile

// 创建包含文件音频的音频轨道
val audioTrack = localParticipant.createAudioTrackWithFile(
    context = context,                             // Android Context
    name = "background_music",
    audioFileUri = Uri.parse("file:///path/to/audio.mp3"),
    loop = true,                                    // 循环播放
    microphoneGain = 0.7f,                         // 麦克风音量 70%
    customAudioGain = 0.8f,                        // 自定义音频音量 80%
    mixMode = CustomAudioMixer.MixMode.ADDITIVE    // 混合模式
)

// 发布音频轨道
localParticipant.publishAudioTrack(audioTrack)
```

### 2. 从缓冲区流式传输

```kotlin
import io.livekit.android.room.participant.createAudioTrackWithBuffer

// 创建缓冲区音频轨道
val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
    name = "streamed_audio",
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    channelCount = 2,
    sampleRate = 44100,
    microphoneGain = 0.5f,
    customAudioGain = 1.0f,
    mixMode = CustomAudioMixer.MixMode.ADDITIVE
)

// 发布音频轨道
localParticipant.publishAudioTrack(audioTrack)

// 添加音频数据（可以多次调用）
val audioData: ByteArray = getAudioDataFromSomewhere()
bufferProvider.addAudioData(audioData)
```

### 3. 自定义音频提供者

```kotlin
// 实现自定义音频提供者
class MyCustomAudioProvider : CustomAudioBufferProvider {
    override fun provideAudioData(
        requestedBytes: Int,
        audioFormat: Int,
        channelCount: Int,
        sampleRate: Int
    ): ByteBuffer? {
        // 生成或获取音频数据
        return generateAudioData(requestedBytes, audioFormat, channelCount, sampleRate)
    }

    override fun start() { /* 启动音频源 */ }
    override fun stop() { /* 停止音频源 */ }
    override fun hasMoreData(): Boolean = true
    override fun getCaptureTimeNs(): Long = System.nanoTime()
}

// 使用自定义提供者
val customProvider = MyCustomAudioProvider()
val audioTrack = localParticipant.createAudioTrackWithCustomSource(
    name = "custom_audio",
    customAudioProvider = customProvider,
    microphoneGain = 0.3f,
    customAudioGain = 1.0f,
    mixMode = CustomAudioMixer.MixMode.REPLACE
)
```

## 实际使用场景

### 场景1：背景音乐播放

```kotlin
lifecycleScope.launch {
    // 播放背景音乐并与麦克风混合
    val audioTrack = localParticipant.createAudioTrackWithFile(
        context = this@YourActivity,
        audioFileUri = Uri.parse("android.resource://$packageName/${R.raw.background_music}"),
        loop = true,
        microphoneGain = 0.8f,   // 保持麦克风清晰
        customAudioGain = 0.3f,  // 背景音乐较低
        mixMode = CustomAudioMixer.MixMode.ADDITIVE
    )
    
    localParticipant.publishAudioTrack(audioTrack)
}
```

### 场景2：音频文件播放（替换麦克风）

```kotlin
lifecycleScope.launch {
    // 播放预录音频，播放完毕后切换回麦克风
    val audioTrack = localParticipant.createAudioTrackWithFile(
        context = this@YourActivity,
        audioFileUri = recordedMessageUri,
        loop = false,             // 不循环
        microphoneGain = 1.0f,    // 正常麦克风音量（作为备选）
        customAudioGain = 1.0f,   // 正常文件音量
        mixMode = CustomAudioMixer.MixMode.REPLACE  // 文件优先，无文件时用麦克风
    )
    
    localParticipant.publishAudioTrack(audioTrack)
}
```

### 场景3：实时音频处理

```kotlin
lifecycleScope.launch {
    // 创建缓冲区音频轨道用于实时流处理
    val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
        audioFormat = AudioFormat.ENCODING_PCM_16BIT,
        channelCount = 1,    // 单声道
        sampleRate = 16000,  // 16kHz 用于语音
        mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY  // 仅使用自定义音频
    )
    
    localParticipant.publishAudioTrack(audioTrack)
    
    // 启动音频处理线程
    launch {
        while (isActive) {
            val processedAudio = processAudioFromExternalSource()
            bufferProvider.addAudioData(processedAudio)
            delay(20) // 20ms 间隔
        }
    }
}
```

## 音频格式支持

### 支持的输入格式
- **文件格式**: MP3, WAV, OPUS, AAC, M4A, OGG 等（由 Android MediaExtractor 支持）
- **PCM 格式**: 8-bit, 16-bit, 32-bit float
- **采样率**: 8kHz - 192kHz（常用：16kHz, 44.1kHz, 48kHz）
- **声道**: 单声道, 立体声, 多声道

### 输出格式
SDK 会自动处理格式转换，匹配 WebRTC 的要求：
- 16-bit PCM（主要）
- 48kHz 采样率（WebRTC 首选）
- 单声道或立体声

## 性能优化建议

### 1. 文件音频
```kotlin
// 预加载音频文件信息
val fileProvider = FileAudioBufferProvider(context, audioUri, loop = true)
val audioInfo = fileProvider.getAudioFileInfo()
Log.d("Audio", "文件时长: ${audioInfo?.durationMs}ms, 比特率: ${audioInfo?.bitrate}")
```

### 2. 缓冲区管理
```kotlin
// 监控缓冲区状态
val queuedBuffers = bufferProvider.getQueuedBufferCount()
if (queuedBuffers < 3) {
    // 缓冲区不足，添加更多数据
    addMoreAudioData()
}
```

### 3. 内存管理
```kotlin
// 及时清理不需要的音频数据
bufferProvider.clearAudioData()

// 停止音频轨道时清理资源
audioTrack.stop()
```

## 权限要求

在 `AndroidManifest.xml` 中添加必要权限：

```xml
<!-- 录音权限（用于麦克风输入） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 读取外部存储（如果从文件播放音频） -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## 错误处理

```kotlin
try {
    val audioTrack = localParticipant.createAudioTrackWithFile(
        audioFileUri = audioUri,
        // ... 其他参数
    )
    localParticipant.publishAudioTrack(audioTrack)
} catch (e: SecurityException) {
    // 权限不足
    Log.e("Audio", "录音权限未授予", e)
} catch (e: IOException) {
    // 文件读取错误
    Log.e("Audio", "音频文件读取失败", e)
} catch (e: Exception) {
    // 其他错误
    Log.e("Audio", "音频轨道创建失败", e)
}
```

## 完整示例

参见 `CustomAudioExample.kt` 文件，其中包含了所有使用场景的完整示例代码。

## 注意事项

1. **性能影响**: 自定义音频处理会增加 CPU 使用率，特别是格式转换
2. **延迟**: 文件读取和格式转换可能引入延迟
3. **同步**: 确保音频数据及时提供，避免音频中断
4. **内存**: 大音频文件和缓冲区会占用较多内存
5. **权限**: 确保已获得必要的权限才能使用相关功能

通过这些功能，您可以轻松实现各种音频需求，从简单的背景音乐播放到复杂的实时音频处理。