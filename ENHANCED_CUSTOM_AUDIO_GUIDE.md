# 增强版自定义音频输入使用指南

## 📋 Demo更新总结

我们已经成功更新了demo应用，解决了自定义音频输入没有效果的问题。

### 🔧 主要修复内容

#### 1. **新增增强版组件**
- ✅ `EnhancedCustomAudioMixer.kt` - 修复了激活状态管理问题
- ✅ `TestAudioProvider.kt` - 多种测试信号生成器
- ✅ `CustomAudioDebugUtils.kt` - 完整的调试工具
- ✅ `EnhancedCustomAudioExtensions.kt` - 便捷的扩展函数

#### 2. **创建新的测试Activity**
- ✅ `EnhancedCustomAudioTestActivity.kt` - 全新的测试界面
- ✅ `activity_enhanced_custom_audio_test.xml` - 对应的布局文件

#### 3. **修复原有Demo**
- ✅ 更新 `RemoteAudioProcessingTestActivity.kt` 使用增强版API
- ✅ 替换旧版 `createAudioTrackWithBuffer` 为 `createEnhancedCustomAudioTrack`
- ✅ 增加详细的调试信息和状态监控

## 🚀 如何使用增强版Demo

### **方法1：使用新的增强版测试Activity**

```kotlin
// 在AndroidManifest.xml中添加
<activity android:name=".EnhancedCustomAudioTestActivity" />

// 启动Activity
val intent = Intent(this, EnhancedCustomAudioTestActivity::class.java)
startActivity(intent)
```

**主要功能：**
- 🎵 **正弦波测试** - 生成440Hz标准测试音
- 🔔 **哔哔声序列** - 间歇性哔哔声测试
- 🌪️ **白噪声测试** - 低音量白噪声
- 📁 **PCM文件播放** - 从本地PCM文件播放音频
- 🔄 **模式切换演示** - 自动切换不同音频模式
- 📊 **实时调试信息** - 详细的状态监控

### **方法2：使用修复后的原有Activity**

原有的 `RemoteAudioProcessingTestActivity` 已经修复，现在使用增强版API：

```kotlin
// 修复前的问题代码（已替换）
val (audioTrack, bufferProvider) = room.localParticipant.createAudioTrackWithBuffer(...)

// 修复后的代码
val (audioTrack, mixer, monitor) = room.localParticipant.createEnhancedCustomAudioTrack(
    name = "enhanced_pcm_track",
    customAudioProvider = bufferProvider,
    microphoneGain = 0.0f,
    customAudioGain = 1.0f,
    mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY,
    enableDebug = true
)
```

## 📱 快速测试步骤

### **1. 基本测试**

```kotlin
// 连接到房间后，创建正弦波测试
val testTrack = localParticipant.createSineWaveTestTrack(
    frequency = 440.0,
    amplitude = 0.5
)

// 发布音频轨道
localParticipant.publishAudioTrack(testTrack.audioTrack)

// 查看状态
Log.d("Test", testTrack.getStatusInfo())
```

### **2. 高级自定义**

```kotlin
// 创建自定义音频提供器
val customProvider = object : CustomAudioBufferProvider {
    override fun provideAudioData(...): ByteBuffer? {
        // 您的自定义音频生成逻辑
        return generateCustomAudio()
    }
    // ... 其他方法
}

// 使用增强版混音器
val (audioTrack, mixer, monitor) = localParticipant.createEnhancedCustomAudioTrack(
    name = "my-custom-audio",
    customAudioProvider = customProvider,
    enableDebug = true
)
```

## 🔍 调试和故障排除

### **查看详细状态信息**

```kotlin
// 混音器状态
val mixerStatus = mixer.getStatusInfo()
Log.d("Debug", mixerStatus)

// 音频提供器状态（如果是TestAudioProvider）
if (provider is TestAudioProvider) {
    val providerStatus = provider.getGeneratorInfo()
    Log.d("Debug", providerStatus)
}

// 系统音频信息
val systemInfo = CustomAudioDebugUtils.checkSystemAudioInfo()
Log.d("Debug", systemInfo)
```

