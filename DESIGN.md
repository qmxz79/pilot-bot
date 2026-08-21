# Pilot-bot 设计文档（DESIGN）

> 一个 Android 导航辅助机器人：把机械的导航播报变成"坐在副驾的朋友"，陪你开车、陪你聊天。
>
> 版本：v0.2（开源工具定位） · 状态：待评审

---

## 1. 项目定位

### 1.1 一句话

一个**开源、免费、无广告**的 Android 导航辅助工具：自带导航，在导航之上叠一层有性格的大模型"副驾朋友"，把冷冰冰的导航数据改写成有人味的对话，同时陪独自开车的司机聊天解闷。

### 1.2 它是什么

- 一个 **开源工具**，首要用户是作者自己和朋友，不是商业产品。
- 一个 **Android 原生 App**，导航 + 语音伴侣一体。
- 复用高德/百度**导航 SDK**，不重新造导航。
- 一个大模型 **LLM Provider 可插拔**的对话层（云端 API 或本地小模型）。
- 一个可**自定义人设**的语音人格（名字、声线、语气、口头禅）。

### 1.3 它不是什么

- **不是**一个商业产品：不做用户系统、不做账号体系、不做云服务、不接广告。
- **不是**另一个导航软件（不和高德/百度在导航能力上竞争）。
- **不是**去"读取用户正在使用的高德/百度 App 数据"（技术上不可行，见 §2.1）。
- **不是**一个纯闲聊机器人——它必须**扎根于实时驾驶情境**，闲聊是情境之上的延伸。

### 1.4 核心场景

- 一个人长途自驾 / 通勤，路上无聊、易犯困。
- 想要一个"什么都知道、还能聊得来、会表达自己想法"的副驾，而不是只会念"前方 500 米右转"的机械语音。

### 1.5 目标与成功标准

**目标**：做一个开源、免费、无广告的工具，作者自己用、也发给朋友用。

**成功标准**（不是日活、营收，而是三条朴素的标准）：
1. **作者自己愿不愿意用**（而不是打开百度）；
2. **朋友装起来顺不顺**（配置 5 分钟内能跑起来）；
3. **用久了它有没有"人味"、能不能让人笑一下**。

这三条直接约束设计：配置必须傻瓜化、依赖必须少、核心闭环必须足够有"人味"。

---

## 2. 关键技术决策

### 2.1 为什么导航必须内嵌，而不是"偷读"现成导航 App

高德地图、百度地图这两个**成品 App 不开放任何 API**，第三方无法读取它们当前正在进行的导航会话（当前路线、剩余距离、下一路口）。

但两家的**导航 SDK**会把实时导航数据以回调形式完整吐给开发者：

