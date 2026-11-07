# 鲸伴 SkyPet v0.2 · 构建与验证记录（BUILD-VERIFY）

> 产出：`executor`（t3 初版；t5 修复轮更新）。本文件只记录**本机可做的静态/结构/逻辑级验证**。
> **编译、真机、截图、wrapper.jar 合法性、JDK17 toolchain 解析在本机均无法验证，见 §6「未验证项」。**
> 构建真相源：`SkyPet/docs/CONTRACT.md` 与 `SkyPet/docs/UI-SPEC.md`。本工程**纯 View 体系，禁用 Compose**（UI-SPEC 早期“Compose 实现”措辞已由 captain 校正为 View）。

---

## 0. 本机环境（实测）

| 项 | 值 |
|---|---|
| OS | Windows（工作区 `D:\work_dsh`） |
| JDK | OpenJDK **21.0.11**（Microsoft build）——`java -version` 实测 |
| Python | 3.13.2（用于静态校验） |
| Gradle | **未安装**（`which gradle` 无输出） |
| Android SDK / adb / kotlinc | **未安装**（`ANDROID_HOME` 空；无 `android` 命令） |

结论：**无法真实执行 `./gradlew assembleDebug`**（无 Gradle 分发与 Android SDK）。以下验证均为静态结构/逻辑级。

---

## 1. 工程目录树（实际产出）

```
SkyPet/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/
│   ├── gradle-wrapper.properties           # Gradle 8.9
│   ├── gradle-wrapper.properties.template   # 供 Studio 重命名/自生成
│   └── gradle-wrapper.jar.README.txt        # 占位说明（真 jar 需 Gradle 生成，未验证）
├── app/
│   ├── build.gradle.kts                     # compileSdk35/minSdk26/targetSdk35, JDK17
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/skypet/pet/            # 19 个 .kt（见 §2）
│       └── res/
│           ├── values/{strings,colors,themes}.xml
│           ├── drawable/{ic_launcher_background,ic_launcher_foreground,pet_default}.xml
│           ├── mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}.xml
│           └── drawable-nodpi/               # pet_whale.png 可选内置整图（本工程不提供→走矢量兜底，见 §3.3）
├── preview/whale-ui.html                     # ui-designer 产出，不参与编译
└── docs/{CONTRACT.md, UI-SPEC.md, BUILD-VERIFY.md}
```

> 说明：CONTRACT §1 目录树未列出 `gradlew`/`gradlew.bat` 脚本，且其不在 in-scope 明文（in-scope 为 `gradle/**`）。为不越界，本工程**不提交 `gradlew`/`gradlew.bat`**；wrapper 配置（`gradle-wrapper.properties`=8.9 与 `.template`）按契约齐备。缺的启动脚本与 `gradle-wrapper.jar` 同样在装有 Gradle/Studio 的环境用 `gradle wrapper --gradle-version 8.9` 一次生成（同时产出 jar 与两个脚本），见 §6。

---

## 2. Kotlin 源文件清单（**实际 19 个**，覆盖 CONTRACT §1 全部具名文件）

> **计数说明（R10）**：CONTRACT 正文曾自述“16 个 .kt”，但 §1 目录树逐文件枚举实为 **19** 个具名文件。**本工程实际落地 19 个 `.kt`**；`kt_count` 校验（§3.1 V1）读数 19。契约正文计数口径已由负责人（captain）在 CONTRACT 校正；本文件按**真实 19** 记录，不夸大。

