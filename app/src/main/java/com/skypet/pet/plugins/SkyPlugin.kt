package com.skypet.pet.plugins

import com.skypet.pet.core.plugin.Accessory
import com.skypet.pet.core.plugin.GamePlugin
import com.skypet.pet.core.plugin.PetLook
import com.skypet.pet.core.plugin.PluginSection
import com.skypet.pet.core.plugin.TipCard
import com.skypet.pet.ui.UiColors

/**
 * 光·遇（Sky: Children of the Light）内置插件。
 *
 * id 发布后不可改（"sky"），否则用户本地每日勾选会丢失（CONTRACT §5.1）。
 * gamePackages 指向光·遇 Android 包名，用于“只在游戏里出现”的前台判定。
 *
 * 覆盖 README §1 光·遇四项内容：
 *  - 每日任务清单（checklist）                          -> section "sky_daily"（id/顺序保持稳定）
 *  - 蜡烛路线                                          -> section "sky_candle"
 *  - 先祖                                              -> section "sky_spirits"
 *  - 入门术语                                          -> section "sky_terms"
 *
 * 说明：所有内容均为本地静态整理，非实时游戏数据（不联网、不读画面），随时间可能变化，仅供参考。
 */
class SkyPlugin : GamePlugin {

    override val id: String = "sky"
    override val name: String = "光·遇"
    override val tagline: String = "在云海与星光之间，一起飞翔"

    override val gamePackages: List<String> = listOf("com.tgc.sky.android")

    override val look: PetLook = PetLook(
        body = UiColors.mistBlue,
        belly = UiColors.creamWhite,
        accent = UiColors.lightPink,
        glow = UiColors.seaSaltBlue,
        accessory = Accessory.Star,
        imageName = null
    )

    override fun greeting(): String = "今天也要记得喝水哦。"

    override val idleLines: List<String> = listOf(
        "云海今天的风，很温柔呢。",
        "记得抬头看看星星。",
        "飞累了就落在我肩上歇一会儿吧。",
        "你专注的样子，我也在偷偷看。"
    )

    override val sections: List<PluginSection> = listOf(
        // ---- 1) 每日任务清单（id/顺序保持稳定，勿改） ----
        PluginSection(
            id = "sky_daily",
            title = "今日光·遇小目标",
            checklist = listOf(
                "跑一趟云野，收一束光",
                "和好友牵手飞一圈",
                "在雨林点一支蜡烛",
                "去暮土看看星星",
                "睡前喝一杯温水"
            ),
            cards = listOf(
                TipCard(
                    key = "sky_tip_water",
                    title = "记得补水",
                    desc = "连续游玩容易忘喝水，起身接杯水再回来吧。",
                    tag = "小提醒"
                ),
                TipCard(
                    key = "sky_tip_eyes",
                    title = "让眼睛休息",
                    desc = "每玩 45 分钟，望向远处 20 秒，缓解视疲劳。",
                    tag = "护眼"
                )
            )
        ),

        // ---- 2) 蜡烛路线 ----
        PluginSection(
            id = "sky_candle",
            title = "今日蜡烛路线",
            checklist = listOf(
                "云野（晨岛）：蝶群洞口与圣岛大蜡烛",
                "雨林：躲避雨水，沿蘑菇与桥洞收烛光",
                "霞谷：滑行赛道两侧烛光与终点蜡烛",
                "暮土：谨慎通过，收集墓地与管道蜡烛",
                "禁阁：乘船上行，逐层拾取烛光"
            ),
            cards = listOf(
                TipCard(
                    key = "sky_candle_tip_route",
                    title = "跑图顺序建议",
                    desc = "按 云野→雨林→霞谷→暮土→禁阁 顺序跑，路线连贯更省时。",
                    tag = "路线"
                ),
                TipCard(
                    key = "sky_candle_tip_daily",
                    title = "每日蜡烛上限",
                    desc = "蜡烛可合成获得，注意当日上限；大致收益以游戏内为准。",
                    tag = "收益"
                )
            )
        ),

        // ---- 3) 先祖 ----
        PluginSection(
            id = "sky_spirits",
            title = "先祖与复刻",
            checklist = listOf(
                "确认本周复刻先祖位置与所需蜡烛数",
                "收集先祖动作、发型与斗篷外观",
                "留意限时回归先祖的截止日期",
                "用爱心与朋友互赠装扮"
            ),
            cards = listOf(
                TipCard(
                    key = "sky_spirits_tip_weekly",
                    title = "每周复刻",
                    desc = "复刻先祖通常每周轮换，位置与兑换清单以游戏内公告为准。",
                    tag = "先祖"
                ),
                TipCard(
                    key = "sky_spirits_tip_candle",
                    title = "攒蜡烛换装扮",
                    desc = "复刻兑换需要蜡烛与爱心，提前规划避免错过。",
                    tag = "兑换"
                )
            )
        ),

        // ---- 4) 入门术语 ----
        PluginSection(
            id = "sky_terms",
            title = "入门术语速查",
            checklist = emptyList(),
            cards = listOf(
                TipCard(
                    key = "sky_terms_candle",
                    title = "蜡烛",
                    desc = "游戏内主要货币之一，收集烛光后合成获得，用于兑换装扮与先祖物品。",
                    tag = "货币"
                ),
                TipCard(
                    key = "sky_terms_heart",
                    title = "爱心",
                    desc = "由好友间互赠获得，常用于兑换友情向道具与部分先祖物品。",
                    tag = "货币"
                ),
                TipCard(
                    key = "sky_terms_wing",
                    title = "斗篷 / 光翼",
                    desc = "斗篷代表飞行能力；收集“光之翼”可提升飞行能量上限。",
                    tag = "成长"
                ),
                TipCard(
                    key = "sky_terms_realm",
                    title = "地图 / 关",
                    desc = "晨岛、云野、雨林、霞谷、暮土、禁阁等为主要场景，逐关解锁。",
                    tag = "场景"
                )
            )
        )
    )
}
