<h1 align="center">QYMusic</h1>

<p align="center">
  一款使用 Kotlin 与 Jetpack Compose 构建的 Android 本地音乐播放器。
</p>

QYMusic 面向本地曲库，专注音乐管理、逐句歌词和播放听感调节。应用不申请网络权限，不依赖在线音乐服务，歌曲、歌词、封面、播放统计和歌单数据均保留在设备本地。

## 功能特性

### 本地音乐库

- 通过 Android Storage Access Framework 选择一个或多个音乐目录，并持久保存读取授权
- 递归扫描 MP3、FLAC、M4A、AAC、OGG、OPUS 和 WAV
- 读取标题、艺术家、专辑、时长、码率、采样率、位深和声道数
- 按音频参数标记 Hi-Res、SQ、HQ、标准品质
- 使用文件大小和修改时间缓存扫描结果，未变化的歌曲无需重复读取元数据
- 提供歌曲、艺术家、专辑和歌单四个分类
- 支持搜索、排序，以及置顶艺术家、专辑和歌单
- 支持创建、重命名、删除歌单，设置自定义封面和调整播放顺序

### 播放与媒体控制

- 基于 Media3 ExoPlayer 播放，使用 `MediaSessionService` 支持后台播放
- 支持系统通知栏、锁屏和蓝牙设备的媒体控制
- 支持顺序播放、单曲循环和随机播放
- 支持播放队列查看、拖动排序和“添加到下一首”
- 支持 `0.5x` 到 `2.0x` 播放速度，以及 `-12` 到 `+12` 半音变调
- 支持倒计时和“播完当前曲目”两种定时关闭方式
- 播放页可根据歌曲封面生成背景，并可选音乐律动效果
- 播放间歇或缓冲时保持播放/暂停按钮状态稳定

### 歌词

- 优先读取与音频文件同名、位于同一目录的 `.lrc` 文件
- 无外部歌词时，读取 FLAC 内嵌 Vorbis Comment 歌词
- 支持 `LYRICS`、`LYRIC`、`UNSYNCEDLYRICS` 等常见歌词字段
- 支持 LRC 时间标签、一行多个时间标签、`offset` 和行内逐字时间标签
- 同一时间戳的多行歌词会合并显示
- 支持 UTF-8、UTF-16 LE/BE 和 GB18030 编码
- 无时间轴的纯文本歌词也可以直接显示
- 支持逐句滚动、高亮，以及居左、居中、居右三种对齐方式
- 可调整歌词字号、粗体和非当前歌词模糊程度

### 音效与输出

- 调用 Android 系统均衡器，支持预设和多频段调节
- 支持低音增强、虚拟环绕和响度补偿
- 支持环境混响及混响强度调节
- 探测当前输出设备的采样率、位深、声道和 Hi-Res 能力
- 在设备支持时启用 24-bit PCM 输出链路
- 支持按中置声道实时分离人声和伴奏
- 支持循环声道效果，可调节转速和旋转方向

> 人声/伴奏分离与真正的 bit-perfect 直通均受设备解码、音频路由和 Android 版本限制。具体能力可在设置的“输出”区域查看。

### 播放统计

- 记录每首歌曲的播放次数、累计播放时长和最后播放时间
- 支持按天、周、月、年查看听歌热力图
- 支持查看播放次数前 20 的歌曲和全部歌曲统计
- 支持切换历史时间段或直接选择日期

## 使用方式

1. 安装并打开 QYMusic。
2. 点击“添加音乐目录”，在系统文件选择器中选择存放音乐的文件夹。
3. 点击“重新扫描”。首次扫描需要读取音频元数据，曲库较大时可能需要一些时间。
4. 在歌曲、艺术家、专辑或歌单页面中选择歌曲开始播放。
5. 进入播放页可查看封面、歌词、播放队列、均衡器和更多播放选项。

移除音乐目录只会撤销该目录的扫描授权，不会删除设备中的文件。

## 歌词规则

外部歌词应与音频文件位于同一目录，并使用相同的文件名。例如：

```text
Music/
├── 歌曲名.flac
└── 歌曲名.lrc
```

常见的 LRC 内容示例：

```lrc
[offset:200]
[00:12.35]第一句歌词
[00:18.20][01:05.10]重复出现的歌词
[00:24.00]<00:24.00>逐<00:24.35>字<00:24.70>歌<00:25.05>词
```

