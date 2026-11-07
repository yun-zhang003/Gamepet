# 鲸伴 SkyPet · v0.2 工程契约（CONTRACT）— 单一真相源

> 状态：**FROZEN / v1（r1）**
> 角色归属：backend-architect（后端/系统技术契约顾问）
> 独占写入范围：`SkyPet/docs/CONTRACT.md`（本文件）
> 上位依据：用户在会话中粘贴的《鲸伴 · 安卓桌宠 MVP》README（**不是** 工作区里的 `D:/work_dsh/README.md`——那是无关的 QQ 插件项目，已确认忽略）。
> 消费方：`executor`（据此实现代码）、`ui-designer`（据此产出 UI-SPEC 数值令牌）、`reviewer`（据此逐条审查）。
> 修订规则：本文件冻结后，签名/键名/包名/权限的任何变更都视为 v0.2 契约破坏，须走一次显式契约修订；`executor` 不得私自偏离，若发现不可实现须回退给 captain 重新冻结。

---

## 0. 结论摘要（TL;DR）

- 目标：从零生成一个**可静态审查**的 Android 桌宠工程 `SkyPet`，包名 `com.skypet.pet`，核心是一个常驻 `PetOverlayService`（悬浮桌宠）+ 一个普通 `MainActivity`（三页内嵌导航 UI）。
- 技术选型：**纯 View 体系 + 手写 UI，不用 Compose**（理由见 §2.2）。语言 Kotlin，`compileSdk 35 / minSdk 26 / targetSdk 35`，Gradle `8.9`，AGP `8.7.x`，Kotlin `2.0.x`，JDK17 toolchain（本机 JDK21 运行 Gradle，用 toolchain 压到 17 编译）。
- 权限 5 项：`SYSTEM_ALERT_WINDOW`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、`POST_NOTIFICATIONS`、`PACKAGE_USAGE_STATS`（仅声明 + 引导去系统设置，不申请运行时授权）。
- 组件：`MainActivity`（launcher）+ `PetOverlayService`（`foregroundServiceType="specialUse"`）+ 必要 receiver（`BootReceiver` 可选、`PowerConnectionReceiver` 不需要；见 §3.3）。
- 数据：`SharedPreferences`（单文件 `skypet_prefs`）存每日勾选/位置/尺寸/开关；导入图片复制到 `filesDir/pets/`；不联网、不读游戏画面、代码不含任何密钥。
- 本机限制：**有 JDK21，无 Android SDK / Gradle / adb / kotlinc / 设备**。因此「能编译」「能真机跑」「能截图」三项在 CONTRACT 层一律**标记未验证**，`executor`/`reviewer` 必须如实沿用（见 §9）。

---

## 1. 精确目录树与每文件职责

工程根：`D:\work_dsh\SkyPet`。严格对齐 README 第 3 节目结构（下文 `app/src/main/` 内的路径即契约路径，包前缀 `com.skypet.pet`）。

```
SkyPet/
├── settings.gradle.kts                     # 工程名/仓库/模块声明
├── build.gradle.kts                        # root 构建脚本（插件版本 only）
├── gradle.properties                       # JVM/AndroidX/非传递R类等开关
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.properties       # 固定 Gradle 8.9
│       ├── gradle-wrapper.properties.template  # README 要求的模板（内容同 wrapper，供 Studio 重命名/sync 用）
│       └── gradle-wrapper.jar              # 二进制；本机无 Gradle 时可为占位并标注未验证（见 §2.5）
├── app/
│   ├── build.gradle.kts                    # app 模块：插件、android{}、dependencies
│   ├── proguard-rules.pro                  # 空/最小，release 用
│   └── src/main/
│       ├── AndroidManifest.xml             # 权限+组件+queries（见 §3）
│       ├── java/com/skypet/pet/
│       │   ├── MainActivity.kt             # 唯一 Activity（launcher），承载三页导航
│       │   ├── core/
│       │   │   ├── Prefs.kt                # 全部 SharedPreferences 读写（契约 §4.3）
│       │   │   ├── PetImageStore.kt        # 导入图片复制/读取优先级（§4.4）
│       │   │   ├── ForegroundApp.kt        # UsageStats 判定前台包名（§4.5）
│       │   │   └── plugin/
│       │   │       ├── GamePlugin.kt       # 插件契约数据类型+接口（§4.1）
│       │   │       └── PluginRegistry.kt   # 内置插件注册表（§4.2）
│       │   ├── plugins/
│       │   │   ├── SkyPlugin.kt            # 光·遇（Sky）内置插件实现
│       │   │   └── GenericPlugin.kt        # 通用兜底插件实现
│       │   ├── overlay/
│       │   │   └── PetOverlayService.kt    # 悬浮桌宠前台服务：三窗+手势+气泡+计时+通知（§4.6）
│       │   └── ui/
│       │       ├── Theme.kt                # UiKit 配色令牌常量（§4.7）
│       │       ├── UiKit.kt                # 通用 UI 构建工具函数（dp、圆角、文本框、卡片等）
│       │       ├── Icons.kt                # 纯 Canvas 绘制的矢量图标（底部导航三图标等）
│       │       ├── HomeScreen.kt           # 「陪伴」页
│       │       ├── PacksScreen.kt          # 「陪伴包」页（插件选择+每日勾选卡片）
│       │       ├── SettingsScreen.kt       # 「设置」页（开关/尺寸/导入图/权限引导）
│       │       ├── PetArt.kt               # 宠物绘制入口：图片优先/矢量兜底（§4.7）
│       │       ├── WhaleGirlArt.kt         # 矢量鲸鱼娘绘制（look 配色驱动）
│       │       ├── PetPanel.kt             # 点开桌宠后的功能面板（“歇一会儿”等）
│       │       └── SpeechBubble.kt         # 奶白底深蓝字气泡视图
│       └── res/
│           ├── drawable-nodpi/pet_whale.png        # 可选内置整图（缺失则走矢量兜底）
│           ├── drawable/ic_launcher_foreground.xml # 或 mipmap，最小占位
│           ├── mipmap-anydpi-v26/ic_launcher.xml   # 自适应图标（可选）
│           ├── values/strings.xml                   # app_name 等
│           ├── values/colors.xml                    # 与 Theme.kt 对齐的兜底色（可选）
│           ├── values/themes.xml                    # 透明/无 ActionBar 主题
│           └── xml/（如需要）                        # 备份规则/数据提取规则（可选）
├── preview/
│   └── whale-ui.html                       # ui-designer 产出，不参与编译（列此仅为对齐 README）
└── docs/
    ├── CONTRACT.md                         # 本文件（backend-architect 独占）
    ├── UI-SPEC.md                          # ui-designer 产出（消费 §6 的令牌需求）
    └── BUILD-VERIFY.md                     # executor 产出（记录静态验证与未验证项）
```

