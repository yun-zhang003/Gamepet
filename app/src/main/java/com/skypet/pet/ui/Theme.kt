package com.skypet.pet.ui

import android.graphics.Color

/**
 * UiKit 配色令牌（ARGB Int）。数值取自 UI-SPEC §1（hex），与 res/values/colors.xml 对齐。
 * 只放令牌常量，不写布局（CONTRACT §4.7）。
 */
object UiColors {
    /** 雾蓝 — 次级填色、开关轨道、角色头发（#B8D7E8）。 */
    val mistBlue: Int = Color.parseColor("#B8D7E8")

    /** 裙装蓝 — 主按钮、裙装、导航选中（#4778A8）。 */
    val skirtBlue: Int = Color.parseColor("#4778A8")

    /** 奶白 — 全局底色、气泡、卡片（#FFF9EE）。 */
    val creamWhite: Int = Color.parseColor("#FFF9EE")

    /** 浅粉 — 陪伴舞台、腮红（#F7E4E8）。 */
    val lightPink: Int = Color.parseColor("#F7E4E8")

    /** 海盐蓝 — 地平线、封面海面（#88BACB）。 */
    val seaSaltBlue: Int = Color.parseColor("#88BACB")

    /** 深蓝字 — 正文、图标描边、气泡文字（#243E57）。 */
    val deepBlueText: Int = Color.parseColor("#243E57")

    /** 主色上的文字色 — 白色（#FFFFFF）。 */
    val onPrimary: Int = Color.parseColor("#FFFFFF")

    // Extra tokens from UI-SPEC §1 (kept here so screens share one source).
    /** 次级文字（#53697B）。 */
    val mutedInk: Int = Color.parseColor("#53697B")

    /** 分隔线（#D3E0E4）。 */
    val lineBlue: Int = Color.parseColor("#D3E0E4")

    /** 纯白（#FFFFFF）。 */
    val paperWhite: Int = Color.parseColor("#FFFFFF")
}

/**
 * 尺寸/字号令牌（dp / sp）。数值取自 UI-SPEC §2。
 * PET_SIZE_* 为 README 硬要求：默认 108，范围 72..144。
 */
object UiDims {
    val cornerRadiusDp: Float = 24f
    val cardPadDp: Float = 16f
    val bubblePadDp: Float = 12f
    val bubbleCornerDp: Float = 16f
    val navIconDp: Float = 24f

    // Layout tokens (UI-SPEC §2)
    val pagePadDp: Float = 24f
    val sectionGapDp: Float = 20f
    val rowGapDp: Float = 12f
    val smallCornerDp: Float = 14f
    val navHeightDp: Float = 72f
    val primaryButtonHeightDp: Float = 52f
    val listRowMinHeightDp: Float = 60f
    val touchTargetDp: Float = 48f
    val checkIconDp: Float = 20f
    val stageHeightDp: Float = 252f
    val stageCornerDp: Float = 28f

    // Font sizes (sp, UI-SPEC §2)
    val titleSp: Float = 28f
    val sectionTitleSp: Float = 18f
    val bodySp: Float = 14f
    val noteSp: Float = 12f
    val buttonSp: Float = 16f
    val bubbleSp: Float = 14f

    // Pet size (README / CONTRACT §4.3 / §5.10)
    const val PET_SIZE_DEFAULT_DP = 108
    const val PET_SIZE_MIN_DP = 72
    const val PET_SIZE_MAX_DP = 144
}
