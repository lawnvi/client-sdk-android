# LiveKit Android SDK 音频功能测试指南

## 概述

本指南描述了如何测试LiveKit Android SDK中新增的自定义音频处理功能，包括自定义音频数据源和远程音频数据处理功能。

## 新增功能

### 1. 自定义音频数据源

- **BufferAudioBufferProvider**: 从内存缓冲区提供音频数据
- **FileAudioBufferProvider**: 从音频文件提供音频数据  
- **CustomAudioBufferProvider**: 自定义音频数据提供器接口
- **CustomAudioMixer**: 音频混合器，支持多种混合模式

### 2. 远程音频数据处理

- **RemoteAudioDataInterceptor**: 拦截和处理远程音频数据
- **AudioDataProcessor**: 简化的音频数据处理器
- **PlaybackControlledInterceptor**: 带播放控制的音频拦截器
- **AudioPlaybackController**: 自定义音频播放控制器

### 3. 扬声器播放控制

- 可选择是否通过扬声器播放远程音频
- 自定义播放控制器提供音量调节、特效处理等功能
- 支持完全静音或自定义播放逻辑

## 测试组件

### 主要测试类

1. **ComprehensiveAudioTestActivity**: 综合音频功能测试
2. **RemoteAudioInterceptorTestExample**: 远程音频拦截测试示例
3. **CustomAudioSourceTestExample**: 自定义音频数据源测试示例
4. **TestLauncherActivity**: 测试启动器

### 测试文件

- `ComprehensiveAudioTestActivity.kt`: 主要测试活动
- `RemoteAudioInterceptorTestExample.kt`: 远程音频处理示例
- `CustomAudioSourceTestExample.kt`: 自定义音频源示例
- `TestLauncherActivity.kt`: 测试选择界面

## 测试场景

### 1. 自定义音频数据源测试

#### 场景1.1: 缓冲区音频提供器
```kotlin
val (audioTrack, bufferProvider) = localParticipant.createAudioTrackWithBuffer(
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    channelCount = 2,
    sampleRate = 44100,
    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
)
```

#### 场景1.2: 文件音频提供器
```kotlin
val audioTrack = localParticipant.createAudioTrackWithFile(
    context = context,
    audioFileUri = Uri.fromFile(audioFile),
    loop = true,
    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY
)
```

#### 场景1.3: 自定义音频生成器
```kotlin
val customGenerator = object : CustomAudioBufferProvider {
    override fun provideAudioData(...): ByteBuffer? {
        // 生成自定义音频数据
    }
}
```

### 2. 远程音频数据处理测试

#### 场景2.1: 基础音频数据处理
```kotlin
audioTrack.setAudioDataProcessor(participant, trackSid) { 
    participant, trackSid, audioData, ... ->
    // 处理音频数据但不影响播放
}
```

#### 场景2.2: 选择性静音
```kotlin
audioTrack.muteWithProcessor(participant, trackSid) { 
    participant, trackSid, audioData, ... ->
    // 音频被静音但仍可处理数据
}
```

#### 场景2.3: 完全控制播放
```kotlin
val interceptor = object : RemoteAudioDataInterceptor {
    override fun onRemoteAudioData(...): InterceptorResult {
        // 完全控制音频播放逻辑
        return InterceptorResult(allowPlayback = shouldPlay)
    }
}
```

### 3. 扬声器播放控制测试

#### 场景3.1: 自定义播放控制器
```kotlin
val playbackController = AudioPlaybackController(context)
playbackController.initialize()
playbackController.playAudioData(audioData)
```

#### 场景3.2: 带播放控制的拦截器
```kotlin
val interceptor = object : PlaybackControlledInterceptor(context, enablePlayback = true) {
    override fun processAndControlPlayback(...): Boolean {
        // 自定义播放逻辑
        playbackController?.playAudioData(audioData)
        return false // 阻止系统默认播放
    }
}
```

## 使用说明

### 运行测试