### 1.1 包与文件职责边界（强制）

| 文件 | 包 | 单一职责 | 明确不做 |
|---|---|---|---|
| `MainActivity.kt` | `com.skypet.pet` | 启动三页 View 导航；申请/引导权限；首次“开始陪伴”启动 Service | 不做悬浮逻辑、不持有 WindowManager |
| `core/Prefs.kt` | `com.skypet.pet.core` | 所有持久化读写集中于此（键名见 §4.3） | 不直接引用 UI |
| `core/PetImageStore.kt` | `com.skypet.pet.core` | 导入图复制/清除/读取优先级 | 不做裁剪抠图 |
| `core/ForegroundApp.kt` | `com.skypet.pet.core` | UsageStats 前台包名查询+降级 | 不申请权限本身 |
| `core/plugin/GamePlugin.kt` | `com.skypet.pet.core.plugin` | 数据类型 + `GamePlugin` 接口 | 不含具体插件数据 |
| `core/plugin/PluginRegistry.kt` | `com.skypet.pet.core.plugin` | `plugins` 列表 | 不实现插件内容 |
| `plugins/SkyPlugin.kt` | `com.skypet.pet.plugins` | 光·遇插件数据（look/lines/sections） | 不限死颜色到某一像素 |
| `plugins/GenericPlugin.kt` | `com.skypet.pet.plugins` | 通用插件，`gamePackages=[]` | — |
| `overlay/PetOverlayService.kt` | `com.skypet.pet.overlay` | 前台服务+三悬浮窗+手势+气泡+计时+通知 | 不读游戏画面、不联网 |
| `ui/Theme.kt` | `com.skypet.pet.ui` | 颜色/尺寸令牌常量 | 不做布局 |
| `ui/UiKit.kt` | `com.skypet.pet.ui` | 构建控件的小工具 | 不存状态 |
| `ui/Icons.kt` | `com.skypet.pet.ui` | 矢量图标 `Drawable`/绘制函数 | 不依赖网络图标 |
| `ui/HomeScreen.kt` | `com.skypet.pet.ui` | 陪伴页 View 树 | 不直接写 Prefs（经回调/接口） |
| `ui/PacksScreen.kt` | `com.skypet.pet.ui` | 陪伴包页（插件切换+每日勾选） | — |
| `ui/SettingsScreen.kt` | `com.skypet.pet.ui` | 设置页（开关/尺寸/导入/权限） | — |
| `ui/PetArt.kt` | `com.skypet.pet.ui` | 图片优先/矢量兜底的统一绘制入口 | 不做手势 |
| `ui/WhaleGirlArt.kt` | `com.skypet.pet.ui` | 矢量鲸鱼娘绘制（look 驱动） | — |
| `ui/PetPanel.kt` | `com.skypet.pet.ui` | 桌宠功能面板（“歇一会儿”） | 不做悬浮窗管理 |
| `ui/SpeechBubble.kt` | `com.skypet.pet.ui` | 气泡视图（奶白底/深蓝字） | — |

> 注：README 只列了 14 个 `.kt`（含 `plugins/` 2 个），本契约额外把 `plugins/SkyPlugin.kt`、`plugins/GenericPlugin.kt` 显式化（README §3 已含 `plugins/` 目录）。实际 `.kt` 总数 = 19（含 MainActivity；`plugins/` 2 个已计入）。下游校验要求 `>=12`，本契约按 19 冻结。`[r2 校准]`

---

## 2. 构建配置契约

### 2.1 版本矩阵（冻结）

| 项 | 值 | 说明 |
|---|---|---|
| Gradle（wrapper 分发） | **8.9** | `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl=...gradle-8.9-bin.zip` |
| Android Gradle Plugin (AGP) | **8.7.3** | 与 Gradle 8.9 兼容（AGP 8.7 要求 Gradle ≥ 8.9）|
| Kotlin | **2.0.21** | 与 AGP 8.7.x 兼容 |
| compileSdk | **35** | |
| minSdk | **26** | Android 8.0 |
| targetSdk | **35** | |
| Java 源/目标编译级别 | **17** | `compileOptions` source/target = `JavaVersion.VERSION_17` |
| JDK 运行 Gradle | 本机 **JDK21** | Gradle 8.9 支持 JDK21 运行；编译目标经 toolchain/`jvmTarget=17` 压到 17 |
| jvmTarget | **17** | `kotlinOptions { jvmTarget = "17" }` |
| 依赖体系 | **View 体系（AppCompat + Material 可选）**，**不使用 Compose** | 见 §2.2 |