| # | 文件 | 包 | 关键内容（t5 后） |
|---|---|---|---|
| 1 | `MainActivity.kt` | `com.skypet.pet` | 三页导航、权限引导、开始/停止陪伴、相册导入；**R1 尺寸回调→refreshServiceIfRunning** |
| 2 | `core/Prefs.kt` | `.core` | 全部持久化、冻结键名、每日勾选跨天失效、sizeDp clamp 72..144 |
| 3 | `core/PetImageStore.kt` | `.core` | 导入图复制（decodeFile 原尺寸）、清除、导入>pet_whale 优先级 |
| 4 | `core/ForegroundApp.kt` | `.core` | UsageStats 前台包名 + 无权限降级恒显示 |
| 5 | `core/plugin/GamePlugin.kt` | `.core.plugin` | `Accessory`/`PetLook`/`TipCard`/`PluginSection`/`GamePlugin` 冻结签名 |
| 6 | `core/plugin/PluginRegistry.kt` | `.core.plugin` | `plugins: List<GamePlugin>`、`byId` 回退 first() |
| 7 | `plugins/SkyPlugin.kt` | `.plugins` | **R6：4 个 section（每日任务/蜡烛路线/先祖/入门术语），id 稳定** |
| 8 | `plugins/GenericPlugin.kt` | `.plugins` | 通用插件（id="generic", gamePackages=[]） |
| 9 | `overlay/PetOverlayService.kt` | `.overlay` | 三窗+手势+气泡+15s tick+45min；**R2 休息态/R3 started/R7 可见性/R8 reminderDue** |
| 10 | `ui/Theme.kt` | `.ui` | `UiColors` + `UiDims`（PET_SIZE 108/72/144） |
| 11 | `ui/UiKit.kt` | `.ui` | dp/sp、roundedBg、控件工具（无状态） |
| 12 | `ui/Icons.kt` | `.ui` | 纯 Canvas/Path 三图标（选中实心/未选中描边） |
| 13 | `ui/HomeScreen.kt` | `.ui` | 陪伴页；**R1 尺寸回调+拖动即时预览；R10 舞台点缀=look.accent** |
| 14 | `ui/PacksScreen.kt` | `.ui` | 陪伴包页（插件选择+封面+每日勾选+TipCard） |
| 15 | `ui/SettingsScreen.kt` | `.ui` | 设置页（形象/陪伴方式/权限/关于） |
| 16 | `ui/PetArt.kt` | `.ui` | **R4 imageName 解析 + R5 呼吸/眨眼/笑 + reduced-motion + R9 fitCenter** |
| 17 | `ui/WhaleGirlArt.kt` | `.ui` | **R10 dress=look.accent；hair=body/belly=belly/blush=glow；R5 表情/眨眼重载** |
| 18 | `ui/PetPanel.kt` | `.ui` | 面板；**R2 休息卡「我歇好了」；R8 提醒状态渲染** |
| 19 | `ui/SpeechBubble.kt` | `.ui` | 奶白底深蓝字气泡 |

---

## 3. 静态验证命令与结果（本机实际执行）

### 3.1 验收脚本（等价任务 Verify 字段）

```bash
# (V1) Kotlin 文件数量 >= 12
python -c "import glob; ks=glob.glob('SkyPet/app/src/main/java/com/skypet/pet/**/*.kt',recursive=True); print('kt_count',len(ks)); assert len(ks)>=12"
# 结果：kt_count 19   → PASS

# (V2) 全部 XML 良构（xml.dom.minidom 解析）
python -c "import glob,xml.dom.minidom as M; fs=glob.glob('SkyPet/**/*.xml',recursive=True); [M.parse(f) for f in fs]; print('xml_ok',len(fs))"
# 结果：xml_ok 9        → PASS

# (V3) 必需文件存在
python -c "import os; req=['SkyPet/settings.gradle.kts','SkyPet/app/build.gradle.kts','SkyPet/app/src/main/AndroidManifest.xml','SkyPet/docs/BUILD-VERIFY.md']; miss=[f for f in req if not os.path.exists(f)]; print('missing',miss); assert not miss"
# 结果：missing []      → PASS
```

### 3.2 深度结构/逻辑静态校验（8 项）

说明：8 项深度检查由一次性辅助脚本完成（已随临时文件清理，未纳入工程）。其原始输出记录如下（真实执行结果），并附 §3.2b 可直接运行的自包含等价命令。t5 修复后复跑仍全绿。

```
[1] package<->dir consistency: 19 files, 0 problems
[2] forbidden deps/network/screen-capture/INTERNET: 0 problems
[3] GamePlugin interface members + 2 implementations: 0 problems
[4] brace/paren balance: 0 problems
[5] Prefs frozen keys + clamp + cross-day methods: 0 problems
[6] Manifest permissions/components: 0 problems
[7] Cross-file invariants: 0 problems
[8] secret scan: 0 problems
=== RESULT ===
ALL STRUCTURAL CHECKS PASSED
kt_files=19
```

### 3.2b 自包含等价校验命令（可直接复现）

