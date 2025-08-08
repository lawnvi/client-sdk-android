# 自定义音频输入源使用指南（文件路径版本）

## 快速开始

### 1. 基本用法

```kotlin
// 获取音频轨道
val audioTrack = room.localParticipant.getOrCreateDefaultAudioTrack()

// 配置音频格式（必须与LiveKit配置匹配）
val audioConfig = CustomAudioInputSource.AudioConfig(
    sampleRate = 48000,                          // 48kHz采样率
    channelCount = 1,                            // 单声道
    audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16位PCM
)
```

### 2. 使用PCM文件（文件路径方式）

```kotlin
// 从文件路径加载PCM文件
val callback = audioTrack.setCustomPcmFileInput(
    filePath = "/path/to/your/audio.pcm",
    audioConfig = audioConfig,
    replaceOriginal = true,  // 完全替换麦克风
    enableLooping = true
)

// 启动播放
callback.start()

// 停止播放
callback.stop()
callback.close()
```

### 3. 使用程序生成的音频

```kotlin
// 生成正弦波音频数据
val frequency = 440.0  // A4音符
val duration = 3       // 3秒
val samples = generateSineWave(frequency, duration, audioConfig)

// 设置流输入
val callback = audioTrack.setCustomStreamInput(
    inputStream = ByteArrayInputStream(samples),
    audioConfig = audioConfig,
    replaceOriginal = false, // 与麦克风混合
    enableLooping = true
)

callback.start()
```

### 4. 使用网络音频流

```kotlin
// 从网络获取音频流
val url = URL("https://example.com/audio.pcm")
val networkStream = url.openStream()

val callback = audioTrack.setCustomStreamInput(
    inputStream = networkStream,
    audioConfig = audioConfig,
    replaceOriginal = true,
    enableLooping = false,     // 网络流通常不循环
    bufferSizeMs = 200        // 增大缓冲区应对网络延迟
)

callback.start()
```

## 示例应用功能测试

### 运行示例应用
1. 连接LiveKit服务器
2. 点击"选择 PCM 文件"按钮选择音频文件
3. 选择音频源（麦克风/PCM文件/正弦波）
4. 选择模式（替换/混合）
5. 点击"开始自定义音频"测试

### 测试场景
- **PCM文件播放**：选择本地PCM文件进行播放
- **正弦波生成**：实时生成523Hz（C5音符）正弦波
- **音频混合**：将自定义音频与麦克风混合
- **动态切换**：在不同音频源之间切换

## 音频格式要求

确保您的音频数据符合以下要求：
- 采样率：48000 Hz（推荐）
- 位深：16-bit
- 声道：单声道
- 格式：PCM
- 字节序：Little Endian

## 文件路径支持

### Android 文件访问
- **内部存储**：`/data/data/package/files/audio.pcm`
- **外部存储**：`/storage/emulated/0/Download/audio.pcm`
- **SD卡**：`/storage/sdcard0/audio.pcm`

### 权限要求
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### 运行时权限
```kotlin
// 请求存储权限
Dexter.withContext(this)
    .withPermissions(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    .withListener { /* 处理权限结果 */ }
    .check()
```

## 常见问题

**Q: 为什么没有听到自定义音频？**
A: 检查音频格式是否匹配，确保调用了callback.start()

**Q: 如何确保音频质量？**
A: 使用48kHz采样率，确保音频数据没有失真

**Q: 可以同时使用多个音频源吗？**
A: 目前每个轨道只支持一个自定义音频源，但可以在混合模式下与麦克风叠加

**Q: 如何优化性能？**
A: 使用适当的缓冲区大小，避免在音频线程中进行重计算

**Q: 文件路径无效怎么办？**
A: 检查文件是否存在、是否有读取权限，使用绝对路径

## 注意事项

1. **权限**：应用需要 READ_EXTERNAL_STORAGE 权限才能读取外部文件
2. **文件格式**：确保PCM文件格式正确，无文件头
3. **文件大小**：大文件会自动限制内存加载，建议文件小于100MB
4. **路径格式**：使用绝对路径，避免相对路径问题
5. **错误处理**：检查文件是否存在和可读
