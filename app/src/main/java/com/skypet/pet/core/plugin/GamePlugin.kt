package com.skypet.pet.core.plugin

/**
 * 配件枚举/密封类。至少含 Star（星星）；可扩展。
 * FROZEN by CONTRACT §4.1 — do not rename or remove members.
 */
sealed class Accessory {
    data object None : Accessory()
    data object Star : Accessory()
    data object Bubble : Accessory()
    data object Note : Accessory()
}

/**
 * 宠物外观。颜色均为 ARGB Int（android.graphics.Color）。
 * imageName: 指向 res/drawable 的图片名（不含扩展名）；null 或找不到时回退矢量绘制。
 *
 * Constructor order is FROZEN: body, belly, accent, glow, accessory = Accessory.None, imageName = null.
 */
data class PetLook(
    val body: Int,
    val belly: Int,
    val accent: Int,
    val glow: Int,
    val accessory: Accessory = Accessory.None,
    val imageName: String? = null
)

/** 每日清单里的一张提示卡片。Constructor order: key, title, desc, tag. */
data class TipCard(
    val key: String,
    val title: String,
    val desc: String,
    val tag: String
)

/** 一个可勾选分区（如“今日光·遇小目标”）。Constructor order: id, title, checklist, cards. */
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
 *
 * FROZEN by CONTRACT §4.1 — the 8 members below must stay byte-identical in name & type.
 */
interface GamePlugin {
    val id: String
    val name: String
    val tagline: String
    val gamePackages: List<String>
    val look: PetLook

    /** 方法，非属性（CONTRACT §4.1 强制点）。 */
    fun greeting(): String

    val idleLines: List<String>
    val sections: List<PluginSection>
}
