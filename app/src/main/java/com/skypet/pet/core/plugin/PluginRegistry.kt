package com.skypet.pet.core.plugin

import com.skypet.pet.plugins.GenericPlugin
import com.skypet.pet.plugins.SkyPlugin

/**
 * 内置插件注册表。顺序即 UI 展示顺序；首个为默认。
 * FROZEN by CONTRACT §4.2 — `plugins` must be List<GamePlugin>, `byId` falls back to first.
 */
object PluginRegistry {

    /** 内置插件表；顺序即 UI 展示顺序。首个为默认。 */
    val plugins: List<GamePlugin> = listOf(
        SkyPlugin(),
        GenericPlugin()
    )

    /** 未知/空 id 一律回退到默认插件（plugins.first()），保证 UI/服务永不 NPE。 */
    fun byId(id: String?): GamePlugin =
        plugins.firstOrNull { it.id == id } ?: plugins.first()

    fun default(): GamePlugin = plugins.first()
}