| 数据 | 来源 |
| --- | --- |
| 剩余距离 / 剩余时间 / 当前道路 / 下一路口 / 拥堵长度 / 红绿灯 | 高德 `NaviInfo`、百度导航 SDK 对应回调 |
| **导航播报原文**（"前方 500 米靠右行驶"） | 高德 `AMapNaviListener#onGetNavigationText` |
| 任意区域/道路的交通态势（路网级） | 高德[交通态势 Web API](https://lbs.amap.com/api/webservice/guide/api/trafficstatus) |

**结论**：使用形态是"打开 **Pilot-bot 这个 App**"，它自带导航 + 机器人，而不是"打开高德再启动机器人"。

### 2.2 大模型接入：一个接口覆盖大多数

DeepSeek、通义千问、智谱、Kimi、MiniMax 等国产主流模型几乎全部提供 **OpenAI 兼容接口**。因此只需实现一个 "OpenAI 兼容客户端"，把 `base_url / api_key / model` 做成可配置项，即可覆盖绝大多数云端模型，无需逐家写适配器。

本地小模型走独立分支：llama.cpp 的 Android 绑定，跑 Qwen 0.5B~3B 级中文小模型，做**离线兜底 / 隐私模式**。

### 2.3 平台与语言

- **平台**：Android 原生 App（Kotlin）。
- **理由**：高德/百度导航 SDK 是原生 SDK，Kotlin 集成最顺；语音（ASR/TTS）原生能力也最丰富。
- 后台保活：前台服务 + 通知，保证息屏 / 切后台时导航与闲聊不断。

---

## 3. 系统架构

### 3.1 模块总览

```
┌──────────────┐   ┌───────────────┐   ┌───────────────┐   ┌──────────┐
│ ① 导航数据层  │ → │ ② 情境构建层   │ → │ ③ 大模型对话层  │ → │ ④ 语音层  │
│ 高德/百度SDK │   │ 机械播报→结构化 │   │ LLM + 人设     │   │ ASR / TTS│
│ NaviInfo 回调│   │ 上下文         │   │ 流式输出        │   │          │
└──────────────┘   └───────────────┘   └───────────────┘   └──────────┘
        ↑                                                       │
        │                ⑥ 沿途数据层（POI / 行政区划）           │
        │                                                       │
        └─────── ⑤ 安全策略层（驾驶负荷评分，贯穿全程）────────────┘
```

| 模块 | 职责 | 关键接口 |
| --- | --- | --- |
| ① 导航数据层 | 内嵌导航 SDK，采集 `NaviInfo` + 机械播报原文 | `NavigationProvider`（§5.1） |
| ② 情境构建层 | 把数值 + 机械播报压缩成"此刻发生了什么" | `ContextBuilder`（§5.2） |
| ③ 大模型对话层 | 人设 + 情境 + 历史 + 用户输入 → 流式生成 | `LlmProvider`（§5.3） |
| ④ 语音层 | ASR 听、TTS 说，均要求流式 | `SpeechToText` / `TextToSpeech`（§5.4） |
| ⑤ 安全策略层 | 驾驶负荷评分，决定"说多少、说不说" | `DrivingLoadEstimator`（§5.5） |
| ⑥ 沿途数据层 | 反向地理编码、周边 POI、行政区划叙事 | `EnRouteDataSource`（§5.6） |
| ⑦ 人设层 | 人格 preset 的存储、加载、prompt 生成 | `PersonaStore`（§5.7） |

### 3.2 依赖方向

```
语音层 ← 对话层 ← 情境构建层 ← 导航数据层
             ↖                ↖
         人设层          安全策略层
             ↑
         沿途数据层
```

单向依赖、接口面向抽象，便于替换导航厂商 / 模型厂商。

---

## 4. 核心闭环（工具成立的那一下）

```
高德播报 "前方 3 公里拥堵，预计通行 6 分钟"
        ↓ ② 情境构建层
StructuredEvent {
  type    = CONGESTION,
  distance= 3000m,
  duration= 360s,
  road    = "建国门桥",
  load    = L2_MILD,
}
        ↓ ③ 大模型对话层（注入人设 + 沿途数据）
"前面建国门那儿堵上了，得磨五六分钟。不着急，
 我正好给你讲讲建国门这块的老故事……"
        ↓ ④ 语音层（流式 TTS）
```

这是本项目的**最小可行内核**：机械播报 → 结构化 → LLM 改写 → 有人味的语音。其余功能都是在此闭环上生长出来的。

---

## 5. 接口定义（Kotlin 草案）

### 5.1 导航数据层

```kotlin
/** 一次导航会话的数据源，屏蔽高德 / 百度差异 */
interface NavigationProvider {
    /** 开始导航 */
    suspend fun startNavi(route: RoutePlan)
    suspend fun stopNavi()
    /** 是否正在导航中 */
    val isNavigating: Boolean
    fun addListener(listener: NaviEventListener)
    fun removeListener(listener: NaviEventListener)
}

/** 导航实时状态快照（由 SDK 的 NaviInfo 等回调聚合而来） */
data class NaviState(
    val remainingDistanceMeters: Int,
    val remainingTimeSeconds: Int,
    val currentRoadName: String?,
    val currentSpeedKmh: Float,
    val nextTurn: TurnInstruction?,
    val jamLengthMeters: Int,
    val jamDurationSeconds: Int,
    val isNight: Boolean,
)

/** 下一路口的转向指令 */
data class TurnInstruction(
    val action: TurnAction,   // TURN_LEFT / KEEP_RIGHT / ENTER_ROUNDABOUT ...
    val distanceMeters: Int,
)

/** 导航事件回调（onGetNavigationText 是机械播报的入口） */
interface NaviEventListener {
    fun onNaviStateChanged(state: NaviState)
    /** 导航播报原文，如 "前方 500 米靠右行驶" */
    fun onNaviText(text: String)
    fun onRouteCalculated(route: RoutePlan)
    fun onArrived()
    fun onNaviError(error: NaviError)
}
```

### 5.2 情境构建层

```kotlin
/** 把机械播报 + 数值，归类为结构化事件 */
data class StructuredEvent(
    val type: EventType,             // CONGESTION / TURN_AHEAD / ARRIVE_SOON ...
    val naviText: String,            // 原始播报
    val naviState: NaviState,
    val load: DrivingLoadLevel,      // 由安全策略层给出
)

interface ContextBuilder {
    /** 机械播报 → 结构化事件 */
    fun buildEvent(naviText: String, state: NaviState): StructuredEvent

    /** 生成喂给 LLM 的"驾驶情境"文本块 */
    fun buildContextBlock(event: StructuredEvent): String
}
```

### 5.3 大模型对话层

```kotlin
interface LlmProvider {
    val supportsStreaming: Boolean
    /** 流式对话；每来一段增量调用一次 onDelta */
    suspend fun streamChat(
        messages: List<ChatMessage>,
        config: GenerationConfig,
        onDelta: (String) -> Unit,
    ): ChatResult
}

data class ChatMessage(
    val role: Role,       // SYSTEM / USER / ASSISTANT
    val content: String,
)

data class GenerationConfig(
    val temperature: Double = 0.8,
    val maxTokens: Int = 512,
    val stopSequences: List<String> = emptyList(),
)

/** 端点可配置，覆盖 OpenAI 兼容的绝大多数云端模型 */
data class LlmEndpoint(
    val provider: String,        // openai-compatible / local
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)
```

**云端实现**：`OpenAiCompatibleProvider`（一个类覆盖 DeepSeek / 通义 / 智谱 / Kimi / MiniMax）。
**本地实现**：`LocalLlmProvider`（llama.cpp 绑定，离线兜底 / 隐私模式）。

### 5.4 语音层

```kotlin
interface SpeechToText {
    /** 唤醒词 / 按键触发，返回识别文字；车内场景需去噪 + VAD */
    suspend fun listen(): String
    fun cancel()
}

interface TextToSpeech {
    /** 流式朗读；返回可取消的会话，便于被导航播报打断 */
    suspend fun speak(text: String, onStart: () -> Unit): SpeakSession
    fun interrupt()
}

interface SpeakSession { suspend fun await() }
```

> MVP 阶段：语音输入先用**按键说话**兜底，甚至文字输入兜底；全双工（随时插话）放后期。

### 5.5 安全策略层

```kotlin
/** 驾驶负荷四档，决定"说多少、说不说" */
enum class DrivingLoadLevel {
    L0_SILENT,       // 静默：拥堵爬行 / 急弯 / 恶劣天气 → 闭嘴，只留导航
    L1_RESTRAINED,   // 克制：复杂路口 / 变道密集 → 只回应，不主动找话
    L2_MILD,         // 轻度：轻度拥堵 / 临近动作 → 降低主动频率，回应变短
    L3_ACTIVE,       // 活跃：高速巡航 / 路况简单 → 随便聊
}

interface DrivingLoadEstimator {
    fun estimate(state: NaviState): DrivingLoadLevel
}
```

**两条铁律**（安全策略层的顶层约束，凌驾于一切人设之上）：
1. **导航播报永远优先**，可打断任何正在进行的闲聊。
2. **默认短句**，不诱导司机做复杂决策。

### 5.6 沿途数据层

```kotlin
interface EnRouteDataSource {
    /** 反向地理编码 + 周边 POI（景点 / 美食 / 历史） */
    suspend fun nearbyPoi(lat: Double, lng: Double, radiusMeters: Int): List<Poi>
    /** 当前行政区划（跨省/市/县界触发叙事） */
    suspend fun currentAdminArea(lat: Double, lng: Double): AdminArea
    /** 按剩余路线预取沿途 POI，进隧道没信号也不哑 */
    suspend fun prefetchAlongRoute(route: RoutePlan)
}

data class Poi(val name: String, val category: String, val lat: Double, val lng: Double)

data class AdminArea(val province: String, val city: String, val district: String)
```

### 5.7 人设层

```kotlin
data class Persona(
    val id: String,
    val name: String,              // 机器人名字
    val voiceId: String,           // 联动 TTS 音色
    val catchphrases: List<String>,// 口头禅
    val humorLevel: Int,           // 0~10 幽默度
    val tone: Tone,                // 活泼 / 沉稳 / 毒舌 ...
    val knowledgePrefs: List<String>, // 知识偏好：历史 / 美食 / 时事 ...
) {
    /** 由人设生成 system prompt */
    fun buildSystemPrompt(): String
}

interface PersonaStore {
    fun list(): List<Persona>
    fun get(id: String): Persona?
    fun save(persona: Persona)
    fun delete(id: String)
}
```

人设 preset 可切换、可导出分享（JSON）。

---

## 6. 数据流

### 6.1 主动播报（导航 → 机器人）

```
导航 SDK onNaviText ──► ContextBuilder.buildEvent ──► DrivingLoadEstimator.estimate
                                                          │
                    ┌───────────── L0 ? ──► 丢弃，仅保留机械播报
                    │
                    └─► LlmProvider.streamChat(system=人设, user=情境块)
                              │
                              ▼
                    TextToSpeech.speak(流式)  ◄── 若导航播报出现则 interrupt
```

### 6.2 用户主动聊天

```
按键/唤醒 ──► SpeechToText.listen ──► LlmProvider.streamChat(人设+情境+历史+用户)
                                              │
                                              ▼
                                  TextToSpeech.speak(流式)
```

### 6.3 沿途叙事触发

```
定位变化 ──► currentAdminArea 变化 ──► 生成"你正进入 XX，这里是……"
          ──► nearbyPoi 命中偏好类别 ──► 生成相关闲聊
```

---

## 7. 关键数据模型汇总

| 模型 | 说明 |
| --- | --- |
| `RoutePlan` | 路线（起终点、途经点、总距离/时间） |
| `NaviState` | 导航实时快照 |
| `TurnInstruction` | 下一路口指令 |
| `StructuredEvent` | 结构化驾驶事件 |
| `DrivingLoadLevel` | 驾驶负荷四档 |
| `ChatMessage` / `GenerationConfig` | 对话层 |
| `LlmEndpoint` | 云端模型端点配置 |
| `Persona` | 人设 |
| `Poi` / `AdminArea` | 沿途数据 |

---

## 8. MVP 范围（第一版最小闭环）

聚焦"单人可跑通的开源工具"，砍掉一切服务化的东西（用户系统、云服务、分享平台）。

| # | 目标 | 说明 |
| --- | --- | --- |
| 1 | 内嵌高德导航 | 拿到 `NaviInfo` 与 `onGetNavigationText` |
| 2 | 播报 → LLM 改写 → 流式 TTS | 核心闭环跑通 |
| 3 | 一个可切换预设人设 | 验证"人味" |
| 4 | 按键说话 / 文字输入兜底 | 暂不接 ASR 全链路 |
| 5 | 驾驶负荷 L0 / L3 两档 | 先做"复杂路况闭嘴" |
| 6 | 傻瓜化配置（首次启动引导） | 朋友 5 分钟内能跑起来 |

**明确不做（MVP 之外）**：全双工语音、沿途 POI 预取、账号/云服务、广告、车机/CarPlay。

**本地小模型**：从"后期打磨"提前为**高优先级**——它是"无 key、纯离线、隐私"的关键，也是开源分发最顺的形态（见 §12）。

---

## 9. 里程碑建议

1. **M0 — 技术验证**：高德 SDK 回调能否顺畅拿到（本项目最大不确定性，最先验证）。
2. **M1 — 核心闭环**：播报 → 改写 → 流式 TTS。
3. **M2 — 可聊天**：按键说话 + 人设 + 历史上下文。
4. **M3 — 安全策略**：驾驶负荷分级接入。
5. **M4 — 沿途数据**：POI / 行政区划叙事。
6. **M5 — 打磨**：本地小模型、全双工、人设 JSON 分享。

---

## 10. 风险与开放问题

| 风险 / 问题 | 说明 | 应对 |
| --- | --- | --- |
| 导航 SDK 回调实时性 | 能否拿到足够细腻、低延迟的数据 | M0 最先验证 |
| 语音延迟 | 车内场景对延迟敏感，回应慢会很怪 | 流式 + 短句优先 |
| 车内噪声 / 回声 | ASR 与 TTS 同时工作难 | MVP 用按键，后期再做全双工 |
| 分心驾驶伦理 | 即使开源，也需守住安全底线 | 驾驶负荷分级 + 导航优先铁律 |
| 本地小模型效果 | 3B 以下中文对话质量有限 | 主走云端，本地作离线/隐私模式 |
| 高德/百度 SDK 合规 | 闭源 SDK + 每人自己的 key | 配置傻瓜化，见 §12 |
| API key 泄露 | 不能把 key 提交进仓库 | BYOK + .gitignore + 本地存储 |

---

## 11. 下一步

- [ ] 评审本设计（平台 / 技术栈 / 模块划分）
- [ ] 立项确认：申请高德导航 SDK key
- [ ] M0 技术验证：搭 Android 骨架，验证导航回调

---

## 12. 开源与发布

### 12.1 开源形态

- **代码全开源**，但**不提交任何 key**（高德 key、模型 key）。
- 采用 **BYOK（Bring Your Own Key）**：每个使用者自己申请、自己填写。
- 提供**本地小模型**作为无 key、纯离线、隐私的选项（优先做，见 §8）。

### 12.2 配置项

| 配置 | 来源 | 是否必须 |
| --- | --- | --- |
| 高德导航 SDK key | [高德开放平台](https://lbs.amap.com/) 免费注册 | 必须 |
| 模型端点 `LlmEndpoint` | 用户自己的 DeepSeek / 通义 / 智谱等 | 二选一 |
| 本地模型文件 | 随 App 打包或首次下载 | 二选一 |
| 人设 preset | 内置 + 用户自定义（JSON） | 可选 |

### 12.3 首次启动引导（傻瓜化目标）

用户首次打开 App，按引导完成：
1. 粘贴高德 key；
2. 选模型：云端（填 `base_url / api_key / model`）或本地小模型；
3. 选人设（或跳过用默认）。

目标：**5 分钟内从下载到"能聊起来"**。

### 12.4 License 建议

- 主体代码：**MIT** 或 **Apache-2.0**（宽松，方便朋友直接用）。
- 注意：**高德/百度 SDK 是闭源的**，随 App 分发时需遵守其各自协议；开源仓库里只放自己的代码，SDK 通过 Gradle 依赖引入，不复制其二进制进仓库。
- 人设 preset、prompt 文案：可单独用一个宽松协议（如 CC0）便于分享。

### 12.5 发布方式

- 源码：GitHub 仓库。
- 成品：优先 **GitHub Releases 直接放 APK**（开源工具，无需上架应用商店；避开分心驾驶相关审核摩擦）。
- 后续可选：F-Droid 等开源应用市场。