**JDK17 toolchain 处理（重点，本机是 JDK21）**：在 `app/build.gradle.kts` 写
```kotlin
kotlin { jvmToolchain(17) }
```
或使用 `java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }`。
说明：本机只有 JDK21 且**没有 Android SDK/Gradle**，toolchain 的 17 实际能否解析取决于本机是否装有 JDK17；若仅有 JDK21，Gradle 会尝试下载 17 toolchain（需网络与 foojay 解析器）或报错。**因此在 CONTRACT 层声明：以 `jvmTarget="17"` + `JavaVersion.VERSION_17` 作为编译级别，`jvmToolchain(17)` 为推荐但本机未验证**。两者都不得改为 21，以保持 README 的“JDK17 兼容”。

### 2.2 依赖选型决策：View 体系（不用 Compose）

**决策：纯 View 体系。** 理由：
1. README 要求的都是常规 View 能力（三页底部导航、悬浮窗 `LayoutParams`、手写 Canvas 绘制鲸鱼娘/图标、气泡贴边），用 `View`/`Canvas` 最直接，Reviewer 可静态核验 `View` 树与 `Canvas` 调用。
2. 悬浮窗（`WindowManager.addView`）本质就是 View，用 Compose 反而要 `ComposeView` 桥接，增加不可静态编译验证的风险面。
3. 本机无法编译，Compose 的编译器插件（`kotlinCompilerExtensionVersion`、compose BOM）一旦错版本就是大量不可见错误；View 体系依赖更少、更稳。
4. 用 `Canvas`/`Paint` 手绘可满足“无外部图片也能显示鲸鱼娘”的矢量兜底要求。

因此：**禁止引入 Compose。** `executor` 若引入 `androidx.compose.*` 视为违约。

### 2.3 依赖坐标（精确，最小集）

`app/build.gradle.kts` 的 `dependencies {}` 仅允许：
```kotlin
implementation("androidx.core:core-ktx:1.13.1")
implementation("androidx.appcompat:appcompat:1.7.0")
implementation("com.google.android.material:material:1.12.0")   // 可选：若用 MaterialButton/Switch 等；不用可删
// 测试（可选，非必需）：junit / androidx.test
```
- 不使用 `androidx.compose.*`、不使用任何网络库（`okhttp/retrofit`）、不使用图片加载库（`glide/coil`）。
- `minSdk 26` 下 `core-ktx 1.13.1`（要求 compileSdk 34+）与 `appcompat 1.7.0` 均可用。

root `build.gradle.kts` 仅声明插件版本（不 apply）：
```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```
`settings.gradle.kts`（示意，契约要求的要素）：
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "SkyPet"
include(":app")
```
`gradle.properties` 必须含：
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

### 2.4 app/build.gradle.kts 的 android{} 关键块

```kotlin
android {
    namespace = "com.skypet.pet"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.skypet.pet"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }   // 若使用 BuildConfig
}
```

### 2.5 gradle-wrapper

`gradle/wrapper/gradle-wrapper.properties`：
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```
- README 提到 `gradle-wrapper.properties.template`：本契约要求**同时存在** `.template`（内容同上，供 Studio “重命名或自生成”），`.properties` 为主用。
- `gradle-wrapper.jar` 是二进制，本机无 Gradle 时无法合法生成；`executor` 可写占位说明并**在 BUILD-VERIFY.md 标注“wrapper.jar 未验证/需 Studio 或 `gradle wrapper` 生成”**，`reviewer` 不得因 jar 缺失判 compile 通过。**本契约明确：编译项未验证。**

---

## 3. AndroidManifest 权限与组件清单

### 3.1 权限（5 项，全部为 `<uses-permission>`）

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions" />
```
- `PACKAGE_USAGE_STATS`：**仅声明 + 代码跳 `Settings.ACTION_USAGE_ACCESS_SETTINGS`**，不申请运行时授权（本就是签名/特殊权限，无法弹窗授权）。
- `POST_NOTIFICATIONS`：Android 13+ 运行时权限，`MainActivity` 首次引导申请；缺失时服务仍可跑，仅通知不可见（不阻断）。
- 需要 `xmlns:tools="http://schemas.android.com/tools"` 以用 `tools:ignore`。

### 3.2 组件

```xml
<application
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.SkyPet">

    <activity
        android:name=".MainActivity"
        android:exported="true"
        android:theme="@style/Theme.SkyPet">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>

    <service
        android:name=".overlay.PetOverlayService"
        android:exported="false"
        android:foregroundServiceType="specialUse">
        <property
            android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
            android:value="floating_pet_desktop_companion" />
    </service>
</application>
```
- `PetOverlayService` **必须**：`android:exported="false"` + `android:foregroundServiceType="specialUse"` + 内嵌 `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>`（Android 14/targetSdk34+ 对 `specialUse` 强制要求该 property，否则安装/启动被拒）。
- `<property>` 位于 `<service>` 内，`android:value` 用自定义字符串说明子类型用途。

### 3.3 receiver 与 queries（必要/可选）

- **必要**：本 MVP **不需要** `BOOT_COMPLETED` receiver（README 明确“无开机自启”）。因此 Manifest **不得**声明 `RECEIVE_BOOT_COMPLETED`，也**不要求** `BootReceiver`。
- **可选 receiver**：`PetActionReceiver`（`BroadcastReceiver`，`exported="false"`）用于承接通知栏动作按钮（暂停/退出/歇一会儿）；若 `executor` 用 `PendingIntent` 直接指向 Service 的 `ACTION_*`，则可不建 receiver。**契约允许两种实现，但必须保证通知动作可触发**（§4.6）。
- **queries**：`ForegroundApp` 用 UsageStats **不需要** `<queries>`。若实现选择 `getRunningTasks`/`PackageManager` 探测，同样无需 queries。故 `<queries>` 非必需。

### 3.4 权限缺失时的行为（与 README 第 5 节一致）

| 权限 | 缺失后果 | 代码处理 |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | 无法显示悬浮桌宠 → 不能启动陪伴 | `Settings.canDrawOverlays()` 为 false 时，`MainActivity` 引导到 `ACTION_MANAGE_OVERLAY_PERMISSION`，禁用“开始陪伴” |
| `FOREGROUND_SERVICE(_SPECIAL_USE)` | 服务无法常存活 | 声明即得（普通权限）；`startForeground` 必带 type |
| `POST_NOTIFICATIONS` | 通知不可见，服务仍跑 | 13+ 请求；失败不阻断 |
| `PACKAGE_USAGE_STATS` | “只在游戏里出现”退化为**始终显示** | `ForegroundApp` 返回“未知→按始终显示处理”（§4.5） |

---

## 4. 核心接口契约（精确到 Kotlin 签名）

> 以下签名即**冻结契约**。字段名、类型、可空性、默认值都不得改。`executor` 必须逐字一致（允许额外添加 `@JvmStatic`/注释/私有辅助，不得改公开签名）。

### 4.1 `core/plugin/GamePlugin.kt`

```kotlin
package com.skypet.pet.core.plugin

