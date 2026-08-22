# M1-M2 交付文档（核心闭环 + 语音聊天 + 全模块骨架）

> 状态：**代码完成，待真机验收**（验收重点：语音三模式选型）。
> M1（核心闭环）与 M2+（聊天闭环 + 剩余模块骨架）一并交付，接续 M0（见 `M0-DELIVERY.md`）。

## 一、M1 交付内容（核心闭环）

**目标**（DESIGN §4 最小可行内核）：把高德机械播报改写成"副驾朋友"的口语并流式念出来。

```
高德 onNaviText / 模拟播报
  → context/SimpleContextBuilder     关键词分类 → 结构化事件（转弯/拥堵/到达/通用）
  → llm/OpenAiCompatibleProvider     OpenAI 兼容 SSE 流式（一个客户端覆盖 DeepSeek/通义/智谱/Kimi/MiniMax）
  → tts/AndroidTextToSpeech          系统 TTS，按句子边界（。！？\n）逐句朗读
  → copilot/CopilotEngine            编排，新播报打断进行中的生成
```

| 层 | 文件 | 说明 |
| --- | --- | --- |
| 配置 | `config/AppConfig.kt` | SharedPreferences 存/取 `LlmEndpoint` + `Persona`，运行时改不用重建 |
| 人设 | `persona/Persona.kt` | 名字/语气/口头禅 → `buildSystemPrompt()`（短句、口语、不照读铁律） |
| 情境 | `context/ContextBuilder.kt` | `classify()` 纯函数分类，`buildContextBlock()` 生成喂给 LLM 的情境块 |
| LLM | `llm/LlmProvider.kt` / `OpenAiCompatibleProvider.kt` | OkHttp 异步 + `suspendCancellableCoroutine`，取消经 `Call.cancel()` 真正断连；`parseSseDelta()` 纯函数 |
| 语音 | `tts/TextToSpeech.kt` / `AndroidTextToSpeech.kt` | 系统 TTS 零依赖，句子流式；`interrupt()` 打断 |
| 编排 | `copilot/CopilotEngine.kt` | 播报 → 改写 → 流式 TTS；新播报取消上一代 + 打断 TTS |
| 界面 | `SettingsActivity` / `MainActivity` | 设置页（模型端点 + 人设）；模拟播报按钮（不开车即可验证闭环） |

**M1 新增依赖**：okhttp 4.12.0、kotlinx-coroutines-android 1.8.1。

**关键实现**：
- 取消语义用 OkHttp 异步 + `Call.cancel()`（而非阻塞读），保证"导航播报打断副驾"可靠生效。
- 设置页填完整 `base_url`（如 `https://api.deepseek.com/v1`），代码拼 `/chat/completions`。

## 二、M2+ 交付内容（语音聊天 + 全模块骨架）

**目标**：M2 可聊天（语音交互 + 多轮历史 + 人设预设），并按用户要求把 DESIGN §5 七层一次性摆齐（安全策略/沿途数据做接口 + 最小实现）。

### 语音交互三模式（设置页切换，真机试选）

| 模式 | 行为 | 状态 |
| --- | --- | --- |
| 按键说话 | 点一下说一句（`listenOnce`），打断进行中的回复 | 实现 |
| 连续对话 | 半双工：听 → 回复 → 回复时自动暂停听（防回声）→ 说完恢复听 | 实现 |
| 唤醒词触发 | 同连续，但只应答以唤醒词（默认「小伴」）开头的语句 | 实现 |
| 全双工 | 边说边听、TTS 不停 | **占位**（设置页禁用，需流式 ASR + 回声消除，后期） |

### 模块

| 层 | 文件 | 说明 |
| --- | --- | --- |
| 语音识别 | `asr/SpeechToText.kt` / `AndroidSpeechToText.kt` | 系统 `SpeechRecognizer` 封装：单次 + 连续 + 开口回调（`onBeginningOfSpeech`）；接口隔离可换云 ASR |
| 语音交互 | `voice/ConversationMode.kt` / `VoiceController.kt` | 三模式驱动；半双工听/说交替 |
| 编排扩展 | `copilot/CopilotEngine.kt` | 多轮历史、双路打断（用户开口 + 新播报）、说话窗口（`speaking`+`generationFinished` 防错乱）、L0 闭嘴 |
| 历史 | `llm/ChatHistory.kt` | 滑动窗口 8 条，聊天全历史携带 → 转移话题天然支持 |
| 安全策略 | `safety/DrivingLoadLevel.kt` / `DrivingLoadEstimator.kt` | L0/L3 两档：低速(<15km/h)→L0 静默；估测器接口 |
| 沿途数据 | `enroute/EnRouteDataSource.kt` / `NoopEnRouteDataSource.kt` | 接口 + 空实现（M4 填真 geocoding） |
| 人设库 | `persona/PersonaStore.kt` | 内置预设（活泼·小伴 / 沉稳·老哥 / 毒舌·损友）+ 自定义，Spinner 切换 |