如果没有 `.lrc` 文件，QYMusic 会尝试从 FLAC 的内嵌 Vorbis Comment 中读取歌词。

## 构建

### 环境要求

- Android Studio，或可用的 Android SDK 命令行环境
- Android SDK Platform 37
- JDK 17 或更高版本
- Windows、macOS 或 Linux

项目使用 Gradle Wrapper，无需单独安装 Gradle。

### 调试包

Windows：

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux：

```bash
./gradlew assembleDebug
```

调试 APK 输出到：

```text
app/build/outputs/apk/debug/app-debug.apk
```

安装到已连接的设备：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 运行单元测试

```powershell
.\gradlew.bat testDebugUnitTest
```

### 正式包签名

在项目根目录创建 `keystore.properties`：

```properties
storeFile=your-release-key.jks
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

然后执行：

```powershell
.\gradlew.bat assembleRelease
```

如果没有提供 `keystore.properties`，Release 构建会回退到本机 Android 调试签名，便于直接安装测试。发布到应用商店前应改用独立的生产签名，并妥善保管密钥。

## 项目结构

```text
QYMusic/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/qymusic/player/
│       │   │   ├── data/       # 扫描、设置、SQLite、数据模型
│       │   │   ├── lyrics/     # LRC 解析与 FLAC 歌词读取
│       │   │   ├── playback/   # Media3、均衡器、音频处理器
│       │   │   └── ui/         # Compose 页面、控件与 ViewModel
│       │   └── res/            # 图标、主题与字符串资源
│       └── test/               # JVM 单元测试
├── gradle/
│   └── libs.versions.toml      # 依赖版本目录
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew / gradlew.bat
```

主要模块职责：

| 模块 | 职责 |
| --- | --- |
| `data` | SAF 目录管理、音乐扫描、设置持久化、歌曲缓存、播放统计和歌单数据库 |
| `lyrics` | LRC 解码与解析、FLAC Vorbis Comment 歌词提取 |
| `playback` | ExoPlayer 服务、媒体会话、均衡器、输出探测和自定义 PCM 音频效果 |
| `ui` | 音乐库、播放页、设置页、统计页及 Material 3 主题 |

## 技术栈

| 组件 | 版本或用途 |
| --- | --- |
| Kotlin | 2.2.10 |
| Jetpack Compose | Compose BOM 2026.02.01 |
| Material 3 | 应用界面与主题 |
| Media3 ExoPlayer / Session | 1.8.0，音频播放和系统媒体控制 |
| AndroidX DocumentFile | SAF 目录访问 |
| SQLite | 歌曲缓存、播放历史、统计和歌单 |
| Gradle / AGP | Gradle Wrapper / Android Gradle Plugin 9.3.3 |
| 最低 Android 版本 | Android 7.0（API 24） |

## 数据与隐私

- 应用不声明 `INTERNET` 权限，不上传歌曲、歌词、封面或播放记录
- 音乐目录授权通过 SAF 持久化保存
- 界面设置保存在 `SharedPreferences`
- 播放统计、歌曲缓存和歌单保存在名为 `qy_music.db` 的本地 SQLite 数据库中
- 移除目录只会停止扫描，不会修改或删除原始音乐文件

## 常见问题

**添加目录后没有显示歌曲？**

确认目录中包含受支持格式的音频文件，然后执行“重新扫描”。应用只扫描用户明确授权的目录，不会自动扫描整个存储空间。

**为什么不显示歌词？**

检查歌词文件是否与音频文件同名并位于同一目录。对于 FLAC，也可以将带时间标签的歌词写入 `LYRICS` 或 `UNSYNCEDLYRICS` 等 Vorbis Comment 字段。

**均衡器为什么暂时不可用？**

系统音频会话建立后才能应用均衡器。开始播放一首歌曲并等待播放器连接完成后再进行调节。

**为什么高规格音频没有显示为 Hi-Res 直通？**

Android 通常会经过 AudioFlinger 混音，实际输出能力取决于手机、系统版本、耳机或 DAC。应用会探测并展示当前链路能力，但无法保证所有设备都支持绕过系统重采样。

## 已知限制

- 曲库来自用户授权的 SAF 目录，不使用系统媒体库全盘扫描
- 当前不支持在线歌词、在线封面或流媒体播放
- 人声/伴奏分离基于中置声道实时混音，并非 AI 音源分离，效果会因歌曲混音方式不同而变化
- Hi-Res 输出和 bit-perfect 直通受硬件、音频路由及 Android 版本限制