import androidx.annotation.DrawableRes   // 可选；imageName 用字符串也可

/** 配件枚举/密封类。至少含 Star（星星）；可扩展。 */
sealed class Accessory {
    data object None : Accessory()
    data object Star : Accessory()
    data object Bubble : Accessory()
    data object Note : Accessory()
    // 允许新增，但 Star 必须存在
}

/**
 * 宠物外观。颜色均为 ARGB Int（android.graphics.Color）。
 * imageName: 指向 res/drawable 的图片名（不含扩展名）；null 或找不到时回退矢量绘制。
 */
data class PetLook(
    val body: Int,
    val belly: Int,
    val accent: Int,
    val glow: Int,
    val accessory: Accessory = Accessory.None,
    val imageName: String? = null
)

/** 每日清单里的一张提示卡片。 */
data class TipCard(
    val key: String,
    val title: String,
    val desc: String,
    val tag: String
)

/** 一个可勾选分区（如“今日光·遇小目标”）。 */
data class PluginSection(
    val id: String,
    val title: String,
    val checklist: List<String>,
    val cards: List<TipCard> = emptyList()
)

/**
 * 桌宠插件契约。
 * 约定：
 *  - id 发布后不可改（本地勾选按 id 维度存储，改了会丢数据）。
 *  - gamePackages 为空 List = 通用插件（任何前台应用都出现）。
 *  - look 的颜色决定矢量绘制配色；imageName 决定图片外观。
 */
interface GamePlugin {
    val id: String
    val name: String
    val tagline: String
    val gamePackages: List<String>
    val look: PetLook
    fun greeting(): String
    val idleLines: List<String>
    val sections: List<PluginSection>
}
```

**契约强制点**
- `GamePlugin` 的 8 个成员名/类型**逐字**：`id:String`、`name:String`、`tagline:String`、`gamePackages:List<String>`、`look:PetLook`、`greeting():String`（**方法**，非属性）、`idleLines:List<String>`、`sections:List<PluginSection>`。
- `PetLook` 构造顺序：`body, belly, accent, glow, accessory=Accessory.None, imageName:String?=null`。
- `PluginSection` 构造顺序：`id, title, checklist, cards`。
- `TipCard` 构造顺序：`key, title, desc, tag`。

### 4.2 `core/plugin/PluginRegistry.kt`

```kotlin
package com.skypet.pet.core.plugin

import com.skypet.pet.plugins.SkyPlugin
import com.skypet.pet.plugins.GenericPlugin

object PluginRegistry {
    /** 内置插件表；顺序即 UI 展示顺序。首个为默认。 */
    val plugins: List<GamePlugin> = listOf(
        SkyPlugin(),
        GenericPlugin()
    )

    fun byId(id: String?): GamePlugin =
        plugins.firstOrNull { it.id == id } ?: plugins.first()

    fun default(): GamePlugin = plugins.first()
}
```
- **`plugins` 必为 `List<GamePlugin>`**，含 `SkyPlugin`（id 建议 `"sky"`）与 `GenericPlugin`（id 建议 `"generic"`，`gamePackages=emptyList()`）。
- `byId` 必须对未知/空 id 回退到 `plugins.first()`（默认插件），保证 UI 永不 NPE。

### 4.3 `core/Prefs.kt`

**存储方式**：单文件 `SharedPreferences("skypet_prefs", Context.MODE_PRIVATE)`。所有读写集中此文件；其他类只经 `Prefs` 访问。

**键名（冻结）**

| 常量名 | key 字符串 | 类型 | 含义 / 默认 |
|---|---|---|---|
| `KEY_PLUGIN_ID` | `"plugin_id"` | String | 当前插件 id，默认 `PluginRegistry.default().id` |
| `KEY_CHECK_PREFIX` | `"check_"` | — | 每日勾选前缀，完整 key = `check_<pluginId>_<sectionId>_<index>` |
| `KEY_CHECK_DATE` | `"check_date"` | String | 当日日期 `yyyy-MM-dd`（本地时区），用于跨天失效 |
| `KEY_POS_X` | `"pos_x"` | Int | 桌宠右上角/左上角 x（px），默认由屏幕宽度算（见 §8） |
| `KEY_POS_Y` | `"pos_y"` | Int | 桌宠 y（px） |
| `KEY_SIZE_DP` | `"size_dp"` | Int | 桌宠边长 dp，默认 108，范围 72–144 |
| `KEY_QUIET` | `"quiet_companion"` | Boolean | 安静陪伴（不主动冒气泡），默认 false |
| `KEY_ONLY_IN_GAME` | `"only_in_game"` | Boolean | 只在游戏里出现，默认 true |
| `KEY_REST_REMINDER` | `"rest_reminder"` | Boolean | 45min 休息提醒，默认 true |
| `KEY_VOICE_LINES` | `"voice_lines"` | Boolean | （可选）是否冒泡说话，默认 true |
| `KEY_IMPORTED_IMAGE` | `"imported_image"` | String? | 导入图私有目录文件名（PetImageStore 管），无则空 |
| `KEY_STARTED` | `"started"` | Boolean | 是否“开始陪伴”过，默认 false |

**每日勾选逻辑（跨天失效，冻结语义）**
- 勾选 key = `check_<pluginId>_<sectionId>_<index>`，值为 Boolean。
- 另存 `check_date` = 今日 `yyyy-MM-dd`。
- `isChecked(pluginId, sectionId, index): Boolean`：
  1. 读取 `check_date`；若 ≠ 今日 → **视为全部未勾**（返回 false），并（惰性）把 `check_date` 刷成今日。
  2. 若日期相同，返回对应 key 的布尔值。
- `setChecked(pluginId, sectionId, index, checked)`：**写前先确保 `check_date`==今日**；否则先清空所有 `check_*`（跨天重置）再写 `check_date`=今日，然后写该 key。
- `resetTodayIfNewDay()`：供 Service/Activity 启动时调用，统一做跨天重置。
- **语义要求**：无需真的删除旧 key（可比对日期视为失效），但为省空间，建议跨天时 `edit().clear()` 掉 `check_` 前缀项后重写日期。两种实现都可，`reviewer` 以“跨天后所有勾选读为 false”为判定标准。

**其他存取方法（冻结签名）**
```kotlin
class Prefs(context: Context) {
    var pluginId: String
    var posX: Int
    var posY: Int
    var sizeDp: Int              // clamp 到 72..144
    var quietCompanion: Boolean
    var onlyInGame: Boolean
    var restReminder: Boolean
    var voiceLines: Boolean
    var importedImageName: String?
    var started: Boolean

