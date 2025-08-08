# PCM 文件音频处理示例

本文档展示如何使用 LiveKit Android SDK 的音频功能实现：
- **输入**：从 PCM 文件读取音频数据并发布到房间
- **输出**：拦截远程参与者的音频数据并写入文件

## 快速开始

### 1. 准备 PCM 文件

首先准备一个 PCM 音频文件，格式要求：
- **采样率**：44100 Hz
- **位深度**：16-bit
- **声道数**：2（立体声）
- **字节序**：小端序（Little Endian）
- **文件格式**：原始 PCM 数据（无文件头）

### 2. 使用简化示例

```kotlin
import io.livekit.android.examples.SimplePCMExample

class MainActivity : AppCompatActivity() {
    private lateinit var room: Room
    private lateinit var pcmExample: SimplePCMExample
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建房间
        room = LiveKit.create(applicationContext)
        pcmExample = SimplePCMExample(this, room)
        
        // 准备文件路径
        val inputFile = File(filesDir, "input.pcm")    // 您的输入文件
        val outputFile = File(filesDir, "output.pcm")  // 录制输出文件
        
        lifecycleScope.launch {
            try {
                // 连接到房间
                room.connect("wss://your-livekit-server", "your-token")
                
                // 开始音频处理
                pcmExample.startExample(inputFile, outputFile)
                
                // 运行 60 秒后停止
                delay(60000)
                pcmExample.stop()
                
                Log.d("MainActivity", "音频处理完成，输出保存在: ${outputFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e("MainActivity", "音频处理失败", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        pcmExample.stop()
        room.disconnect()
    }
}
```

## 详细示例

### 完整功能示例

```kotlin
import io.livekit.android.examples.PCMFileAudioExample

class AdvancedAudioActivity : AppCompatActivity() {
    private lateinit var pcmExample: PCMFileAudioExample
    
    private fun setupAdvancedExample() {
        pcmExample = PCMFileAudioExample(this, room)
        
        lifecycleScope.launch {
            // 显示音频格式信息
            Log.d("Audio", pcmExample.getAudioInfo())
            
            // 开始录制远程音频（每个参与者单独文件）
            val outputDir = File(filesDir, "recordings")
            pcmExample.startRecordingRemoteAudio(outputDir)
            
            // 开始播放 PCM 文件（循环播放）
            val inputFile = File(filesDir, "background_music.pcm")
            pcmExample.startPlayingFromPCMFile(inputFile, loop = true)
        }
    }
}
```

## PCM 文件格式说明

### 文件结构
```
PCM 文件结构（16-bit 立体声）：
[样本1-左声道] [样本1-右声道] [样本2-左声道] [样本2-右声道] ...
     2字节         2字节          2字节         2字节
```

### 数据计算
```kotlin
// 音频参数
val sampleRate = 44100    // 采样率 44.1kHz
val channels = 2          // 立体声
val bitDepth = 16         // 16-bit
val bytesPerSample = 2    // 16-bit = 2 bytes
val bytesPerFrame = channels * bytesPerSample  // 4 bytes per frame

// 计算文件大小和时长
val fileSize = pcmFile.length()                           // 文件大小（字节）
val totalFrames = fileSize / bytesPerFrame                // 总帧数
val durationSeconds = totalFrames.toDouble() / sampleRate // 时长（秒）
val dataRatePerSecond = sampleRate * bytesPerFrame        // 每秒数据量：176,400 字节
```

## 创建测试 PCM 文件

### 使用工具函数创建

```kotlin
import io.livekit.android.examples.PCMFileHelper

// 创建 10 秒的测试音频文件（440Hz 正弦波）
val testFile = File(filesDir, "test_audio.pcm")
PCMFileHelper.createTestPCMFile(
    outputFile = testFile,
    sampleRate = 44100,
    channels = 2,
    durationSeconds = 10,
    frequency = 440.0  // A4 音符
)

// 验证文件格式
val isValid = PCMFileHelper.validatePCMFile(testFile, 44100, 2)
if (isValid) {
    Log.d("PCM", "测试文件创建成功")
} else {
    Log.e("PCM", "测试文件格式错误")
}
```