### **常见问题解决**

#### ❌ **问题：自定义音频没有声音**
✅ **解决方案：**
1. 检查混音器是否激活：`mixer.isActivated()`
2. 检查音频提供器是否有数据：`provider.hasMoreData()`
3. 确认使用 `CUSTOM_ONLY` 模式：`mixMode = CustomAudioMixer.MixMode.CUSTOM_ONLY`
4. 查看调试日志确认音频数据流

#### ❌ **问题：音频质量问题**
✅ **解决方案：**
1. 检查音频格式参数是否正确
2. 确认采样率、声道数、位深度匹配
3. 调整音频增益：`customAudioGain`
4. 使用音频分析工具检查数据质量

#### ❌ **问题：性能问题**
✅ **解决方案：**
1. 关闭调试模式：`enableDebug = false`
2. 优化音频数据生成逻辑
3. 使用合适的缓冲区大小
4. 监控CPU和内存使用

## 📊 性能监控

### **内置监控功能**

增强版Demo提供了完整的性能监控：

```kotlin
// 音频缓冲区分析
val analyzer = CustomAudioDebugUtils.AudioBufferAnalyzer()
val analysis = analyzer.analyzeBuffer(audioBuffer, audioFormat)
Log.d("Performance", analysis.getDescription())

// 提供器监控
val monitor = CustomAudioDebugUtils.CustomAudioProviderMonitor(provider)
monitor.startMonitoring()
```

### **关键指标**

- **缓冲区请求频率** - 正常应该是 ~100次/秒（10ms间隔）
- **自定义音频提供成功率** - 应该接近100%
- **音频数据质量** - 检查峰值振幅、RMS、动态范围
- **延迟性能** - 音频生成和传输的延迟

## 🎯 最佳实践

### **1. 音频格式配置**
```kotlin
// 推荐配置
audioFormat = AudioFormat.ENCODING_PCM_16BIT
channelCount = 2  // 立体声
sampleRate = 48000  // 高质量采样率
```

### **2. 错误处理**
```kotlin
try {
    val testTrack = localParticipant.createSineWaveTestTrack()
    localParticipant.publishAudioTrack(testTrack.audioTrack)
} catch (e: Exception) {
    Log.e("CustomAudio", "创建失败", e)
    // fallback逻辑
}
```

### **3. 资源管理**
```kotlin
// 确保正确释放资源
override fun onDestroy() {
    super.onDestroy()
    testTrack?.stop()
    customAudioTrackManager.stopAllTracks()
}
```

### **4. 权限处理**
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

## 🔗 API参考

### **主要扩展函数**

```kotlin
// 创建增强版自定义音频轨道
fun LocalParticipant.createEnhancedCustomAudioTrack(...)

// 创建测试音频轨道
fun LocalParticipant.createSineWaveTestTrack(...)
fun LocalParticipant.createBeepTestTrack(...)
fun LocalParticipant.createWhiteNoiseTestTrack(...)

// 音频轨道管理
class CustomAudioTrackManager {
    fun addTrack(trackId: String, trackResult: TestAudioTrackResult)
    fun stopTrack(trackId: String)
    fun stopAllTracks()
}
```

### **调试工具**

```kotlin
// 音频缓冲区分析
CustomAudioDebugUtils.AudioBufferAnalyzer()

// 提供器监控
CustomAudioDebugUtils.CustomAudioProviderMonitor(provider)

// 系统信息
CustomAudioDebugUtils.checkSystemAudioInfo()
```

## 🎉 总结

增强版自定义音频解决方案已经完全修复了原有的问题：

✅ **解决了混音器激活状态问题**
✅ **提供了完整的调试和监控工具** 
✅ **支持多种测试音频类型**
✅ **包含详细的错误处理和日志**
✅ **提供了易用的扩展函数**
✅ **创建了完整的demo应用**

现在您可以可靠地使用自定义音频输入功能，无论是简单的测试音频还是复杂的自定义音频处理都能正常工作！

如果遇到任何问题，请查看调试日志和状态信息，或参考demo中的实现示例。