```bash
python - <<'PY'
import re,glob
kt=glob.glob('SkyPet/app/src/main/java/com/skypet/pet/**/*.kt',recursive=True)
mani=open('SkyPet/app/src/main/AndroidManifest.xml',encoding='utf-8').read()
prefs=open('SkyPet/app/src/main/java/com/skypet/pet/core/Prefs.kt',encoding='utf-8').read()
svc=open('SkyPet/app/src/main/java/com/skypet/pet/overlay/PetOverlayService.kt',encoding='utf-8').read()
pkg_bad=0
for p in kt:
    pkg=re.search(r'^\s*package\s+([\w\.]+)',open(p,encoding='utf-8').read(),re.M).group(1)
    rel=p.replace('\\','/').split('java/')[-1].rsplit('/',1)[0].replace('/', '.')
    pkg_bad += (pkg != rel)
forbidden=[t for t in ['androidx.compose','okhttp','retrofit','HttpURLConnection','MediaProjection','AccessibilityService','ScreenCapture'] if any(t in open(f,encoding='utf-8').read() for f in kt)]
perms=all(('android.permission.'+x) in mani for x in ['SYSTEM_ALERT_WINDOW','FOREGROUND_SERVICE','FOREGROUND_SERVICE_SPECIAL_USE','POST_NOTIFICATIONS','PACKAGE_USAGE_STATS'])
print('pkg_bad',pkg_bad,'forbidden',forbidden,'perms',perms,
      'specialUse','foregroundServiceType="specialUse"' in mani and 'PROPERTY_SPECIAL_USE_FGS_SUBTYPE' in mani,
      'no_internet','android.permission.INTERNET' not in mani,
      'fgs_type','FOREGROUND_SERVICE_TYPE_SPECIAL_USE' in svc,
      '45min','45 * 60 * 1000' in svc,'tick15s','15_000' in svc,
      'clamp','coerceIn(UiDims.PET_SIZE_MIN_DP, UiDims.PET_SIZE_MAX_DP)' in prefs,
      'crossday','resetTodayIfNewDay' in prefs and 'check_date' in prefs)
PY
```
预期输出：`pkg_bad 0 forbidden [] perms True specialUse True no_internet True fgs_type True 45min True tick15s True clamp True crossday True`。

### 3.3 关键运行时语义（静态核对，非运行验证）

- **每日勾选跨天失效**：`Prefs.resetTodayIfNewDay()` 比较 `check_date`（`yyyy-MM-dd` 本地时区），不同则移除 `check_` 前缀项并写今日；`check_<pluginId>_<sectionId>_<index>`。
- **位置只存 x/y**：Service 首次按 `screenW-size-24dp`、`screenH/3` 计算；之后与拖拽均 `coerceIn(0, screenW-size)` 夹取；旋转按当前屏夹取，不存方向。
- **尺寸 72–144 默认 108**：`Prefs.sizeDp` setter clamp；`Theme.UiDims.PET_SIZE_*`；HomeScreen SeekBar 范围即 72..144；**R1** 松手回调→`refreshServiceIfRunning()`→服务依最新 sizeDp 重建宠物窗并夹取位置。
- **图片优先级（含 imageName，R4/R9）**：`PetArt.loadBitmap(context, look, baseBitmap)`：① `baseBitmap`（= `PetImageStore.loadPetBitmap()`，即用户导入图 > 内置 pet_whale.png）；② `look.imageName` 经 `getIdentifier(name,"drawable",pkg)`（无效/null→跳过）；③ 均无→矢量 `WhaleGirlArt.draw`。三路径代码级可证。图片均 `fitCenter` 等比（`render` 与 `PetView.onDraw` 两处），不拉伸、不裁剪，View 不设背景→透明。
- **动画（R5）**：`PetArt.PetView` 呼吸（半周期 1.8s，REVERSE 合计 3.6s，scaleY 0.98–1.02，浮动约 2dp）、眨眼（4–7s 随机，闭眼 120ms）、点击 `smile()` 约 1s；`Settings.Global.ANIMATOR_DURATION_SCALE==0` 时 reduced-motion 停循环；`onDetachedFromWindow`/`releaseAnimations()` 取消 ValueAnimator 与 handler 回调。点击热区由监听 petView 的悬浮窗决定，动画不改热区。
- **UsageStats 降级**：`ForegroundApp.shouldShowFor()` — 无权限/`currentPackage()==null`/`gamePackages` 空 → true；否则 `current in gamePackages`。
- **休息流程（R2）**：`enterRest()` 置 `resting=true`、清 `reminderDue`、`restBaseline=now`、显示休息卡（久坐文案+「我歇好了」）；`completeRest()`（「我歇好了」）退出休息并 `restBaseline=now` 重新计时；`onTick` 在 `resting` 时跳过 45min 判定。
- **安静陪伴（R8）**：quiet 下 45min 到点仍置 `reminderDue=true` 并 `refreshPanelState()`（必要时创建面板）显示文案，不因 speak 被抑制而丢失；点击宠物仍回应；退出 quiet/重开提醒时 `onTick` 检测并重置 baseline、清 `reminderDue`。
- **前台规则约束三窗+气泡（R7）**：`onTick` 与 `onStartCommand` 均调 `applyVisibility(computeShouldShow())`；隐藏时 pet 置 GONE、气泡清空置 GONE、面板隐藏；`onReminderDue` 仅 `isPetVisibleNow() && !quiet` 才主动 speak。启动/配置刷新即时判定，不等 15s。
- **三窗**：pet（`TYPE_APPLICATION_OVERLAY`+`FLAG_NOT_FOCUSABLE`）、bubble（+`FLAG_NOT_TOUCHABLE`，贴 pet 上方，`coerceAtLeast(0)` 顶部夹 0）、panel（可点击，右侧优先越界翻左，宽 220dp）。
- **前台服务**：`onStartCommand` 首行 `startForegroundSafely()`（API34+ 带 `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`）；`CHANNEL_ID="skypet_overlay"`/`IMPORTANCE_LOW`；通知含 `ACTION_REST` 与 `ACTION_STOP`（`FLAG_IMMUTABLE`）。

