# LiveKit Android SDK Custom Audio Input Sample

这个示例应用演示了如何在 LiveKit Android SDK 中使用自定义音频输入源功能。

## 功能特性

- 连接到 LiveKit 房间
- 切换不同的音频输入源：
  - 麦克风（默认）
  - PCM 音频文件
  - 程序生成的正弦波
- 支持两种音频处理模式：
  - 完全替换麦克风音频
  - 与麦克风音频混合
- 实时控制麦克风开关

## 使用方法

### 1. 连接到房间

1. 在 "LiveKit URL" 字段中输入您的 LiveKit 服务器地址（例如：`wss://your-server.livekit.cloud`）
2. 在 "Access Token" 字段中输入有效的访问令牌
3. 点击 "连接" 按钮

### 2. 测试自定义音频输入源

连接成功后，您可以：

1. **选择音频源**：
   - 麦克风：使用设备麦克风（默认行为）
   - PCM 文件：使用内置的测试音频文件（440Hz 正弦波，5秒）
   - 正弦波：程序生成的 523Hz 正弦波（C5 音符，3秒）

2. **选择音频模式**：
   - 替换麦克风：完全使用自定义音频源，忽略麦克风输入
   - 与麦克风混合：将自定义音频与麦克风音频混合

3. **控制播放**：
   - 点击 "开始自定义音频" 启动选定的音频源
   - 点击 "停止自定义音频" 恢复使用麦克风
   - 使用 "启用/禁用麦克风" 按钮控制麦克风开关

## 技术实现

### 自定义音频输入源

示例使用了以下 LiveKit SDK 新功能：

```kotlin
// PCM 文件输入
val audioCallback = audioTrack.setCustomPcmRawInput(
    context = this,
    resourceId = R.raw.test_audio_48k_16bit_mono,
    audioConfig = audioConfig,
    replaceOriginal = true,
    enableLooping = true
)

// 流输入（如程序生成的音频）
val audioCallback = audioTrack.setCustomStreamInput(
    inputStream = ByteArrayInputStream(audioData),
    audioConfig = audioConfig,
    replaceOriginal = false,
    enableLooping = true
)

// 启动自定义音频
audioCallback.start()

// 停止并清理
audioCallback.close()
audioTrack.clearCustomAudioInput()
```

### 音频格式要求

- 采样率：48000 Hz（推荐，与 WebRTC 默认配置匹配）
- 声道数：1（单声道）
- 编码格式：16-bit PCM
- 字节序：Little Endian

### 测试文件

应用包含一个预生成的 PCM 测试文件：
- 文件：`test_audio_48k_16bit_mono.pcm`
- 格式：48kHz, 16-bit, 单声道
- 内容：440Hz 正弦波（A4 音符）
- 时长：5 秒

## 注意事项

1. **权限**：应用需要 RECORD_AUDIO 权限才能正常工作
2. **网络**：确保设备能够访问指定的 LiveKit 服务器
3. **令牌**：使用有效的访问令牌，确保具有发布音频的权限
4. **音频格式**：自定义音频数据必须与当前 WebRTC 配置的音频格式匹配

## 扩展功能

您可以基于这个示例扩展更多功能：

- 从文件系统读取 PCM 文件
- 从网络流读取音频数据
- 实时音频处理和效果
- 多音频源混合
- 音频格式转换
- 播放进度控制

## 故障排除

如果遇到问题：

1. 检查日志输出中的错误信息
2. 确保音频格式配置正确
3. 验证 LiveKit 服务器连接和令牌有效性
4. 确认设备权限已正确授予
