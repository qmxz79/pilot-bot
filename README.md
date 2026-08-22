# Pilot-bot

> 一个开源、免费、无广告的 Android 导航辅助机器人：把机械的导航播报变成"坐在副驾的朋友"，陪你开车、陪你聊天。

## 项目状态

**M5 打磨完成（代码）：人设 JSON 导入/导出、文字输入兜底、首次启动引导。DESIGN §5 七层 + MVP 清单全部落地，待真机验收。**

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
2. 申请[高德开放平台](https://lbs.amap.com/) key（包名 `com.qmxz.pilotbot` + `./gradlew signingReport` 的 SHA1）
3. 复制 `android/local.properties.template` 为 `android/local.properties`，填入 `sdk.dir` 与 `amap.api.key`
4. Android Studio 打开 `android/` 目录，Sync 后运行到真机

> ⚠️ `local.properties` 已在 `.gitignore` 中，**永远不要**把 key 提交进仓库。

## M0 真机验收

见 `docs/M0-DELIVERY.md` 第四节（7 项验收清单，重点：Stop 后立即 Start 的三类场景、播报原文是否拿到）。

## License

[MIT](LICENSE)
