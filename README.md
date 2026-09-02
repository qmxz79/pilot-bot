# Pilot-bot

> 一个开源、免费、无广告的 Android 导航辅助机器人：把机械的导航播报变成"坐在副驾的朋友"，陪你开车、陪你聊天。

## 项目状态

**安全化迭代进行中：已加入首次隐私同意门槛、release 禁止明文网络、Keystore 密钥保存，以及独立 LLM/ASR/TTS 配置。DESIGN §5 七层 + MVP 清单已落地，仍待真机验收。**

- 代码：`android/`（包名 `com.qmxz.pilotbot`）
- 设计：`DESIGN.md`
- 交付与验收清单：`docs/M0-DELIVERY.md`、`docs/M1-M2-DELIVERY.md`

## 功能规划

- 内嵌高德/百度导航，读取实时导航数据（剩余距离、时间、道路、播报原文）
- 大模型"副驾朋友"：把机械播报改写成有人味的对话，陪司机聊天解闷
- 可自定义人设（名字、声线、语气、口头禅）
- 驾驶负荷分级安全策略（复杂路况自动闭嘴）
- 云端大模型 API 或本地小模型双模式（BYOK）

## 目录结构

```
android/                  Android 工程
  app/src/main/java/com/qmxz/pilotbot/
    MainActivity.kt       测试界面（导航 + 模拟播报 + 说话 + 对话记录 + 设置入口）
    SettingsActivity.kt   设置（模型端点 + 人设预设 + 语音模式）
    navi/                 导航数据层（coordinator 架构）
    context/              情境构建层（播报分类 → 结构化事件）
    llm/                  大模型对话层（OpenAI 兼容 SSE 流式 + 多轮历史）
    tts/                  语音层（系统 TTS，句子流式 + 队列空闲回调）
    asr/                  语音识别层（系统 SpeechRecognizer）
    voice/                语音交互层（按键/连续/唤醒词三模式）
    copilot/              编排层（播报改写 + 多轮聊天 + 打断 + 主动叙事）
    safety/               安全策略层（驾驶负荷四档 L0-L3）
    enroute/              沿途数据层（定位 SDK 逆地理 + 行政区划叙事）
    config/  persona/     配置与人设（内置预设 + 自定义）
docs/                     项目文档
DESIGN.md                 设计文档
```

## 本地构建（需要真机）

1. 环境：JDK 17 + Android Studio（含 Android SDK 34）
2. 拉取高德 SDK（`maven.amap.com` 已永久不可用，改为离线引入）：
   - Windows：`powershell -ExecutionPolicy Bypass -File .\fetch-amap-sdk.ps1`
   - macOS/Linux：`./fetch-amap-sdk.sh`
   （首次下载约 224MB；脚本幂等、支持断点续传，产物在 `android/app/libs/` 与 `jniLibs/`，gitignored）
3. 申请[高德开放平台](https://lbs.amap.com/) key（包名 `com.qmxz.pilotbot` + `./gradlew signingReport` 的 SHA1）
4. 复制 `android/local.properties.template` 为 `android/local.properties`，填入 `sdk.dir` 与 `amap.api.key`
5. Android Studio 打开 `android/` 目录，Sync 后运行到真机

> ⚠️ `local.properties` 已在 `.gitignore` 中，**永远不要**把 key 提交进仓库。

## 安全与验收状态

| 项目 | 代码 | 自动测试 | 真机/发布验证 |
| --- | --- | --- | --- |
| 高德隐私同意门槛 | 已实现 | JVM 测试通过；待仪器测试 | 待验证拒绝、同意与重启路径 |
| Release 明文 HTTP | 已禁用 | Manifest 静态检查 | 待验证 release 安装包；HTTP Ollama 仅 debug |
| 密钥存储 | Android Keystore + 旧配置迁移 | JVM 测试通过 | 待验证设备升级/Keystore 失效恢复 |
| LLM / ASR / TTS 独立配置 | 已实现，兼容旧 LLM 配置回退 | JVM 测试通过 | 待验证三家不同供应商组合 |
| 本地诊断日志 | 已实现；可在设置中关闭并清除，内容会脱敏 | JVM 测试通过 | 待验证失败场景与关闭后不落盘 |

生产版本只允许 HTTPS。需要连接 HTTP 局域网 Ollama 时，请使用 debug 构建；不要为了它放宽 release 的网络安全策略。

## M0 真机验收

见 `docs/M0-DELIVERY.md` 第四节（7 项验收清单，重点：Stop 后立即 Start 的三类场景、播报原文是否拿到）。

## License

[MIT](LICENSE)