**新增权限**：`RECORD_AUDIO`（动态申请）。

### 打断与转移话题的实现路径

- 打断：用户开口（ASR `onBeginningOfSpeech`）或新导航播报 → `CopilotEngine.interrupt()`（取消 LLM 流式 + `tts.interrupt()`）→ 进新一轮。两条来源共用同一入口。
- 转移话题：每轮聊天 messages = 人设 system + 当前驾驶情境 + `ChatHistory` 全历史 + 用户输入，模型能接住"刚才说的那个"。
- 防回声：连续/唤醒词为半双工，回复期间暂停麦克风，TTS 队列空闲（`UtteranceProgressListener` 计数）后再恢复。

## 三、本地构建 + 真机验收步骤

1. **配置 key**（沿用 M0）：`android/local.properties` 填 `sdk.dir` 与 `amap.api.key`（`local.properties` 不入库）。
2. **构建**：Android Studio 打开 `android/`，Sync 后 Run 到真机（导航 SDK 需真机）。
3. **先配模型再玩**：打开 App →「设置」→ 填 `base_url`/`api_key`/`model`（如 `https://api.deepseek.com/v1` + `deepseek-chat`）→ 选人设预设 → 选语音模式 → 保存。

## 四、验收清单（真机，放行前置条件）

### M1 核心闭环
1. 「模拟播报」→ 不开车即可验证：改写文本逐字出现（流式）+ 副驾语音念出。
2. 「开始测试导航」→ 真实 `onNaviText` 走同一条链路，播报原文能拿到。
3. 连续两个播报 → 后一个**打断**前一个的语音/文本。
4. 设置里人设（名字/语气/口头禅）改动后生效。

### M2 语音交互选型（重点，记录车内噪声环境）
5. 依次试三种模式（按键 / 连续 / 唤醒词），记录：
   - 车内噪声下识别率；
   - 打断是否灵敏、有无回声自触发；
   - 连续模式回复期间麦克风暂停是否符合预期。
6. 多轮历史：先聊 A 话题，再问"刚才说的那个"，能接上。
7. 唤醒词：只说"小伴，前面堵吗"应答，其余话语不应答。
8. 导航时 L0：拥堵/低速时副驾闭嘴（只留 SDK 导航播报），用户主动聊天不受影响。
9. `RECORD_AUDIO` 权限申请流程正常。

## 五、文档化已知上限

| 项 | 现状 | 升级路径 |
| --- | --- | --- |
| 全双工 | 占位枚举，设置页禁用 | 需流式 ASR + 回声消除（AEC），另行排期 |
| 连续模式打断 | 回复期间麦克风暂停，**不能中途打断** | 真机确认是否必要；必要时上 AEC 或唤醒词打断 |
| L0 判定 | 仅用速度(<15km/h)（M0 coordinator 未填 jam 指标） | M3 接入 `NaviInfo` 拥堵字段补全 L1/L2 |
| 沿途数据 | Noop 空实现 | M4 接高德 Web API / 定位 SDK |
| 识别可用性 | 依赖设备有系统识别服务（SpeechRecognizer） | 若真机无服务，换云 ASR 实现 `SpeechToText` |
| 单测 | 纯函数（`parseSseDelta`/`classify`/`buildSystemPrompt`/`splitSentences`/`SimpleDrivingLoadEstimator`）已保证可测，未建测试基础设施 | 待本地有工具链补最小 JUnit |

## 六、下一步

- 真机验收（本清单第四节），重点定语音模式去留。
- 验收后按 DESIGN §9 继续：M3 安全策略补全（L1/L2 + 拥堵指标）、M4 沿途数据真实接入、M5 打磨（本地小模型 / 人设 JSON 分享 / 全双工）。
