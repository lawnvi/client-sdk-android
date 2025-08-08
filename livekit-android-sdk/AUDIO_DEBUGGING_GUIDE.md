# 音频功能调试指南

## 问题现象
您在 sample-app-basic 中测试音频功能时，保存的文件没有声音。

## 可能原因分析

### 1. 远程音频数据接收问题
**最可能的原因**：没有其他参与者在房间中，或其他参与者没有开启麦克风。

**检查方法：**
```kotlin
// 检查远程参与者数量
Log.d("Debug", "远程参与者数量: ${room.remoteParticipants.size}")

// 检查远程音频轨道
room.remoteParticipants.values.forEach { participant ->
    Log.d("Debug", "参与者: ${participant.identity}")
    participant.audioTrackPublications.values.forEach { publication ->
        Log.d("Debug", "  音频轨道: ${publication.track?.sid}, 状态: ${publication.track?.state}")
    }
}
```

### 2. 音频轨道访问问题
从您的代码修改中，我注意到这个变化：
```kotlin
// 原始代码
participant.audioTrackPublications.values.firstOrNull()?.track

// 您的修改
participant.audioTrackPublications.firstOrNull()?.first?.track
```

这个修改可能有问题。让我们使用正确的访问方式：

```kotlin
// 正确的方式
val audioPublication = participant.audioTrackPublications.values.firstOrNull()
val audioTrack = audioPublication?.track as? RemoteAudioTrack
```

### 3. 采样率不匹配问题
您将采样率从44100改为8000，但这可能导致不兼容：

```kotlin
// 建议使用标准采样率
val sampleRate = 16000  // 或 44100
```

## 调试解决方案

### 方案1：使用诊断工具

```kotlin
// 在您的MainActivity中
val diagnostic = AudioDiagnostic(context, room)

lifecycleScope.launch {
    room.connect(url, token)
    
    // 等待连接完成
    delay(2000)
    
    val inputFile = File(filesDir, "test_input.pcm")
    val outputFile = File(filesDir, "test_output.pcm")
    
    diagnostic.startDiagnostic(inputFile, outputFile)
    
    // 运行30秒
    delay(30000)
    
    diagnostic.stop()
    
    // 查看诊断报告
    Log.d("Diagnostic", diagnostic.getDiagnosticReport())
}
```

### 方案2：修复现有代码

修改您的 `SimplePCMExample.kt`：

```kotlin
private fun setupAudioOutput(outputFile: File) {
    outputFile.parentFile?.mkdirs()
    outputStream = FileOutputStream(outputFile)
    
    // 正确的远程音频轨道访问
    room.remoteParticipants.values.forEach { participant ->
        val audioPublication = participant.audioTrackPublications.values.firstOrNull()
        val audioTrack = audioPublication?.track as? RemoteAudioTrack
        
        if (audioTrack != null) {
            Log.d("SimplePCM", "设置音频录制: ${participant.identity}")
            
            audioTrack.setAudioDataProcessor(
                participant = participant,
                trackSid = audioTrack.sid
            ) { participant, _, audioData, _, _, _, _, _ ->
                scope.launch {
                    try {
                        val audioBytes = ByteArray(audioData.remaining())
                        audioData.duplicate().get(audioBytes)
                        outputStream?.write(audioBytes)
                        outputStream?.flush()
                        
                        Log.d("SimplePCM", "录制: ${participant.identity} - ${audioBytes.size} bytes")
                    } catch (e: Exception) {
                        Log.e("SimplePCM", "写入失败", e)
                    }
                }
            }
        } else {
            Log.w("SimplePCM", "参与者 ${participant.identity} 没有音频轨道")
        }
    }
}
```

### 方案3：监听房间事件

确保监听新参与者加入和轨道订阅事件：

```kotlin
// 在您的MainActivity中添加
room.events.collect { event ->
    when (event) {
        is RoomEvent.TrackSubscribed -> {
            if (event.track is RemoteAudioTrack) {
                Log.d("Debug", "新的远程音频轨道: ${event.participant.identity}")
                // 重新设置音频录制
                setupAudioRecordingForParticipant(event.participant)
            }
        }
        is RoomEvent.ParticipantConnected -> {
            Log.d("Debug", "新参与者加入: ${event.participant.identity}")
        }
        else -> {}
    }
}
```

## 测试步骤

### 1. 单设备测试
如果只有一个设备，录制不到声音是正常的，因为没有其他参与者。

### 2. 双设备测试
1. 使用两个设备（或一个设备+浏览器）
2. 都加入同一个房间
3. 确保至少一个设备开启麦克风
4. 另一个设备运行录制功能

### 3. 浏览器测试
访问 LiveKit 的示例页面或使用以下简单HTML：

```html
<!DOCTYPE html>
<html>
<head>
    <title>LiveKit Test</title>
    <script src="https://unpkg.com/livekit-client/dist/livekit-client.umd.js"></script>
</head>
<body>
    <button onclick="connect()">连接并开启麦克风</button>
    <script>
        async function connect() {
            const room = new LiveKit.Room();
            await room.connect('wss://ls1.dearlink.com', 'your-token');
            await room.localParticipant.enableCameraAndMicrophone();
            console.log('已连接并开启麦克风');
        }
    </script>
</body>
</html>
```

## 日志检查清单

在logcat中查找以下关键信息：

```
✅ 应该看到的日志：
- "房间连接成功"
- "远程参与者加入"
- "发现远程音频轨道"
- "录制: [participant] - [bytes] bytes"

❌ 问题指标：
- "没有远程参与者"
- "参与者没有音频轨道"
- "写入失败"
- 长时间没有录制日志
```

## 文件验证

检查输出文件：

```kotlin
val outputFile = File(filesDir, "output.pcm")
if (outputFile.exists()) {
    val fileSize = outputFile.length()
    Log.d("Debug", "输出文件大小: $fileSize 字节")
    
    if (fileSize == 0L) {
        Log.w("Debug", "输出文件为空 - 没有录制到音频")
    } else {
        // 估算时长（假设16kHz单声道16bit）
        val durationSeconds = fileSize / (16000 * 1 * 2)
        Log.d("Debug", "录制时长约: $durationSeconds 秒")
    }
} else {
    Log.e("Debug", "输出文件不存在")
}
```

## 常见解决方案

1. **确保有其他参与者**：使用浏览器或另一个设备加入房间
2. **检查权限**：确保RECORD_AUDIO权限已授予
3. **检查网络**：确保能正常连接到LiveKit服务器
4. **使用标准参数**：采样率使用16000或44100Hz
5. **添加详细日志**：在关键步骤添加日志输出
6. **检查轨道状态**：确保远程轨道处于LIVE状态

通过这些调试步骤，您应该能够定位并解决音频录制问题。