    fun isChecked(pluginId: String, sectionId: String, index: Int): Boolean
    fun setChecked(pluginId: String, sectionId: String, index: Int, checked: Boolean)
    fun resetTodayIfNewDay()
    fun clearAllChecks()
}
```
- `sizeDp` setter 必须 `coerceIn(72, 144)`。
- 位置只存 `x/y`（px 或 dp 二选一但要全工程统一；**本契约建议存 px 屏幕坐标**，并在旋转后按当前屏幕夹取，**不感知横竖屏**——见 §7）。

### 4.4 `core/PetImageStore.kt`

职责：把用户选择的图片**复制到应用私有目录**（不长期依赖 content Uri，避免权限失效），提供读取优先级。

```kotlin
class PetImageStore(private val context: Context) {
    /** 私有目录：context.filesDir/pets/ */
    fun petsDir(): File

    /**
     * 从 content Uri 导入：把字节复制到 filesDir/pets/imported_<ts>.<ext>，
     * 返回新文件名（存入 Prefs.importedImageName）；失败返回 null。
     * 不抠图、不裁剪；显示时按比例缩放（fitCenter）。
     */
    fun importFromUri(uri: Uri): String?

    /** 清除导入图（删文件 + Prefs 置空）。 */
    fun clearImported()

    /** 当前导入图 File（存在才返回），否则 null。 */
    fun currentImportedFile(): File?

    /**
     * 读取优先级（供 PetArt 使用）：
     * 1) 导入图（Prefs.importedImageName 且文件存在）
     * 2) res/drawable-nodpi/pet_whale.png（存在则用）
     * 3) null → 调用方回退矢量 WhaleGirlArt
     * 返回 Bitmap?（1/2 命中）或 null（走矢量）。
     */
    fun loadPetBitmap(): Bitmap?
}
```
**读取优先级（冻结）**：**用户导入图 > `drawable-nodpi/pet_whale.png` > 矢量鲸鱼娘**。
- 命中导入图：读私有文件 `BitmapFactory.decodeFile`。
- 无导入图：尝试 `R.drawable.pet_whale`（用 `resources.getDrawable` 或 decode，注意 `pet_whale.png` 若不存在则该 drawable 资源 id 不存在——`executor` 必须用 `try/catch` 或 `getIdentifier` 判空，**不得硬编码导致编译期 Constant 引用不存在的资源**）。
- 都无：返回 null，`PetArt` 走 `WhaleGirlArt.draw(canvas, look)` 矢量兜底。

### 4.5 `core/ForegroundApp.kt`

职责：用 `UsageStatsManager` 判定当前前台应用包名，供“只在游戏里出现”。

```kotlin
class ForegroundApp(private val context: Context) {
    /** 是否已获 UsageStats 授权（AppOpsManager.OPSTR_GET_USAGE_STATS == MODE_ALLOWED）。 */
    fun hasUsageAccess(): Boolean

    /** 当前前台包名；无法判定返回 null。 */
    fun currentPackage(): String?

    /**
     * 判定“是否应显示桌宠”（核心降级规则，冻结）：
     * - 传入插件 gamePackages（可能为空=通用）。
     * - 若 !hasUsageAccess() → 返回 true（降级“始终显示”）。
     * - 若 currentPackage()==null → 返回 true（无法判定→不隐藏）。
     * - 若 gamePackages 为空 → 返回 true（通用插件）。
     * - 否则返回 currentPackage() in gamePackages。
     */
    fun shouldShowFor(gamePackages: List<String>): Boolean