1. **启动应用**: 运行sample-app-basic模块
2. **选择测试**: 在TestLauncherActivity中选择"综合测试"
3. **连接房间**: 测试会自动连接到预配置的房间
4. **运行测试**: 点击"运行所有测试"按钮

### 测试步骤

1. **准备阶段**:
   - 生成测试PCM文件
   - 初始化音频组件
   - 连接到LiveKit房间

2. **自定义音频源测试**:
   - 测试Buffer Provider
   - 测试File Provider  
   - 测试自定义生成器

3. **远程音频处理测试**:
   - 设置音频拦截器
   - 测试音频录制
   - 验证音频分析功能

4. **播放控制测试**:
   - 测试自定义播放控制器
   - 验证音量控制
   - 测试选择性播放

5. **生成报告**:
   - 自动生成测试报告
   - 保存测试结果到文件

### 测试配置

#### 网络配置
```kotlin
val wsUrl = "wss://ls1.dearlink.com"
val token = "your_jwt_token"
```

#### 音频配置
```kotlin
val audioConfig = AudioConfiguration(
    sampleRate = 44100,
    channelCount = 2,
    audioFormat = AudioFormat.ENCODING_PCM_16BIT
)
```

### 验证要点

1. **自定义音频数据源**:
   - ✅ 能够成功创建和发布自定义音频轨道
   - ✅ 音频数据正确传输到远程参与者
   - ✅ 支持不同音频格式和质量
   - ✅ 支持实时数据推送

2. **远程音频数据处理**:
   - ✅ 能够拦截远程音频数据
   - ✅ 可以分析音频数据（音量、频率等）
   - ✅ 可以将音频录制到文件
   - ✅ 不影响正常音频播放（可选）

3. **扬声器播放控制**:
   - ✅ 可以完全禁用扬声器播放
   - ✅ 可以使用自定义播放控制器
   - ✅ 支持音量调节和音效处理
   - ✅ 可以根据条件选择性播放

## 文件输出

测试运行后会生成以下文件：

- `test_input.pcm`: 测试用的输入PCM文件
- `recorded_remote_audio.pcm`: 录制的远程音频
- `test_results.txt`: 详细的测试报告

## 故障排除

### 常见问题

1. **音频权限问题**:
   ```xml
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   ```

2. **网络连接问题**:
   - 检查网络配置
   - 验证JWT token有效性
   - 确认服务器地址正确

3. **音频格式问题**:
   - 确保PCM文件格式正确
   - 验证采样率和声道数匹配
   - 检查字节序设置

4. **内存问题**:
   - 合理控制音频缓冲区大小
   - 及时释放音频资源
   - 避免音频数据积累

### 日志输出

测试过程中会输出详细的日志信息：

```
D/AudioTest: 🚀 开始测试自定义音频数据源...
D/AudioTest: 📡 发布Buffer音频轨道...
D/AudioTest: 🎧 设置 participant1 的音频处理...
D/AudioTest: ✅ 自定义音频数据源测试完成
```

## API参考

### 主要接口

- `CustomAudioBufferProvider`: 自定义音频数据提供器
- `RemoteAudioDataInterceptor`: 远程音频数据拦截器
- `AudioPlaybackController`: 音频播放控制器
- `PlaybackControlledInterceptor`: 带播放控制的拦截器

### 扩展方法

- `LocalParticipant.createAudioTrackWithBuffer()`: 创建缓冲区音频轨道
- `LocalParticipant.createAudioTrackWithFile()`: 创建文件音频轨道
- `RemoteAudioTrack.setAudioDataInterceptor()`: 设置音频拦截器
- `RemoteAudioTrack.setAudioDataProcessor()`: 设置音频处理器

## 总结

该测试方案全面覆盖了LiveKit Android SDK中新增的音频处理功能，提供了详细的测试用例和验证方法。通过运行这些测试，可以确保自定义音频数据源和远程音频处理功能正常工作，并验证扬声器播放控制的灵活性。