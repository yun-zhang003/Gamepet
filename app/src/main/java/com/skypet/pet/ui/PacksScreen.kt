package com.skypet.pet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.skypet.pet.core.Prefs
import com.skypet.pet.core.plugin.GamePlugin
import com.skypet.pet.core.plugin.PluginRegistry

/**
 * 「陪伴包」页：插件选择列表 + 选中插件的每日勾选清单 + 提示卡片。
 * 切换插件只改内容与配色（look），不改形象文件本身。CONTRACT §4.7。
 * 所有勾选经 Prefs.isChecked/setChecked（跨天失效由 Prefs 保证）。
 */
class PacksScreen(
    private val ctx: Context,
    private val prefs: Prefs,
    private val onPluginChanged: (GamePlugin) -> Unit
) : FrameLayout(ctx) {

    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx)

    init {
        content.orientation = LinearLayout.VERTICAL
        val pad = ctx.dp(UiDims.pagePadDp)
        content.setPadding(pad, pad, pad, pad)
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        build()
    }

    fun refresh() { build() }

    private fun build() {
        content.removeAllViews()
        content.addView(ctx.makeTextView("给日常，换个风景", UiDims.titleSp, UiColors.deepBlueText, bold = true))
        content.addView(ctx.makeSpacer(16f))

        val current = PluginRegistry.byId(prefs.pluginId)

        // Pack selection cards
        for (plugin in PluginRegistry.plugins) {
            content.addView(packCard(plugin, plugin.id == current.id))
            content.addView(ctx.makeSpacer(UiDims.rowGapDp))
        }

        content.addView(ctx.makeSpacer(8f))

        // Selected pack sections
        val sectionHeader = ctx.makeTextView("今日清单 · ${current.name}", UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true)
        content.addView(sectionHeader)
        content.addView(ctx.makeSpacer(8f))

        if (current.sections.isEmpty()) {
            content.addView(ctx.makeTextView("此陪伴包暂无清单。", UiDims.bodySp, UiColors.mutedInk))
        } else {
            for (section in current.sections) {
                content.addView(sectionCard(current, section.id, section.title, section.checklist, section.cards.map { it }))
                content.addView(ctx.makeSpacer(UiDims.rowGapDp))
            }
        }
    }

    private fun packCard(plugin: GamePlugin, isCurrent: Boolean): View {
        val card = ctx.makeCardLayout()
        if (isCurrent) {
            // DressBlue border + text state for the selected pack.
            card.background = roundedBg(UiColors.paperWhite, ctx.dpF(UiDims.cornerRadiusDp), UiColors.skirtBlue, ctx.dp(2f))
        }
        // Original simplified landscape cover (112dp, hand-drawn via Canvas; no screenshots/official assets).
        val cover = CoverView(ctx, plugin)
        card.addView(cover, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(112f)
        ))
        card.addView(ctx.makeSpacer(8f))
        card.addView(ctx.makeTextView(plugin.name, UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true))
        card.addView(ctx.makeTextView(plugin.tagline, UiDims.noteSp, UiColors.mutedInk))

        val tagRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tag = ctx.makeTextView(if (plugin.gamePackages.isEmpty()) "通用" else "游戏联动", UiDims.noteSp, UiColors.skirtBlue)
        tag.background = roundedBg(UiColors.mistBlue, ctx.dpF(8f))
        val tp = ctx.dp(8f)
        tag.setPadding(tp, ctx.dp(2f), tp, ctx.dp(2f))
        tagRow.addView(tag)
        card.addView(ctx.makeSpacer(8f))
        card.addView(tagRow)

        card.addView(ctx.makeSpacer(8f))
        if (isCurrent) {
            val stateText = ctx.makeTextView("正在陪伴", UiDims.bodySp, UiColors.skirtBlue, bold = true)
            card.addView(stateText)
        } else {
            val useBtn = ctx.makeOutlineButton("使用此包") {
                prefs.pluginId = plugin.id
                onPluginChanged(plugin)
                build()
            }
            card.addView(useBtn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ctx.dp(UiDims.touchTargetDp)
            ))
        }
        return card
    }

    private fun sectionCard(
        plugin: GamePlugin,
        sectionId: String,
        title: String,
        checklist: List<String>,
        cards: List<com.skypet.pet.core.plugin.TipCard>
    ): View {
        val card = ctx.makeCardLayout()
        card.addView(ctx.makeTextView(title, UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true))
        card.addView(ctx.makeSpacer(8f))

        checklist.forEachIndexed { index, item ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ctx.dp(UiDims.listRowMinHeightDp)
            }
            val check = CheckBox(ctx)
            check.isChecked = prefs.isChecked(plugin.id, sectionId, index)
            check.contentDescription = item
            check.buttonTintList = android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(UiColors.skirtBlue, UiColors.mutedInk)
            )
            check.setOnCheckedChangeListener { _, isChecked ->
                prefs.setChecked(plugin.id, sectionId, index, isChecked)
            }
            row.addView(check, LinearLayout.LayoutParams(
                ctx.dp(UiDims.touchTargetDp),
                ctx.dp(UiDims.touchTargetDp)
            ))
            row.addView(
                ctx.makeTextView(item, UiDims.bodySp, UiColors.deepBlueText),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            card.addView(row)
        }

        for (tip in cards) {
            card.addView(ctx.makeSpacer(8f))
            val tipCard = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val p = ctx.dp(12f)
                setPadding(p, p, p, p)
                background = roundedBg(UiColors.creamWhite, ctx.dpF(UiDims.smallCornerDp), UiColors.lineBlue, ctx.dp(1f))
            }
            tipCard.addView(ctx.makeTextView(tip.title, UiDims.bodySp, UiColors.deepBlueText, bold = true))
            tipCard.addView(ctx.makeTextView(tip.desc, UiDims.noteSp, UiColors.mutedInk))
            tipCard.addView(ctx.makeTextView("· ${tip.tag}", UiDims.noteSp, UiColors.skirtBlue))
            card.addView(tipCard)
        }
        return card
    }

    /** 原创简化风景封面（海湾/云丘/星夜），纯 Canvas 绘制，禁止游戏截图/官方素材。 */
    private inner class CoverView(
        context: Context,
        private val plugin: GamePlugin
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val r = ctx.dpF(UiDims.cornerRadiusDp)

            // Sky gradient-ish: use look colors for a themed sea/sky cover.
            paint.color = plugin.look.glow
            canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, paint)

            // Sea band
            paint.color = UiColors.seaSaltBlue
            canvas.drawRect(0f, h * 0.62f, w, h, paint)

            // Hills / clouds
            paint.color = plugin.look.body
            canvas.drawOval(RectF(w * 0.08f, h * 0.42f, w * 0.55f, h * 0.82f), paint)
            paint.color = plugin.look.accent
            canvas.drawOval(RectF(w * 0.45f, h * 0.50f, w * 0.92f, h * 0.86f), paint)

            // Star accent
            paint.color = UiColors.creamWhite
            canvas.drawCircle(w * 0.80f, h * 0.22f, ctx.dpF(4f), paint)
            canvas.drawCircle(w * 0.68f, h * 0.14f, ctx.dpF(2f), paint)
        }
    }
}