---

## 4. README / CONTRACT §7 验收点逐条映射

| README/CONTRACT 要求 | 承载文件 | 静态证据 |
|---|---|---|
| 三页导航（陪伴/陪伴包/设置） | MainActivity + Home/Packs/Settings | `selectTab`、`buildNavItem`、`Icons.*` |
| 悬浮三窗+拖拽/点击+气泡+计时 | PetOverlayService | `attachPet/attachBubbleIfNeeded/attachPanelIfNeeded`、`handlePetTouch`、`ticker` |
| 每日勾选按 pluginId/sectionId/index、跨天失效 | Prefs | `check_<...>`、`check_date`、`resetTodayIfNewDay` |
| 位置只存 x/y | Prefs + Service | `KEY_POS_X/Y`、`coerceIn(0,screenW-size)` |
| 尺寸 72–144 默认 108 + 即时同步 | Prefs + Theme + HomeScreen + Service | setter clamp、`onSizeChanged`→服务重建 |
| 图片优先级 导入>imageName(pet_whale 内置)>矢量 | PetArt + PetImageStore + WhaleGirlArt | `loadBitmap` 三级、`getIdentifier`、`WhaleGirlArt.draw` |
| UsageStats 判前台/缺失退化始终显示 | ForegroundApp + Service | `shouldShowFor` 4 分支 |
| 安静陪伴 / 45min 休息提醒 | Prefs + Service + PetPanel | `quietCompanion`、`reminderDue`、`REST_INTERVAL_MS`、`resting` |
| 呼吸浮动/眨眼/点击笑 | PetArt + WhaleGirlArt | ValueAnimator 呼吸、blink、`smile()` |
| 5 权限 + specialUse 前台服务 | AndroidManifest | §3.2 [6] |
| 不联网 / 不读画面 / 无密钥 | 全工程 | §3.2 [2][8] |

---

## 5. 依赖与版本（静态核对）

- Gradle wrapper：`distributionUrl=...gradle-8.9-bin.zip`。
- AGP `8.7.3`、Kotlin `2.0.21`（root plugins，`apply false`）。
- `compileSdk=35 / minSdk=26 / targetSdk=35 / versionCode=2 / versionName="0.2"`；`compileOptions` Java17 + `kotlinOptions.jvmTarget="17"` + `kotlin { jvmToolchain(17) }`。
- 依赖：`androidx.core:core-ktx:1.13.1`、`androidx.appcompat:appcompat:1.7.0`、`com.google.android.material:material:1.12.0`。**无 Compose / 无网络库 / 无图片加载库**。

---

## 6. 未验证项（必须如实标注，不得视为通过）

| 类别 | 项 | 原因 | 标记 |
|---|---|---|---|
| 构建 | `gradle sync` / `assembleDebug` | 本机无 Android SDK / Gradle | **未验证** |
| 构建 | `gradle-wrapper.jar` 合法性 | 二进制需 Gradle 生成；本机占位 | **未验证** |
| 编译 | Kotlin 编译零错误 | 无 kotlinc / Android SDK | **未验证** |
| 工具链 | JDK17 toolchain 能否解析 | 仅 JDK21，无 SDK | **未验证** |
| 运行 | 悬浮窗显示/手势/气泡/通知/动画 | 无设备/adb/模拟器 | **未验证** |
| 运行 | UsageStats 降级在真机 ROM 行为 | 无设备 | **未验证** |
| 运行 | 尺寸即时重建、休息周期、45min 真机计时 | 无设备 | **未验证** |
| 视觉 | 截图/真实渲染 | 无设备 | **未验证** |

