package com.skypet.pet.plugins

import com.skypet.pet.core.plugin.Accessory
import com.skypet.pet.core.plugin.GamePlugin
import com.skypet.pet.core.plugin.PetLook
import com.skypet.pet.core.plugin.PluginSection
import com.skypet.pet.core.plugin.TipCard
import com.skypet.pet.ui.UiColors

/**
 * 通用兜底插件：gamePackages = emptyList()，表示任何前台应用都显示（通用）。
 * id 发布后不可改（"generic"）。
 */
class GenericPlugin : GamePlugin {

    override val id: String = "generic"
    override val name: String = "通用陪伴"
    override val tagline: String = "不限场景，一直都在"

    override val gamePackages: List<String> = emptyList()

    override val look: PetLook = PetLook(
        body = UiColors.seaSaltBlue,
        belly = UiColors.creamWhite,
        accent = UiColors.mistBlue,
        glow = UiColors.lightPink,
        accessory = Accessory.None,
        imageName = null
    )

    override fun greeting(): String = "累了就休息一下吧。"

    override val idleLines: List<String> = listOf(
        "我在这儿陪着你。",
        "需要的话，点我一下就好的。",
        "慢慢来，不用着急。",
        "喝口水，伸个懒腰吧。"
    )

    override val sections: List<PluginSection> = listOf(
        PluginSection(
            id = "generic_daily",
            title = "今日小目标",
            checklist = listOf(
                "喝够 6 杯水",
                "起身走动 5 分钟",
                "远眺放松眼睛",
                "完成一件想做的事"
            ),
            cards = listOf(
                TipCard(
                    key = "generic_tip_rest",
                    title = "久坐提醒",
                    desc = "已经陪伴一段时间啦，站起来舒展一下身体吧。",
                    tag = "健康"
                )
            )
        )
    )
}
