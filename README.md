# XTune

面向 Android 电视 / 投影仪的第三方音乐播放器客户端。为遥控器与 10 英尺大屏重新设计的界面：顶部标签导航、卡片焦点缩放、快照推滑转场、全屏歌词与播放页。

- **应用名**：XTune
- **包名**：`com.qqmusictv.tv`
- **最低系统**：Android 6.0（推荐 Android 9+）
- **许可证**：GPL-3.0

---

## 功能

- **推荐**：每日推荐、私人 FM、排行榜（热歌 / 新歌 / 飙升 / 特色榜）
- **浏览**：分类歌单、歌手、专辑、MV 入口
- **搜索**：单曲 / 歌手 / 专辑 / 歌单，带搜索历史
- **资料库**：我喜欢、我的歌单、收藏专辑、最近播放
- **播放**：后台播放、锁屏 / 通知栏控制、无缝切歌、淡入淡出、音量均衡
- **歌词**：逐行滚动、逐字高亮，支持拖动浏览后自动回位
- **MV**：全屏播放、清晰度切换
- **遥控**：方向键焦点模型，返回键逐层回退到标签页
- **多 App 共存**：自动检测其他 App（直播、游戏等）开始播放并暂停

## 架构

```
android/app/src/main/java/com/qqmusictv/
├── data/
│   ├── qq/          QQ音乐 Web 接口（搜索 / 歌单 / 榜单 / 歌词 / vkey 取流）
│   ├── repository/  MusicRepository：把 QQ 原始数据映射为 UI 模型
│   ├── network/     网络 DTO
│   └── auth/        登录状态
├── media/           播放器、音效处理、本地回环串流服务
├── ui/              Compose for TV：导航、屏幕、组件、主题、转场
└── util/            崩溃上报、二维码、设备信息
```

数据全部在设备本机直连 `y.qq.com` 获取，不依赖任何自建服务器；音频通过应用内回环 HTTP 服务串流给播放器，以便实时换取 vkey。

## 构建

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

需要 JDK 17、Android SDK（compileSdk 35）。首次构建前请在 `android/local.properties` 中设置 `sdk.dir`。

### 签名

`assembleRelease` 默认使用 debug 证书，方便无密钥环境直接出包。正式分发时在 `android/keystore.properties`（已 gitignore）中提供自己的密钥：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

四个字段齐全时 release 构建自动改用该密钥签名。

### 依赖许可证（合规性）

所有第三方依赖均为 **Apache-2.0 / BSD / MIT** 类宽松许可，与 GPL-3.0 兼容：

- AndroidX / Compose / Media3 / Hilt / OkHttp / Retrofit / Moshi — Apache-2.0
- Bouncy Castle — Apache-2.0
- Robolectric — BSD-3-Clause

唯一需注意的是 `android/app/libs/media3-ffmpeg.jar`：它基于 ExoPlayer FFmpeg Extension 源码构建（Apache-2.0），但运行时链接 FFmpeg native library（LGPL-2.1）。LGPL 与 GPL-3.0 完全兼容，且本项目以动态链接方式使用 FFmpeg native 库（JNI `.so`），符合 LGPL 的"可替换性"要求。

## 登录

1. 首次启动进入连接页，页面会显示电视的局域网地址。
2. 在手机浏览器打开该地址，按提示获取 QQ音乐 Web 版 Cookie 并粘贴。
3. 提交后回到电视即可使用「我喜欢 / 我的歌单」与完整音质播放。

Cookie 仅保存在本机 SharedPreferences 中，不会上传到任何服务器。

---

## 合规免责

本项目为个人学习与研究用途的**非官方**客户端，与腾讯公司无关联，亦未经其认可。QQ音乐及相关商标、音乐与封面版权归各自权利人所有。

本项目**不内置、不分发任何内容源**，仅为媒体播放器；用户需自行通过合法途径获取音乐与歌词数据。

## License

GPL-3.0 — 见 [LICENSE](./LICENSE)。如希望将本项目用于闭源商业产品，请联系作者洽谈独立授权。