### 复现真实构建的命令（在装有 Android SDK + JDK17 的环境执行）

```bash
gradle wrapper --gradle-version 8.9        # 生成 wrapper jar 与 gradlew 脚本
./gradlew :app:assembleDebug               # Windows: .\gradlew.bat :app:assembleDebug
./gradlew :app:lintDebug
```
> 上述命令在本机**未执行**（缺 SDK/Gradle），仅作可复现指引。

---

## 7. t5 修复轮状态（R1–R10，真实实现/部分/未实现）

> 如实反映当前代码状态；“已实现”指代码路径完整、静态可核对，**不等于真机通过**（见 §6）。

| 编号 | 项 | 状态 | 说明 |
|---|---|---|---|
| R1 | 尺寸变化即时同步服务窗与绘制、位置边界 | **已实现**（未真机验证） | HomeScreen `onSizeChanged`→MainActivity `refreshServiceIfRunning()`→服务 `ACTION_START` 重建宠物窗并按当前屏夹取 x/y；拖动过程 `setPreviewSizeDp` 即时更新舞台预览 |
| R2 | 休息状态/可见休息卡/完成入口；休息暂停周期；完成/恢复提醒/退出安静重置计时 | **已实现**（未真机验证） | `resting` 暂停 45min；PetPanel 休息卡「我歇好了」→`completeRest()` 重置 `restBaseline=now`；`onTick` 检测 quiet/restReminder 变化重置 baseline |
| R3 | 真实启动成功/停止/失败状态，及时刷新 Activity，覆盖通知退出与权限失败 | **已实现**（未真机验证） | `onDestroy` 统一 `prefs.started=false`（覆盖 ACTION_STOP/通知退出/权限缺失/attach 失败）；Activity `onResume` 刷新 |
| R4 | imageName 查找加载 + 无效/null 矢量回退；明确用户图>pet_whale>imageName 关系；三路径 | **已实现**（代码级，运行未验证） | `PetArt.loadBitmap`：baseBitmap(导入>pet_whale) > `getIdentifier(imageName)` > 矢量 |
| R5 | UI-SPEC 动画、点击笑、生命周期释放、尊重减少动画、不改热区 | **已实现**（未真机验证） | PetArt.PetView：呼吸 3.6s/scaleY .98–1.02/浮动 2dp、眨眼 4–7s/120ms、点击笑 1s；ANIMATOR_DURATION_SCALE==0 停循环；`releaseAnimations`/`onDetachedFromWindow` |
| R6 | 补全可访问内容分区/卡片，保持 id 稳定，不声称实时游戏数据 | **已实现**（内容静态，非实时） | SkyPlugin 4 section：`sky_daily`(id 顺序不变)/`sky_candle`/`sky_spirits`/`sky_terms`；均为本地静态整理，非实时数据 |
| R7 | 前台规则同时约束三窗+主动气泡；启动/配置刷新立即判定；隐藏不弹泡 | **已实现**（未真机验证） | `applyVisibility(computeShouldShow())` 于 onTick 与 onStartCommand；隐藏清空三窗+气泡；`onReminderDue` 仅可见且非 quiet 才 speak |
| R8 | 到期提醒状态保存并由面板渲染；quiet 不主动但打开面板可见；重置清除 | **已实现**（未真机验证） | `reminderDue` 状态 + `refreshPanelState()`（必要时创建面板 bind）；`completeRest`/设置变化清 `reminderDue` |
| R9 | 复用 fitCenter，横/竖图不变形、透明背景 | **已实现**（代码级，运行未验证） | `render`/`PetView.onDraw` 均按 `min(w/bw,h/bh)` 等比；View 不设背景→透明 |
| R10 | 校正 19 计数、View 体系、角色配色规则，更新 BUILD-VERIFY 真实状态 | **已实现** | 本文件按真实 19 记录并明示 View 体系；WhaleGirlArt `dress=look.accent`（hair=body/belly=belly/blush=glow/accessory 形状=look.accessory 配色=look.accent）；CONTRACT 计数与 UI-SPEC 措辞由 captain 校正 |

**部分实现/局限（诚实标注）**：
- 动画与悬浮渲染、尺寸即时重建、休息计时、UsageStats 等**仅代码级验证**，真机行为**未验证**（无设备）。
- R6 游戏内容为**本地静态整理**，可能与游戏版本存在出入，**非实时数据**。
- 图片“按比例缩放”在矢量预览/悬浮窗为运行时 fitCenter（代码级）；`PetImageStore.importFromUri` 仅复制原文件、不做预缩放（显示时缩放），符合“不抠图、不改变原图”。

