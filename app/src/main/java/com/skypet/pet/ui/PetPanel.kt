package com.skypet.pet.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.skypet.pet.core.plugin.GamePlugin

/**
 * 桌宠功能面板（点开桌宠后弹出）：“歇一会儿”“换陪伴包”“我歇好了”“×”收起等入口。
 *
 * 签名与 CONTRACT §4.7（§602，负责人已冻结）一致：
 *   class PetPanel(context, onRest, onRestDone, onOpenPacks, onClose)
 * 宽 220dp、圆角 20dp、内距 16dp（UI-SPEC §5）。只负责面板自身交互区域，不管理悬浮窗。
 *
 * 提醒状态单一来源（Q1/R8 根因修复）：
 *  - [bind] 的 `reminderText` 是 reminderView 文案的**唯一**来源（由服务按状态机算出）。
 *  - [showRestMode] / [hideRestMode] **只切换 restBtn/doneBtn 显隐**，绝不写 reminderView。
 *  - 因此调用顺序（先 bind 后切换 / 先切换后 bind）都不影响提醒文案正确性。
 */
class PetPanel(
    context: Context,
    private val onRest: () -> Unit,
    private val onRestDone: () -> Unit,
    private val onOpenPacks: () -> Unit,
    private val onClose: () -> Unit
) : LinearLayout(context) {

    private val titleView: TextView
    private val tasksView: TextView
    private val reminderView: TextView
    private val restBtn: TextView
    private val doneBtn: TextView

    init {
        orientation = VERTICAL
        val pad = context.dp(16f)
        setPadding(pad, pad, pad, pad)
        background = roundedBg(UiColors.creamWhite, context.dpF(20f), UiColors.lineBlue, context.dp(1f))
        minimumWidth = context.dp(220f)

        // Header row: title + 48dp close hot zone
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleView = context.makeTextView("小助手", UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true)
        header.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val close = TextView(context).apply {
            text = "×"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(UiColors.deepBlueText)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            contentDescription = "收起面板"
            minimumWidth = context.dp(UiDims.touchTargetDp)
            minimumHeight = context.dp(UiDims.touchTargetDp)
            setOnClickListener { onClose() }
        }
        header.addView(close, LinearLayout.LayoutParams(
            context.dp(UiDims.touchTargetDp),
            context.dp(UiDims.touchTargetDp)
        ))
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(context.makeSpacer(8f))

        tasksView = context.makeTextView("", UiDims.bodySp, UiColors.mutedInk)
        addView(tasksView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(context.makeSpacer(8f))
        reminderView = context.makeTextView("", UiDims.noteSp, UiColors.deepBlueText)
        addView(reminderView, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addView(context.makeSpacer(12f))

        restBtn = context.makePrimaryButton("歇一会儿") { onRest() }
        addView(restBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(UiDims.primaryButtonHeightDp)))

        addView(context.makeSpacer(8f))

        doneBtn = context.makePrimaryButton("我歇好了") { onRestDone() }
        doneBtn.visibility = View.GONE
        addView(doneBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(UiDims.primaryButtonHeightDp)))

        addView(context.makeSpacer(8f))

        val packs = context.makeOutlineButton("换陪伴包") { onOpenPacks() }
        addView(packs, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(UiDims.touchTargetDp)))
    }

    /**
     * 用当前插件与**真实**提醒状态刷新面板内容。
     * reminderText 由服务按状态机（panelReminderText）计算，是 reminderView 的唯一来源。
     */
    fun bind(plugin: GamePlugin, reminderText: String = "久坐提醒：每 45 分钟") {
        titleView.text = plugin.name
        val section = plugin.sections.firstOrNull()
        tasksView.text = if (section != null) {
            "今日任务：${section.checklist.size} 项——${section.title}"
        } else {
            "今天也一起加油呀。"
        }
        reminderView.text = reminderText
        contentDescription = "小助手面板，${plugin.name}"
    }

    /** 进入休息卡：仅切换按钮 + 任务区文案；**不写 reminderView**（避免覆盖 bind 的真实状态）。 */
    fun showRestMode() {
        tasksView.text = "站起来伸个懒腰，让眼睛看看远处吧。"
        restBtn.visibility = View.GONE
        doneBtn.visibility = View.VISIBLE
    }

    /** 退出休息卡：仅切换按钮显隐；**不写 reminderView**（避免覆盖 bind 的真实状态）。 */
    fun hideRestMode() {
        restBtn.visibility = View.VISIBLE
        doneBtn.visibility = View.GONE
    }

    /** 便捷：设置某子项可见性。 */
    fun setChildVisible(index: Int, visible: Boolean) {
        val child: View? = getChildAt(index)
        child?.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