### 使用 FFmpeg 转换现有音频文件

```bash
# 将任意音频文件转换为 PCM 格式
ffmpeg -i input.mp3 -f s16le -ar 44100 -ac 2 output.pcm

# 参数说明：
# -f s16le  : 16-bit 小端序 PCM 格式
# -ar 44100 : 采样率 44.1kHz  
# -ac 2     : 2 声道（立体声）
```

## 文件路径建议

### 内部存储（推荐）
```kotlin
// 应用私有目录（无需权限）
val inputFile = File(context.filesDir, "audio/input.pcm")
val outputFile = File(context.filesDir, "recordings/output.pcm")

// 缓存目录（可被系统清理）
val cacheFile = File(context.cacheDir, "temp_audio.pcm")
```

### 外部存储（需要权限）
```kotlin
// 需要 WRITE_EXTERNAL_STORAGE 权限
val externalDir = context.getExternalFilesDir("audio")
val inputFile = File(externalDir, "input.pcm")
val outputFile = File(externalDir, "output.pcm")
```

## 性能优化建议

### 1. 缓冲区大小优化
```kotlin
// 推荐的缓冲区大小（20-50ms 音频数据）
val bufferSizeMs = 20  // 20 毫秒
val bufferSizeFrames = (sampleRate * bufferSizeMs) / 1000
val bufferSizeBytes = bufferSizeFrames * bytesPerFrame

// 44.1kHz 立体声 16-bit，20ms ≈ 3528 字节
```

### 2. 异步处理
```kotlin
// 在后台线程处理文件 I/O
scope.launch(Dispatchers.IO) {
    val audioData = readAudioFromFile()
    bufferProvider.addAudioData(audioData)
}

// 在后台线程写入文件
scope.launch(Dispatchers.IO) {
    outputStream.write(audioBytes)
    outputStream.flush()
}
```

### 3. 内存管理
```kotlin
// 重用缓冲区
private val reusableBuffer = ByteArray(4096)

// 及时清理资源
try {
    // 音频处理
} finally {
    inputStream?.close()
    outputStream?.close()
}
```

## 权限配置

在 `AndroidManifest.xml` 中添加：

```xml
<!-- 录音权限（麦克风，可选） -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 如果使用外部存储 -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

## 故障排除

### 常见问题

1. **文件不存在**
   ```kotlin
   if (!inputFile.exists()) {
       Log.e("PCM", "输入文件不存在: ${inputFile.absolutePath}")
       return
   }
   ```

2. **文件格式错误**
   ```kotlin
   // 检查文件大小是否为帧大小的整数倍
   val fileSize = pcmFile.length()
   val bytesPerFrame = 4  // 16-bit 立体声
   if (fileSize % bytesPerFrame != 0L) {
       Log.w("PCM", "文件大小不正确，可能不是有效的 PCM 文件")
   }
   ```

3. **播放速度不正确**
   ```kotlin
   // 确保延迟时间正确计算
   val bufferDurationMs = (bufferSizeBytes * 1000) / (sampleRate * bytesPerFrame)
   delay(bufferDurationMs.toLong())
   ```

4. **音质问题**
   - 确认 PCM 文件采样率为 44100 Hz
   - 确认字节序为小端序
   - 检查是否有音频截断或失真

### 调试信息
```kotlin
Log.d("PCM", """
    文件信息:
    - 大小: ${file.length()} 字节
    - 时长: ${file.length() / (44100 * 4)} 秒
    - 格式: 44.1kHz, 16-bit, 立体声
""".trimIndent())
```

## 完整示例文件

- `PCMFileAudioExample.kt` - 完整功能示例
- `SimplePCMExample.kt` - 简化使用示例
- `PCMFileHelper.kt` - 工具函数集合

通过这些示例，您可以轻松实现从 PCM 文件播放音频并录制远程音频的功能。