---

---

## 7.1 t5 修复轮静态复核（实跑，全部 exit0）

对 R1–R10 的代码级落点做了内容锚点断言（不依赖行号），全部 PASS：

- `kt_count=19`；`xml_ok=9`；`missing=[]`（V1/V2/V3）。
- 结构不变量：`pkg_dir_bad=0`、`forbidden=[]`、`brace_bad=[]`（含新增/改动的 7 个 .kt 括号平衡）、`prefs_keys_bad=[]`、`prefs_clamp=True`、`prefs_crossday=True`、Manifest `perms=True/specialUse=True/no_internet=True/no_boot=True`、Service `fgs_type=True/45min=True/tick15=True`、`registry_list=True`、`store_getid=True`、`theme_consts=True`、`secrets=[]`。
- R1：HomeScreen `setOnSizeChanged`/`onStopTrackingTouch`→`onSizeChangedCb`；MainActivity `setOnSizeChanged{refreshServiceIfRunning()}`；Service `ACTION_START` `detachAll()+attachPet()`。
- R2：`resting` 状态、`enterRest`/`completeRest`（重置 `restBaseline`）、PetPanel「我歇好了」、`onTick` 休息跳过、quiet/restReminder 变化重置 baseline。
- R3：`onDestroy`/`ACTION_STOP`/attach 失败三条路径均 `prefs.started=false`。
- R4：`PetArt.loadBitmap` `getIdentifier(name,"drawable",pkg)`；`buildView` 调用之。
- R5：`ValueAnimator` 呼吸、`blinkFraction`/`blinkRunnable`、`smile()`、`ANIMATOR_DURATION_SCALE`/`reducedMotion`、`releaseAnimations`/`onDetachedFromWindow`。
- R6：`sky_daily`/`sky_candle`/`sky_spirits`/`sky_terms` 四 section；含“非实时/静态整理”声明。
- R7：`computeShouldShow`、启动即 `applyVisibility`、隐藏时 `bubbleView?.visibility=GONE`、`isPetVisibleNow() && !prefs.quietCompanion` 才 speak。
- R8：`reminderDue`、`refreshPanelState`（必要时 `attachPanelIfNeeded`）、到期置位与重置清除。
- R9：`PetView.onDraw` 与 `render` 均 `minOf(w/bw,h/bh)` fitCenter。
- R10：WhaleGirlArt `dress=look.accent`（无 `darken`）；HomeScreen 舞台点缀 `plugin.look.accent`；计数=19。

> 上述为**静态内容断言**，不等于真机运行（见 §6）。辅助断言脚本为一次性使用，已清理，未纳入工程。

## 8. 结论

- **静态结构/逻辑级：通过**（§3.1 ×3 + §3.2 ×8 全绿；t7 见 §9）。
- **编译 / 真机 / 截图 / wrapper.jar / JDK17 toolchain：未验证**（§6）。
- R1–R10 修复：代码级已落地（见 §7 状态表），运行项未真机验证；无联网、不读画面、无密钥、id 稳定。

---

## 9. t7 修复轮（round-3，根因重构）— 提醒/休息状态机 + 启动状态 + 尺寸去抖

> 针对 t6 的 Q1–Q6 做**根因重构**而非补丁。以下“已实现”为代码级 + 逻辑推演，**真机仍未验证**（§6）。

### 9.1 根因与修法

