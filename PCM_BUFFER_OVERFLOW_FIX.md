# PCM缓冲区溢出问题修复指南

## 🚨 问题描述

在发送PCM数据时遇到`BufferOverflowException`错误：

```
java.nio.BufferOverflowException
    at java.nio.ByteBuffer.put(ByteBuffer.java:613)
    at io.livekit.android.audio.BufferAudioBufferProvider.addAudioData(BufferAudioBufferProvider.kt:146)
```

## 🔧 根本原因

在`BufferAudioBufferProvider.addAudioData(ByteBuffer)`方法中，当启用循环模式时，ByteBuffer的position和limit状态管理不当，导致：

1. 第一次`bufferCopy.put(audioData)`消耗了audioData
2. 在循环存储时，`audioData.rewind()`后的状态与预期的缓冲区大小不匹配
3. 导致`originalCopy.put(audioData)`时发生溢出

## ✅ 已实施的修复

### 1. 修复原有BufferAudioBufferProvider

```kotlin
// 修复前的问题代码
if (loop) {
    val originalCopy = ByteBuffer.allocate(audioData.remaining())
    audioData.rewind()
    originalCopy.put(audioData)  // 💥 BufferOverflowException
    originalCopy.flip()
    originalBuffers.add(originalCopy)
}

// 修复后的代码
if (loop) {
    // 重置audioData到原始状态以便复制到循环缓冲区
    audioData.position(originalPosition)
    audioData.limit(originalLimit)
    
    val originalCopy = ByteBuffer.allocate(dataSize)
    originalCopy.put(audioData)  // ✅ 安全
    originalCopy.flip()
    originalBuffers.add(originalCopy)
}
```

### 2. 创建SafeBufferAudioBufferProvider

新增了`SafeBufferAudioBufferProvider.kt`，提供更安全的实现：

```kotlin
class SafeBufferAudioBufferProvider(
    // ... 参数
    private val maxQueueSize: Int = 50 // 防止内存溢出
) : CustomAudioBufferProvider {
    
    fun addAudioData(audioData: ByteBuffer): Boolean {
        return try {
            addAudioDataInternal(audioData)
        } catch (e: Exception) {
            bufferOverflowCount.incrementAndGet()
            LKLog.e(e) { "添加音频数据失败" }
            false
        }
    }
}
```

### 3. 在Demo中添加异常处理

```kotlin
// 原来的代码
provider.addAudioData(audioData)

// 修复后的代码
try {
    provider.addAudioData(audioData)
    logTestResult("✅ 成功添加音频数据: ${audioData.size} bytes")
} catch (e: Exception) {
    logTestResult("❌ 添加音频数据失败: ${e.message}")
    updateStatus("❌ 添加音频数据失败: ${e.message}")
    return@launch
}
```

## 🚀 使用修复版本

### 方法1：使用修复后的原版BufferAudioBufferProvider

原有代码无需修改，BufferOverflowException已经修复。

### 方法2：使用新的SafeBufferAudioBufferProvider

```kotlin
// 替换原有的createAudioTrackWithBuffer
val (audioTrack, safeProvider) = room.localParticipant.createSafeAudioTrackWithBuffer(
    name = "safe_pcm_track",
    audioFormat = AudioFormat.ENCODING_PCM_16BIT,
    channelCount = 2,
    sampleRate = 48000,
    loop = true,
    maxQueueSize = 50
)

// 发布轨道
room.localParticipant.publishAudioTrack(audioTrack)

// 安全地添加音频数据
val success = safeProvider.addAudioData(audioData)
if (!success) {
    Log.w("Audio", "添加音频数据失败")
}
```

### 方法3：使用增强版API（推荐）

```kotlin
// 使用增强版自定义音频轨道
val (audioTrack, mixer, monitor) = room.localParticipant.createEnhancedCustomAudioTrack(
    name = "enhanced_pcm_track",
    customAudioProvider = safeProvider,
    enableDebug = true
)
```

## 📊 调试和监控

### 查看SafeBufferAudioBufferProvider统计信息

```kotlin
val statistics = safeProvider.getStatistics()
Log.d("AudioStats", statistics)
```

输出示例：
```
安全音频缓冲区提供器统计:
- 运行状态: true
- 音频格式: 2, 声道: 2, 采样率: 48000
- 循环模式: true
- 队列大小: 5 / 50
- 原始缓冲区: 3
- 添加缓冲区: 25 (120KB)
- 提供缓冲区: 20 (96KB)
- 溢出错误: 0
- 队列溢出: 0
- 有更多数据: true
```

### 监控关键指标

```kotlin
// 检查是否有溢出错误
val stats = safeProvider.getStatistics()
if (stats.contains("溢出错误: 0")) {
    Log.i("Audio", "✅ 无缓冲区溢出错误")
} else {
    Log.w("Audio", "⚠️ 检测到溢出错误")
}
```

## 🔍 故障排除

### 问题1：仍然出现BufferOverflowException
**解决方案：**
1. 确保使用修复后的代码版本
2. 检查音频数据大小是否合理
3. 使用SafeBufferAudioBufferProvider替代原版

### 问题2：音频质量问题
**解决方案：**
1. 检查音频格式参数是否正确
2. 确认PCM数据格式匹配
3. 调整缓冲区大小

### 问题3：内存使用过高
**解决方案：**
1. 降低maxQueueSize参数
2. 及时清理不需要的音频数据
3. 监控队列大小

## 📋 预防措施

1. **总是进行异常处理**：
   ```kotlin
   try {
       provider.addAudioData(audioData)
   } catch (e: Exception) {
       Log.e("Audio", "添加音频数据失败", e)
   }
   ```

2. **监控缓冲区状态**：
   ```kotlin
   if (provider.getQueuedBufferCount() > maxExpected) {
       Log.w("Audio", "缓冲区队列过长")
   }
   ```

3. **使用合适的缓冲区大小**：
   ```kotlin
   val frameSize = sampleRate * channelCount * bytesPerSample * frameDurationMs / 1000
   ```

4. **及时清理资源**：
   ```kotlin
   override fun onDestroy() {
       safeProvider.clearAudioData()
       safeProvider.stop()
   }
   ```

## ✅ 验证修复

运行以下测试确认问题已解决：

1. **基本功能测试**：创建音频轨道并发送PCM数据
2. **循环模式测试**：启用loop=true并发送多个数据块
3. **大数据量测试**：连续发送大量音频数据
4. **异常恢复测试**：模拟错误情况并验证恢复

现在PCM发送功能应该稳定可靠，不再出现BufferOverflowException错误！