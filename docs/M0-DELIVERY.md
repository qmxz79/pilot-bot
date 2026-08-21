# M0 交付文档（里程碑 1：工程骨架 + 高德导航 SDK 集成）

> 状态：**审核通过（T3j）**，待用户本地构建 + 真机验收。
> 流程：项目经理拆解 → 程序员A/B 实现 → 审核员 10 轮审核（T3a–T3j）→ 项目经理终审 → 提交用户。

## 一、交付内容

Android 工程位于 `/home/qmxz/Pilot-bot/android/`，包名 `com.qmxz.pilotbot`，View/XML 界面，共 26 个文件：

| 层 | 文件 | 说明 |
| --- | --- | --- |
| 构建 | `settings.gradle.kts` / `build.gradle.kts` / `app/build.gradle.kts` / `gradle.properties` / `gradle/libs.versions.toml` | AGP 8.7.3、Kotlin 2.0.21、compileSdk 34 / minSdk 26 / JVM 17；含高德 maven 仓库与 navi-3dmap 10.0.600、location 6.4.9 |
| 清单 | `AndroidManifest.xml` | 权限齐全；`com.amap.api.v2.apikey` 用 `${AMAP_KEY}` 占位符 |
| 应用 | `PilotBotApp.kt` / `MainActivity.kt` | 应用类 + 测试界面（开始/停止按钮、状态显示、动态定位权限） |
| 导航层 | `navi/NavigationProvider.kt`、`NaviSessionCoordinator.kt`、`AmapNavigationProvider.kt`、`NaviState.kt`、`TurnInstruction.kt`、`TurnAction.kt`、`RoutePlan.kt`、`NaviError.kt`、`NaviEventListener.kt` | 按 DESIGN.md §5.1 契约实现；coordinator 架构（见下） |
| 资源 | `res/` | 布局、字符串、主题、自适应图标 |
| 安全 | `local.properties.template`、`.gitignore` | key 注入路径；local.properties 不入库 |

## 二、核心架构（审核 10 轮沉淀的结论）

**`NaviSessionCoordinator`（进程级单例）**：
- **唯一持有** `AMapNavi` + 唯一 `SimpleNaviListener`，进程生命周期内 add 一次，会话切换不 remove/add——消除"旧 listener 回调转交新 listener"；
- **generation 状态机**（IDLE / ROUTE_CALCULATING / START_PENDING / NAVIGATING / WAIT_FOR_OLD_TERMINAL / WAIT_FOR_START_ACK）：回调按 generation + phase 过滤，主线程送达时重检；
- **不重叠会话硬约束**：算路中 stop 等旧终态、START_PENDING 停止等旧 ACK，期间新 start 返回 BUSY；无超时静默放行；
- `AmapNavigationProvider` 为纯委托门面；MainActivity 复用单实例。

## 三、你本地构建 + 真机验收步骤

1. **安装**：JDK 17 + Android Studio（自带 SDK 34）。
2. **配置 key**：复制 `android/local.properties.template` 为 `android/local.properties`，填 `sdk.dir=...` 和 `amap.api.key=你的高德Key`（[高德开放平台](https://lbs.amap.com/) 注册，包名 `com.qmxz.pilotbot` + 你机器上 `./gradlew signingReport` 的 SHA1）。
3. **构建**：Android Studio 打开 `android/` 目录，Sync 后 Run 到真机（导航 SDK 需真机，模拟器不可用）。
4. **验收操作**：授予定位权限 → 点「开始测试导航」（固定测试路线：故宫附近）→ 观察状态栏是否显示算路结果、实时剩余距离/时间/道路、以及**导航播报原文**（onGetNavigationText，本项目核心数据）。

## 四、M0 真机验收清单（放行前置条件）

必须覆盖并记录：

1. 算路 → 立即 Stop → Start：验证旧算路终态是否仍送达、是否误启动/误显示旧路线；
2. START_PENDING 时 Stop → 立即 Start 且旧 onStartNavi ACK 延迟：验证不误收；
3. NAVIGATING 时 Stop → 立即 Start 且旧状态/播报/到达延迟：**验证旧回调是否混入新会话**（NAVIGATING 残余风险项，见五）；
4. 两类 WAIT 的终态/ACK 是否必达；若永久 BUSY，验证显式 reset/restart 预案；
5. GPS 是否在 Stop 后实际停止；SDK 回调线程。

## 五、文档化残余风险（PM 决策，审核员接受）

- **成因**：官方 `AMapNaviListener` 无"导航停止"终态回调（Javadoc 方法清单已核实），且回调不带会话 ID；
- **残余**：NAVIGATING 阶段 Stop 后立即 Start，理论上旧状态/播报/到达回调可能混入新会话——代码无法静态排除（无终态可等）；
- **处置**：真机验证（上表第 3 项）；若实测发现混入，引入"实测可配置排水延迟"（常数可调），不臆造静态保证；
- **第二类假设**：被接受的 `calculateDriveRoute`/`startNavi` 必达终态/ACK；若真机发现 stop 后不再送达导致永久 BUSY，属 SDK 契约发现，按显式 reset/restart 处置。

## 六、审核历程摘要（T3a–T3j，10 轮）

| 轮次 | 结论 | 关键修复 |
| --- | --- | --- |
| T3a | 不通过 | IconType 映射错、并发算路竞态、主线程违规、进程级 destroy 误用 |
| T3b | 不通过 | stop 后迟到成功回调误重启导航 |
| T3c | 不通过 | 会话代际缺失（旧回调复活会话）、错误投递遗漏 |
| T3d | 不通过 | suppressed/deferred 状态机死路（换架构） |
| T3e | 不通过 | 单例跨实例 UNEXPECTED 误杀（改忽略+日志） |
| T3f | 不通过 | 锁外写状态、startRequested 未消费 |
| T3g | 不通过 | 旧算路终态转交新 listener（上 coordinator） |
| T3h | 不通过 | 编译阻断、超时≠静默握手、死代码 |
| T3i | 不通过 | START_PENDING/NAVIGATING 停止未隔离 |
| T3j | **通过** | WAIT_FOR_START_ACK；NAVIGATING 残余按文档化处理 |

## 七、下一步（M1 候选）

- M0 真机验收通过后，进入 **M1：核心闭环**（机械播报 → LLM 改写 → 流式 TTS）。
- 建议顺手：`git init` 初始化仓库（开源项目需要），`local.properties` 已在 .gitignore。