| 编号 | 根因 | 修法（单一来源 / 纯函数） |
|---|---|---|
| Q1(R8) | `hideRestMode()` 用固定文案覆写 `reminderView`，把 `bind(reminderText())` 的真实状态覆盖；`refreshPanelState` 先 bind 再 hideRestMode 导致状态全丢 | **提醒文案单一来源**：`bind(reminderText)` 唯一写 `reminderView`；`showRestMode()/hideRestMode()` **只切 `restBtn/doneBtn` 显隐**，绝不写 `reminderView`。调用顺序无关 |
| Q2(R2) | tick 门 `!reminderDue` 且 `reminderDue` 只置 true 从不清零 → 首个 45min 后永不再触发 | 计时门抽为纯函数 `ReminderLogic.shouldFireReminder(restReminder,resting,elapsedMs,intervalMs)`，**不依赖 reminderDue**；`onReminderDue()` 内 `reminderDue=true`（仅显示）并**立即 `restBaseline=now`** 重开下一周期 → 45/90/135 每周期触发、无堆积 |
| Q3(R10) | 实现加了 `onRestDone` 构造参数，但 CONTRACT 仅记 4 参 | 负责人**已冻结 CONTRACT §602 为 5 参** `PetPanel(context,onRest,onRestDone,onOpenPacks,onClose)`；实现与该签名一致，无“隐形扩展” |
| Q4(R7) | 通知 `ACTION_REST`→`enterRest()` 无条件 `showPanel()/speak()`，绕过“只在游戏里出现” | `enterRest()/onReminderDue()` 的**展示以 `isPetVisibleNow()` 为准**：隐藏时只置内部状态、**不创建/不显示任何浮层**；`applyVisibility(true)` 时若面板开着则 `refreshPanelState()` 再展示有效状态 |
| Q5(R3) | Activity 启动前乐观 `started=true`；服务 attach 成功从不置 true；异步失败不通知 UI | **started 以服务为准**：`attachPet()` 成功后置 `prefs.started=true`，全部失败/停止路径置 false；Activity 用 `SharedPreferences` 监听器观察 `KEY_STARTED` 变化即时刷新（不止 onResume）；启动后 `postDelayed(900ms)` 重读，仍 false 则 Toast「启动失败，请检查悬浮窗权限」；启动调用异常即清状态+Toast |
| Q6(R1) | 仅 `onStopTrackingTouch` 持久化；键盘/TalkBack 只走 `onProgressChanged` 永不保存 | `onProgressChanged(fromUser)` 内**即 `prefs.sizeDp=dp` 持久化** + `setPreviewSizeDp` + 去抖 `scheduleSizeReconfig`（300ms，`removeCallbacks+postDelayed`）；`onStopTrackingTouch` 立即 `flushSizeReconfig()` |

### 9.2 状态机不变式表（正常→到期→休息→完成→再计时 / quiet / 关闭提醒 / 隐藏）

| 状态 | 触发条件（纯函数） | reminderView 文案（单一来源） | 主动气泡 | 周期计时 |
|---|---|---|---|---|
| 正常 | not resting ∧ restReminder ∧ elapsed<45min | 久坐提醒：每 45 分钟 | 否 | 累计中（baseline） |
| 到期 | `shouldFireReminder=true`（elapsed≥45min） | 已到 45 分钟，记得起身活动 | 可见∧非quiet 才「该歇一会儿啦」 | **立刻 baseline=now 重开下一周期** |
| 休息 | 用户「歇一会儿」/通知 REST | 休息中，45 分钟周期已暂停 | 可见∧非quiet 时「站起来…」 | 暂停（resting） |
| 完成 | 「我歇好了」 | 回「久坐提醒：每 45 分钟」 | 否 | baseline=now 重新完整计时 |
| quiet | — | 同上（到期仍显示文案） | **不主动**（仅面板可见） | 照常每 45min 置 due |
| 关闭提醒(restReminder=false) | `shouldFireReminder=false` | 久坐提醒已关闭 | 否 | 不触发 |
| 隐藏（前台规则 false） | computeShouldShow=false | 不改（状态保存） | 不创建浮层/不弹 | 照常累计（仅显示受抑） |

### 9.3 逻辑推演证据（Python 镜像纯函数 + tick 时间线，非关键词）

复刻 `ReminderLogic` 与 `onTick/onReminderDue/enterRest/completeRest` 的判定逻辑跑时间线（tick=15s）：

```
normal 150min           -> fire minutes [45, 90, 135]（speak 同点）       ✅ 45/90/135 均提醒、无堆积
quiet=true 150min       -> fire [45,90,135]，speaks []                      ✅ 到期计数但安静不主动弹
rest 50..70min          -> fire [45, 115]                                   ✅ 休息期(50-70)抑制、完成后 +45min=115 再触发
restReminder=false      -> fire []                                          ✅ 关闭提醒不触发
reminderText 四/六例     -> 正常/到期/安静到期/关闭/休息/完成 文案全部匹配  ✅ Q1 四态渲染正确、顺序无关
```

由于纯函数 `shouldFireReminder` 不读 `reminderDue`、`onReminderDue` 每次推进 `restBaseline`，可静态证明：**每满 45min 触发一次且只一次，遇休息暂停、完成重置、无补发堆积**。

### 9.4 t7 静态复核（实跑，全部 exit0）

