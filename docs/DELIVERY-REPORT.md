# 鲸伴 SkyPet v0.2 · 交付与验收报告（队长）

> 生成：Team Lead（captain） ｜ 工程根：`D:\work_dsh\SkyPet`
> 本机环境：Windows / **Temurin JDK 17.0.13 + Gradle 8.9 + Android SDK 35**（已配置于 `D:\work_dsh\AndroidDev`）
> **✅ 已真实编译通过：`gradlew clean assembleDebug` → BUILD SUCCESSFUL，APK 5,671,476 bytes**
> **⚠️ 仍未经真机运行验证**（本机无 Android 设备/模拟器）

---

## 1. 交付物清单

| # | 交付物 | 路径 | 状态 |
|---|---|---|---|
| 1 | 完整 Gradle 工程（19 个 .kt + res + 构建脚本） | `SkyPet/` | ✅ 已落盘 / **已编译通过** |
| 2 | 技术契约（单一真相源） | `SkyPet/docs/CONTRACT.md` | ✅ 冻结（含 r2/r3 校准） |
| 3 | UI / 交互规范 | `SkyPet/docs/UI-SPEC.md` | ✅ 冻结（配色规则 v2） |
| 4 | 可点击静态原型 | `SkyPet/preview/whale-ui.html` | ✅ 单文件无外部依赖 |
| 5 | 原型浏览器截图（7 张） | `SkyPet/preview/shots/` | ✅ 桌面 Chrome 无头渲染 |
| 6 | 构建/验证记录 | `SkyPet/docs/BUILD-VERIFY.md` | ✅ 含 §7/§9/§10 修复轮 |
| 7 | 独立静态校验脚本 | `SkyPet/docs/_static_check.py` | ✅ 可复现 |
| 8 | **Debug APK** | `SkyPet/app/build/outputs/apk/debug/app-debug.apk` | ✅ **BUILD SUCCESSFUL**（5,671,476 bytes） |
| 9 | **Android 构建环境** | `D:\work_dsh\AndroidDev\` | ✅ JDK17 + Gradle8.9 + SDK35 + adb |

### 工程结构（与 README §3 对齐）
```
SkyPet/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/wrapper/gradle-wrapper.properties (Gradle 8.9) + .template
├── app/build.gradle.kts (compileSdk35/minSdk26/targetSdk35, Java17, 纯 View)
├── app/src/main/AndroidManifest.xml (5 权限 + specialUse)
├── app/src/main/java/com/skypet/pet/   ← 19 个 .kt
│   ├── MainActivity.kt
│   ├── core/{Prefs, PetImageStore, ForegroundApp}.kt
│   ├── core/plugin/{GamePlugin, PluginRegistry}.kt
│   ├── plugins/{SkyPlugin, GenericPlugin}.kt
│   ├── overlay/PetOverlayService.kt
│   └── ui/{Theme, UiKit, Icons, HomeScreen, PacksScreen, SettingsScreen,
│            PetArt, WhaleGirlArt, PetPanel, SpeechBubble}.kt
├── app/src/main/res/ (values 3 + drawable 3 + mipmap 2 = 9 XML)
├── preview/whale-ui.html + shots/
└── docs/{CONTRACT.md, UI-SPEC.md, BUILD-VERIFY.md, _static_check.py}
```

---

## 2. 本次实跑的静态验证（队长独立执行，非成员声明）

`python SkyPet/docs/_static_check.py` → **STATIC_OK = True**

| 检查 | 结果 |
|---|---|
| Kotlin 文件数 | 19（与契约一致） |
| XML 文件数 / 良构解析 | 9 / 全部通过 |
| Kotlin 花括号平衡 | 0 异常 |
| 双反斜杠 unicode 残留 | 0 |
| Manifest 5 权限 | 全部声明 |
| foregroundServiceType=specialUse | ✅ |
| PROPERTY_SPECIAL_USE_FGS_SUBTYPE | ✅ |
| INTERNET 权限 | 未声明（符合“不联网”） |
| RECEIVE_BOOT_COMPLETED | 未声明（符合“无开机自启”） |
| 真实密钥扫描（sk-/AIza/ghp_） | 0 |
| ReminderLogic / shouldFireReminder / resting / completeRest | 全部存在 |
| started=false 停止路径 / isPetVisibleNow | 全部存在 |
| onResume 注册 / onPause 注销 | ✅ |

README 功能锚点复核：尺寸默认 108/范围 72–144、气泡不可触摸、通知「收起桌宠」、安静陪伴、只在游戏里出现、45min 提醒、导入图复制、pet_whale 兜底、SkyPlugin 四区（daily/candle/spirits/terms）——全部命中。

---

## 3. 验收矩阵（README v0.2 功能点）

| 功能 | 代码级 | 证据级别 |
|---|---|---|
| 悬浮桌宠默认 108dp、72–144 可调 | ✅ | 静态（Theme.kt 常量 + Prefs clamp） |
| 呼吸浮动 / 眨眼 / 笑 | ✅ | 代码级（ValueAnimator + 表情重载），**未真机验证** |
| 拖动换位置（只存 x/y） | ✅ | 代码级（Touch + WindowManager），**未真机验证** |
| 点一下 → 右侧小助手面板 | ✅ | 代码级（右侧优先/边缘翻转），**未真机验证** |
| 面板「歇一会儿」→ 久坐文案 →「我歇好了」 | ✅ | 代码级 + 纯函数状态机推演（45/90/135） |
| 不可触摸气泡（奶白底深蓝字） | ✅ | 代码级（FLAG_NOT_TOUCHABLE + UiColors） |
| 前台服务常驻 + 通知「收起桌宠」 | ✅ | 代码级（specialUse + ACTION_STOP；通知动作文案已对齐 README「收起桌宠」） |
| 插件切换改内容与配色、形象不变 | ✅ | 代码级（PluginRegistry + look 驱动） |
| 每日任务勾选、跨天重置 | ✅ | 代码级（pluginId/sectionId/index + KEY_CHECK_DATE） |
| 光遇内容：任务/蜡烛路线/先祖/术语 | ✅ | 代码级（四 section 齐全） |
| 只在游戏里出现（UsageStats 降级） | ✅ | 代码级（无权限 → 始终显示） |
| 安静陪伴 | ✅ | 代码级（quietCompanion 门控） |
| 休息提醒每 45 分钟 | ✅ | 代码级 + 纯函数时间线推演 |
| 相册换形象 / pet_whale.png 优先级 | ✅ | 代码级（导入 > pet_whale > 矢量） |
| **「能编译 / 能真机运行」** | ✅ 编译通过 / ❌ 真机未验证 | **BUILD SUCCESSFUL + APK 已产出；无设备** |

---

## 4. 未验证项（必须如实告知）

1. **✅ 编译已通过**——`gradlew clean assembleDebug` 在本机真实执行成功，产出 `app-debug.apk`（5,671,476 bytes）。
2. **❌ 真机运行未验证**——悬浮窗、手势、动画、45min 真实计时、UsageStats、通知动作均无设备证据（无 Android 设备/模拟器）。
3. **✅ `gradlew` / `gradlew.bat` / `gradle-wrapper.jar` 已生成**（43,504 bytes）。
4. **✅ JDK17 toolchain 已解析**——使用 Temurin JDK 17.0.13（含 jlink）。
5. **UI 截图仅为桌面浏览器渲染**（`preview/shots/`），不是 Android 真机截图。
6. **未运行单元/仪器测试**——工程未包含测试代码。

## 5. 残余风险

| 风险 | 影响 | 缓解 |
|---|---|---|
| 首次 Sync 可能有编译错误 | 无法直接 Run | ✅ 已实际编译通过（修复了 Icons.kt 的 PathParser API） |
| UsageStats 厂商 ROM 差异 | “只在游戏里出现”可能需额外后台许可 | 已实现无权限降级为始终显示 |
| 横竖屏切换位置不重算 | 桌宠可能偏离 | README 已知限制，建议手动拖动 |
| 气泡贴顶被夹到 y=0 | 顶部位置气泡遮挡 | 已 coerce 到 0（符合 README） |
| 无自动化测试 | 回归风险 | 建议补 ReminderLogic / Service attach / Prefs 跨天单测 |
| 质量门未终审 pass | 无独立终审结论 | 队长独立静态校验 + 真实编译通过；建议 SDK 环境补一次真实复审 |
| 未真机验证 | 运行时行为未知 | 已配置 adb，可 `adb install` 后按清单验收 |

---

## 6. 质量门历史（如实）

| 轮次 | 任务 | 结论 |
|---|---|---|
| r1 | t4 review | needs_revision（R1–R10） |
| r2 | t6 review | needs_revision（Q1–Q6） |
| r3 | t8 review | needs_revision（S1 medium / S2 low） |
| r3 修复 | t9 | ✅ completed（S1/S2 已落盘并静态复核） |
| 终审 | t10 | ⚠️ **未完成**——reviewer 模型配额 429（基础设施故障），改由队长独立静态校验替代 |

> 结论：**代码级 / 静态级通过；编译、真机、末轮独立复审未完成**。不得表述为“已编译通过”或“真机验证通过”。

---

## 7. 下一步（真机验证）

构建环境已就绪（`D:\work_dsh\AndroidDev`），APK 已产出：

```powershell
. D:\work_dsh\AndroidDev\env.ps1
cd D:\work_dsh\SkyPet
.\gradlew.bat assembleDebug        # 已验证 BUILD SUCCESSFUL
adb devices                        # 连接手机后
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.skypet.pet/.MainActivity
```

真机检查清单见 `D:\work_dsh\AndroidDev\README.md` 与 BUILD-VERIFY §6。
重点：悬浮窗权限 → 通知权限 → 开始陪伴 → 拖动/点击/面板/气泡/尺寸/插件切换/45min 提醒。