    /** 跳转 Usage Access 设置页。 */
    fun openUsageAccessSettings()
}
```
**降级规则（冻结，README §5/§8）**：
- 无 `PACKAGE_USAGE_STATS` 权限/未授权 → `shouldShowFor` **恒 true**（“只在游戏里出现”退化为始终显示）。
- 查询窗口：`UsageStatsManager.queryUsageStats(INTERVAL_DAILY / INTERVAL_BEST, now-10min, now)`，取 `lastTimeUsed` 最大者；辅以 `queryEvents` 的 `ACTIVITY_RESUMED`（API 29+）更稳。部分 ROM 需额外“后台弹出/自启动”许可，README 已列已知限制。
- `shouldShowFor` 是 Service 定时器每 tick 的判定入口；返回 false 时隐藏桌宠窗（`View.GONE` 或 `removeView`），true 时显示。

### 4.6 `overlay/PetOverlayService.kt`

前台服务，负责**三个悬浮窗**、手势、气泡、计时器、前台通知+动作。

**三窗（冻结）**
| 窗 | 内容 | WindowManager.LayoutParams 关键 |
|---|---|---|
| 1. 宠物窗（petView） | `PetArt` 绘制（图片/矢量）+ 可拖拽/点击 | `TYPE_APPLICATION_OVERLAY`，`FLAG_NOT_FOCUSABLE`，`WRAP_CONTENT`，初始位置右侧中间偏上 |
| 2. 气泡窗（bubbleView） | `SpeechBubble`，宠物头顶 | 同 TYPE，`FLAG_NOT_FOCUSABLE`+`FLAG_NOT_TOUCHABLE`，贴 pet 上方，**顶部夹到 y=0** |
| 3. 面板窗（panelView） | `PetPanel`，点宠物后弹出 | 同 TYPE，可点击（点外部或再点宠物收起） |

**服务骨架（冻结签名/生命周期）**
```kotlin
class PetOverlayService : Service() {
    companion object {
        const val ACTION_START = "com.skypet.pet.action.START"
        const val ACTION_STOP  = "com.skypet.pet.action.STOP"
        const val ACTION_REST  = "com.skypet.pet.action.REST"   // 歇一会儿
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "skypet_overlay"
        fun start(ctx: Context)   // ContextCompat.startForegroundService(intent ACTION_START)
        fun stop(ctx: Context)    // stopService
    }
    override fun onCreate()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy()
}
```
- `onStartCommand` 必须先 `startForeground(NOTIF_ID, buildNotification(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`（API 34+ 用带 type 的重载；26–33 用两参重载）。
- **NotificationChannel**：`NotificationChannel(CHANNEL_ID, "桌宠陪伴", IMPORTANCE_LOW)`，`onCreate` 里注册（API26+）。
- **前台通知+动作**：通知常驻，含标题“鲸伴在陪你”+ 至少一个动作 `暂停/歇一会儿`（`ACTION_REST`）与 `退出`（`ACTION_STOP`）。动作用 `PendingIntent.getService(...)`（`FLAG_IMMUTABLE`）触发。
- **手势（冻结语义）**：
  - 拖拽：`OnTouchListener` 记录 `ACTION_DOWN` 的 `rawX/rawY` 与初始 `params.x/y`；`ACTION_MOVE` 更新 `params.x/y` 并 `updateViewLayout`；`ACTION_UP` 写 `Prefs.posX/posY`（**只存 x/y**）。
  - 点击：`ACTION_DOWN→UP` 位移 < touchSlop 且时长 < 200ms 判定为点击 → 切换面板窗显隐 + 冒一句 `greeting()`/随机 `idleLines` 气泡。
  - 拖拽与点击互斥：位移超过 touchSlop 视为拖拽，不触发点击。
- **气泡**：显示随机 `idleLines` 或 `greeting()`，N 秒后自动隐藏；`quietCompanion=true` 时不主动冒泡（仅在点击时冒）。
- **计时器（冻结）**：
  - `Handler`/`postDelayed` 或 `ScheduledExecutor` 每 **15s** tick：调用 `ForegroundApp.shouldShowFor(plugin.gamePackages)` 决定显隐（配合 `onlyInGame` 开关；开关关=始终显示）。
  - 休息提醒：累计陪伴时长每 **45min** 触发一次提醒气泡（“该歇一会儿啦”），`restReminder` 开关控制；可在面板“歇一会儿”后重置计时。
- **尺寸**：`sizeDp` 经 `density` 转 px 作为宠物窗边长；范围 72–144。
- **位置**：`params.gravity = TOP or START`，`x = Prefs.posX`、`y = Prefs.posY`；首次（无存储）默认 **右侧中间偏上**：`x = screenW - size - 24dp`，`y = screenH / 3`。光遇横屏全屏，默认避开左手摇杆（左侧），故靠右。
- **不读画面、不联网**：Service 内不得有网络/截屏/ScreenCapture 调用。
- **气泡贴顶夹取**：气泡窗 y 计算为 `petY - bubbleHeight`，若 `< 0` 则夹到 `0`。

### 4.7 UI 文件职责与关键签名

**`ui/Theme.kt`（UiKit 配色令牌，冻结语义；具体 hex 由 UI-SPEC 给，此处给常量名与用途）**
```kotlin
object UiColors {
    val mistBlue: Int      // 雾蓝（背景/主色基调）
    val skirtBlue: Int     // 裙装蓝（强调/主按钮）
    val creamWhite: Int    // 奶白（气泡底/卡片底）
    val lightPink: Int     // 浅粉（点缀）
    val seaSaltBlue: Int   // 海盐蓝（次级背景/分隔）
    val deepBlueText: Int  // 深蓝字（正文/气泡字）
    val onPrimary: Int     // 主色上的文字色
}
object UiDims {
    val cornerRadiusDp: Float
    val cardPadDp: Float
    val bubblePadDp: Float
    val bubbleCornerDp: Float
    val navIconDp: Float
    // 默认宠物 108dp、范围 72..144 也在此声明常量
    const val PET_SIZE_DEFAULT_DP = 108
    const val PET_SIZE_MIN_DP = 72
    const val PET_SIZE_MAX_DP = 144
}
```
> `Theme.kt` 只放**令牌常量**，不写布局。`UiColors` 具体值由 `ui-designer` 在 UI-SPEC 提供（见 §6 令牌需求）。

**`ui/UiKit.kt`**：工具函数（`Context.dp(Float):Int`、`Context.sp(Float):Float`、`roundedBg(color, radius, stroke)`、`makeTextView(...)`、`makeCardLayout(...)`、`makeSwitchRow(...)` 等）。纯函数式、无状态。

**`ui/Icons.kt`**：`object Icons { fun home(selected:Boolean): Drawable; fun packs(selected:Boolean): Drawable; fun settings(selected:Boolean): Drawable }`，用 `Canvas`/`Path` 手绘，**选中实心 / 未选中描边**。不依赖任何图标字体或网络资源。

**`ui/HomeScreen.kt`**：`class HomeScreen(context, plugin: GamePlugin, prefs: Prefs) : FrameLayout`（或返回 View 的构建函数）。展示：宠物形象预览（`PetArt`）、`greeting()`、`tagline`、一键开始/停止陪伴、今日小目标摘要。

**`ui/PacksScreen.kt`**：`class PacksScreen(context, prefs: Prefs, onPluginChanged:(GamePlugin)->Unit)`。插件选择列表（来自 `PluginRegistry.plugins`，显示 `name/tagline`）；选中插件后展示其 `sections`：每个 section 的 `checklist` 渲染为可勾选行（调用 `Prefs.isChecked/setChecked`，跨天失效）；`cards` 渲染为 `TipCard` 卡片（`title/desc/tag`）。**切换插件只改内容与配色（look），不改形象文件本身**。

**`ui/SettingsScreen.kt`**：`class SettingsScreen(context, prefs: Prefs, imageStore: PetImageStore)`。开关：安静陪伴、只在游戏里出现、45min 休息提醒；尺寸滑杆（72–144，默认 108）；导入/清除宠物图片（调 `PetImageStore`）；权限引导按钮（悬浮窗 `ACTION_MANAGE_OVERLAY_PERMISSION`、通知、Usage Access `ACTION_USAGE_ACCESS_SETTINGS`）。

**`ui/PetArt.kt`**：统一绘制入口。签名建议：
```kotlin
object PetArt {
    /** 图片优先（PetImageStore.loadPetBitmap），否则 WhaleGirlArt 矢量兜底。 */
    fun render(canvas: Canvas, size: Int, look: PetLook, bitmap: Bitmap?)
    fun buildView(context: Context, sizeDp: Int, look: PetLook, bitmap: Bitmap?): View
}
```
优先级：**导入图 > pet_whale.png > 矢量**（由 `PetImageStore.loadPetBitmap()` 返回非 null 即用图片，null 即矢量）。

**`ui/WhaleGirlArt.kt`**：`object WhaleGirlArt { fun draw(canvas: Canvas, size: Int, look: PetLook) }`。用 `look.body/belly/accent/glow` 绘制鲸鱼娘；`look.accessory` 决定配件（Star 画星星等）。纯 Canvas 矢量。

**`ui/PetPanel.kt`**：`class PetPanel(context, onRest:()->Unit, onRestDone:()->Unit, onOpenPacks:()->Unit, onClose:()->Unit) : LinearLayout`。面板入口：常态显示「歇一会儿」(onRest)；进入休息卡后显示久坐提醒文案 +「我歇好了」(onRestDone，退出休息并重置 45min 计时)；「换陪伴包」(onOpenPacks)；「×」(onClose)。`[r3 校准：新增 onRestDone，与 T5/T7 实现一致]`。安静模式开关在设置页，不在面板。

**`ui/SpeechBubble.kt`**：`class SpeechBubble(context) : TextView`（或 FrameLayout 包裹）。**奶白底、深蓝字**、圆角、带小尾巴（可选）；`fun show(text:String)` 更新文字。

---

## 5. 跨文件不变量与风险（强制）

1. **id 稳定**：`GamePlugin.id`（及 `PluginSection.id`、`TipCard.key`）发布后**不可改**。每日勾选按 `check_<pluginId>_<sectionId>_<index>` 存；改 id/sectionId 或调换 checklist 顺序都会导致用户勾选数据错位/丢失。`reviewer` 必须核对 `SkyPlugin` 的 id 在代码与文档一致。
2. **imageName 回退矢量**：`PetLook.imageName` 指向不存在的 drawable 时，**必须回退矢量**（`PetArt`/`WhaleGirlArt`），不得崩溃、不得显示空白。`executor` 用 `try/catch`/`getIdentifier` 判空，禁止编译期硬引用不存在资源。
3. **无联网**：全工程**不得**有网络权限、不得引入网络库、不得有 socket/http 调用（`reviewer` 用 grep 检查 `okhttp|retrofit|HttpURLConnection|Socket|INTERNET`）。
4. **不读取游戏画面**：**不得**使用 `MediaProjection`/`ScreenCapture`/`AccessibilityService` 抓屏；桌宠只做悬浮显示与 UsageStats 判前台。
5. **位置只存 x/y，不感知横竖屏**：`Prefs` 只存 `posX/posY`（单组），旋转后不区分方向；显示时按**当前屏幕**做边界夹取（`coerceIn(0, screenW-size)` 等），不持久化 orientation。
6. **无真实密钥**：代码/资源/文档**绝不含**任何 API Key、token、密码、签名口令。本工程不联网，本身也不需要密钥；如出现任何 `sk-`/`AKIA`/`password=` 均为违约。
7. **包名唯一**：`applicationId`、`namespace`、源码包前缀统一 `com.skypet.pet`，Manifest 组件用相对名（`.MainActivity`、`.overlay.PetOverlayService`）解析到该包。
8. **前台服务必须带 type**：`startForeground(id, notif, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)`，且 Manifest `foregroundServiceType="specialUse"` + `<property>` 子类型；否则 Android 14+ 崩溃。
9. **默认插件唯一入口**：所有“当前插件”读取都经 `PluginRegistry.byId(prefs.pluginId)`，未知 id 回退 `plugins.first()`，保证 UI/服务不 NPE。
10. **尺寸钳制**：任何写入 `sizeDp` 的路径都 `coerceIn(72,144)`，默认 108。

---

## 6. 给 UI-SPEC 的数值令牌需求（`ui-designer` 必须补齐这些值）

> 本契约冻结**语义与常量名**，具体数值由 `SkyPet/docs/UI-SPEC.md` 提供。UI-SPEC 至少需给出下表每个令牌的**具体 hex / dp / sp**，且与 `ui/Theme.kt` 一致。

| 令牌 | 类型 | 用途 | 需 UI-SPEC 给值 |
|---|---|---|---|
| `mistBlue` | hex | 页面背景/雾蓝基调 | ✔ |
| `skirtBlue` | hex | 主按钮/强调/裙装蓝 | ✔ |
| `creamWhite` | hex | 气泡底/卡片底/奶白 | ✔ |
| `lightPink` | hex | 点缀/浅粉 | ✔ |
| `seaSaltBlue` | hex | 次级背景/分隔/海盐蓝 | ✔ |
| `deepBlueText` | hex | 正文/气泡字/深蓝 | ✔ |
| `onPrimary` | hex | 主色上文字色 | ✔ |
| `cornerRadiusDp` | dp | 通用圆角 | ✔ |
| `cardPadDp` | dp | 卡片内边距 | ✔ |
| `bubblePadDp` / `bubbleCornerDp` | dp | 气泡内边距/圆角 | ✔ |
| `navIconDp` | dp | 底部导航图标尺寸 | ✔ |
| `PET_SIZE_DEFAULT_DP` | dp | 桌宠默认 108 | 固定 108 |
| `PET_SIZE_MIN_DP` / `PET_SIZE_MAX_DP` | dp | 72 / 144 | 固定 72/144 |
| 字号（标题/正文/气泡/标签） | sp | 各层级字号 | ✔ |
| 页间距/导航高度 | dp | 三页布局 | ✔ |
| 三页布局与底部导航三图标（陪伴/陪伴包/设置）选中实心/未选中描边 | 规范 | 交互验收 | ✔ |
| 面板“歇一会儿”、气泡“奶白底深蓝字” | 规范 | 视觉验收 | ✔ |

---

## 7. 与 README 的映射核对表（供 reviewer 逐条验收）

| README 要求 | 承载文件 | 契约条款 |
|---|---|---|
| 三页导航（陪伴/陪伴包/设置） | MainActivity + Home/Packs/Settings | §1, §4.7 |
| 悬浮桌宠三窗 + 拖拽/点击 + 气泡 + 计时 | PetOverlayService | §4.6 |
| 每日勾选按 插件id/分区id/序号、跨天失效 | Prefs | §4.3 |
| 位置只存 x/y、不感知横竖屏 | Prefs + Service | §4.3, §5.5 |
| 尺寸 72–144 默认 108 | Prefs + Service + Theme | §4.3, §4.6, §4.7 |
| 图片优先级 导入>pet_whale.png>矢量 | PetImageStore + PetArt + WhaleGirlArt | §4.4, §4.7 |
| UsageStats 判前台/缺失退化始终显示 | ForegroundApp + Service | §4.5 |
| 安静陪伴（不主动冒泡） | Prefs + Service | §4.3, §4.6 |
| 45min 休息提醒 | Prefs + Service | §4.6 |
| 只在游戏里出现（gamePackages 空=通用） | ForegroundApp + PluginRegistry | §4.2, §4.5 |
| 5 项权限 + specialUse 前台服务 | AndroidManifest | §3 |
| 不联网 / 不读画面 / 无密钥 | 全工程 | §5 |

---

## 8. 本机无法验证的项（必须在 BUILD-VERIFY / 审查结论中如实标注）

| 类别 | 项 | 为什么本机不能验证 | 标记 |
|---|---|---|---|
| 构建 | `gradle sync` / `assembleDebug` 能否成功 | 本机**无 Android SDK / Gradle / AGP** | **未验证** |
| 构建 | `gradle-wrapper.jar` 合法性 | 二进制需 Gradle 生成 | **未验证**（可为占位） |
| 编译 | Kotlin 编译零错误 | **无 kotlinc / Android SDK** | **未验证** |
| 工具链 | JDK17 toolchain 能否解析 | 本机仅 JDK21，无 SDK；17 是否可获取未知 | **未验证** |
| 运行 | 悬浮窗真实显示、手势、气泡、通知 | **无设备/adb/模拟器** | **未验证** |
| 运行 | UsageStats 降级在真机 ROM 行为 | 无设备 | **未验证** |
| 视觉 | 截图 / 真实渲染截图 | 无设备、无渲染 | **未验证** |
| 静态 | 目录树/包名/权限/签名/XML 良构/括号配对/资源引用 | **可用 Python 静态检查** | **可验证（静态）** |

> `executor` 必须只在 BUILD-VERIFY.md 声明“静态结构/逻辑级验证通过”，并逐项列出上表的**未验证**项；`reviewer` 不得把“未编译/未真机/无截图”当作缺陷，但必须核对“是否如实标注”。

---

## 9. 交付与验收

- 本契约的唯一交付物：`SkyPet/docs/CONTRACT.md`（本文件）。
- 契约完成后由 captain 解锁 `t3`（executor 实现），`executor` 必须**严格按本文件**产出 19 个 `.kt` + 构建脚本 + Manifest + res，并在 `docs/BUILD-VERIFY.md` 记录静态验证与 §8 未验证项。
- `reviewer` 按 §7 映射表 + §5 不变量 + §8 未验证清单逐条给证据判定。
- 任何契约冲突以本文件为准；`executor` 若发现不可实现，须立即回报 captain 触发契约修订，不得私自偏离。

---
*（本文件为 v0.2 冻结契约 r1。修改需走显式契约修订流程。）*
