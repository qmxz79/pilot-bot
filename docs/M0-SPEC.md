# M0 任务规格（项目组共享契约）

> 本文件是项目经理（我）写给程序员和审核员的共享规格：接口契约、任务拆分、审核清单。
> 程序员按此实现，审核员按此审核。与 DESIGN.md 冲突时以此为准（本文件是 M0 落地版）。

## 已拍板决策（2026-08-15，项目经理代用户拍板）

| 项 | 决策 |
| --- | --- |
| Android 代码根目录 | `/home/qmxz/Pilot-bot/android/` |
| applicationId / namespace | `com.qmxz.pilotbot` |
| UI 方案 | 经典 View + XML（不用 Compose） |
| SDK 级别 | compileSdk 34 / minSdk 26 / targetSdk 34 / JVM 17 |
| 构建工具链 | AGP 8.x + Kotlin（选稳定兼容版本） |
| 高德 | navi-3dmap + location，maven 仓库 `https://maven.amap.com/repository/maven-public/` |
| key 注入 | `local.properties` → manifest 占位符 `${AMAP_KEY}`；禁止硬编码 |
| 构建验证 | 本机无工具链 → 静态审核；用户在本地 Android Studio 编译运行 |

## 任务拆分

| 任务 | 负责人 | 内容 | 产出 |
| --- | --- | --- | --- |
| T1 | 程序员A | 工程骨架：gradle/manifest/res/Application/MainActivity 外壳 | `android/` 目录全套 |
| T2 | 程序员B | 高德导航集成：`NavigationProvider` 实现 + `NaviEventListener` 回调采集 | `navi/` 包 |
| T3 | 审核员 | 静态审核 T1+T2（按下方清单），不通过退回对应程序员修改 | 审核结论 |
| T4 | 项目经理 | 终审 → 提交用户 | 交付说明 |

## 接口契约（T2 必须实现，T1 不得引用）

包路径：`com.qmxz.pilotbot.navi`

```kotlin
interface NavigationProvider {
    suspend fun startNavi(route: RoutePlan)
    suspend fun stopNavi()
    val isNavigating: Boolean
    fun addListener(listener: NaviEventListener)
    fun removeListener(listener: NaviEventListener)
}

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

data class TurnInstruction(
    val action: TurnAction,   // TURN_LEFT / KEEP_RIGHT / ENTER_ROUNDABOUT ...
    val distanceMeters: Int,
)

interface NaviEventListener {
    fun onNaviStateChanged(state: NaviState)
    fun onNaviText(text: String)          // 高德 onGetNavigationText 播报原文
    fun onRouteCalculated(route: RoutePlan)
    fun onArrived()
    fun onNaviError(error: NaviError)
}
```

（RoutePlan / TurnAction / NaviError 由程序员B按需定义，保持与 DESIGN.md 精神一致。）

## 高德 SDK 关键事实（T2 用，程序员B可 web_search 核实最新 API）

- 监听：`AMapNaviDriveManager.getInstance().addAMapNaviListener(listener)`
- 播报原文：`AMapNaviListener#onGetNavigationText(int type, String text)` ← 本项目核心数据
- 实时状态：`AMapNaviListener#onNaviInfoUpdate(NaviInfo info)` → 剩余距离 `info.getPathRetainDistance()`、剩余时间 `getPathRetainTime()`、当前道路 `getCurrentRoadName()`、下一转向 `getIcon()`/`getNextRoadName()` 等
- 初始化：`AMapNaviDriveManager.getInstance().init(context)`；`calculateDriveRoute(...)` 发起算路
- key：manifest meta-data `com.amap.api.v2.apikey`

## 审核清单（T3 审核员用）

1. **工程规范**：manifest 权限齐全、MainActivity 已声明为主入口、namespace/applicationId 一致
2. **构建**：gradle 文件语法正确、版本兼容（AGP↔Kotlin↔高德SDK）、高德 maven 仓库已配置
3. **key 安全**：无硬编码 key；仅 local.properties → manifest 占位符；.gitignore 覆盖 local.properties
4. **解耦**：MainActivity（T1）未引用 navi 包；接口与 DESIGN.md/M0-SPEC 一致（T2）
5. **代码质量**：空指针防护、生命周期处理（init/destroy）、回调线程正确、注释清晰
6. **编译意图**：import 完整、语法正确、XML 良构（本机无法编译，只能静态检查）

## 交付流程

程序员完成 → 审核员审核 → 不通过退回原程序员 → 通过 → 项目经理终审 → 提交用户。