- `kt_count=19`；`xml_ok=9`；`missing=[]`。
- 结构：`brace_bad=[]`、`forbidden=[]`、`no INTERNET`、Manifest `perms/specialUse`、Service `fgs_type`、`45*60*1000`、Prefs `clamp` 全 True。
- Q1：`bind` 唯一写 reminderView；`show/hideRestMode` 不含 `reminderView`；`refreshPanelState` 先 bind 后切按钮。
- Q2：`ReminderLogic.shouldFireReminder/reminderText` 纯函数存在；`onTick` 不依赖 `!reminderDue`；`onReminderDue` 推进 baseline。
- Q3：`PetPanel` 5 参构造含 `onRestDone`；CONTRACT §602 同步。
- Q4：`enterRest/onReminderDue` 受 `isPetVisibleNow()` 门控；`applyVisibility(true)` 重渲染面板。
- Q5：`attachPet` 成功置 `started=true`；Activity 用 SP 监听器 + 延迟核对 + Toast；启动异常清状态。
- Q6：`onProgressChanged(fromUser)` 持久化 + 去抖；`onStopTrackingTouch` flush。

> 仍**未验证**：编译、真机悬浮/动画/计时、真实 Activity 观察与异步 attach 时序、截图（无 SDK/设备）。代码级 + 逻辑推演成立 ≠ 真机通过。

---

## 10. t9 修复轮（round-3 剩余 S1/S2）

> 来源：t8 复审剩余两项。均为代码级修复 + 逻辑推演；**编译/真机/截图未验证**（见 §6）。

### 10.1 S1（medium）：started 监听注册/注销生命周期不配对

- **缺陷**：`MainActivity` 在 `onCreate` 注册 `startedObserver`、`onPause` 注销、`onResume` **不恢复** → 首次离开再回来后永久失去 KEY_STARTED 变化通知（首页按钮不再随服务状态刷新）。
- **修法**：改为**成对 `onResume` 注册 / `onPause` 注销**，`onCreate` **不再注册**；`onDestroy` 兜底注销。用布尔 `startedObserverRegistered` + 幂等 `registerStartedObserver()/unregisterStartedObserver()` 防重复/重复注销。
  - `onResume`：`refreshCurrentScreen()` 后 `registerStartedObserver()`（若已注册则跳过）。
  - `onPause`：`unregisterStartedObserver()`（若未注册则跳过）。
  - `onDestroy`：`unregisterStartedObserver()` + 清 `uiHandler` + `homeScreen.releasePending()`。

**两轮生命周期逻辑推演**（镜像幂等 register/unregister + observer 仅在已注册时回调）：

| 步骤 | 动作 | registered 标志 | KEY_STARTED 变化是否刷新首页 |
|---|---|---|---|
| 1 | onCreate | false（**不注册**） | — |
| 2 | onResume #1 | **true（注册1次）** | ✅ 刷新（idle→运行/停止态） |
| 3 | onPause #1 | false（注销1次） | —（暂停期无监听） |
| 4 | onResume #2 | **true（再注册1次）** | ✅ 仍刷新（**证明首次 pause/resume 后仍有效**） |
| 5 | onPause #2 | false | — |
| 6 | onDestroy（兜底） | 仍 false（幂等，无重复注销/异常） | — |

推演结论：注册/注销次数成对、幂等；第 4 步（第一次 pause→resume 之后）KEY_STARTED 变化仍能触发 `refreshCurrentScreen()`，故障已闭环修复。

### 10.2 S2（low）：今日任务文案 unicode 转义显示字面

- **缺陷**：`PetPanel` 今日任务文本含 `\u2014\u2014` 转义（历史上出现双反斜杠导致显示字面 `\u2014`）。
- **修法**：改为**真实破折号字符 `——`**（U+2014 ×2）。全工程已搜 `\\u` 双反斜杠残留：**0 处**（kt/xml/kts/html）；PetPanel 内单反斜杠 `\u2014` 也为 0，真实 `——` 计数 2。

### 10.3 t9 静态复核（实跑，全部 exit0）

- `xml_ok=9`；`kt=19`（断言 ==19 通过）；`escape_ok`（无 `\\u2014` 双反斜杠）。
- S1：onCreate 无注册、onResume 注册、onPause 注销、onDestroy 兜底注销、布尔幂等、observer 处理 KEY_STARTED→refresh 全部 PASS；两轮生命周期推演 PASS。
- 结构：19 个 .kt 括号平衡、import 形态良构；无 `INTERNET`；无密钥（sk-/AKIA/password=/api_key=）扫描 0。
- 全工程 `\\u` 双反斜杠残留：0 处。

> 仍**未验证**：编译 assembleDebug、真机悬浮/计时/Activity 真实观察时序、真机截图 —— 本机无 Android SDK/Gradle/adb/设备。以上为静态 + 逻辑推演证据，≠ 真机通过。
