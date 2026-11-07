package com.skypet.pet.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat

/**
 * 通用 UI 构建工具函数（纯函数式、无状态）。CONTRACT §4.7。
 * 这些是包级扩展/工具函数，供各 Screen/Panel/Bubble 复用。
 */

/** dp → px（四舍五入）。 */
fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).toInt()

/** dp → px（保留浮点）。 */
fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

/** sp → px（浮点）。 */
fun Context.sp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

/** 构造圆角背景（可选描边）。 */
fun roundedBg(
    fillColor: Int,
    radiusPx: Float,
    strokeColor: Int? = null,
    strokeWidthPx: Int = 0
): GradientDrawable {
    val d = GradientDrawable()
    d.shape = GradientDrawable.RECTANGLE
    d.cornerRadius = radiusPx
    d.setColor(fillColor)
    if (strokeColor != null && strokeWidthPx > 0) {
        d.setStroke(strokeWidthPx, strokeColor)
    }
    return d
}

/** 构造一个基础 TextView。 */
fun Context.makeTextView(
    text: String,
    sizeSp: Float = UiDims.bodySp,
    color: Int = UiColors.deepBlueText,
    bold: Boolean = false
): TextView {
    val tv = TextView(this)
    tv.text = text
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    tv.setTextColor(color)
    if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    return tv
}

/** 构造一个纵向卡片容器（奶白/白底、圆角、1dp 描边、内距）。 */
fun Context.makeCardLayout(
    padDp: Float = UiDims.cardPadDp,
    radiusDp: Float = UiDims.cornerRadiusDp,
    bgColor: Int = UiColors.paperWhite
): LinearLayout {
    val card = LinearLayout(this)
    card.orientation = LinearLayout.VERTICAL
    val pad = dp(padDp)
    card.setPadding(pad, pad, pad, pad)
    card.background = roundedBg(bgColor, dpF(radiusDp), UiColors.lineBlue, dp(1f))
    return card
}

/** 一行：文字 + 右侧开关（SwitchCompat）。 */
fun Context.makeSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
): LinearLayout {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.minimumHeight = dp(UiDims.listRowMinHeightDp)

    val tv = makeTextView(label, UiDims.bodySp, UiColors.deepBlueText)
    val tvParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    row.addView(tv, tvParams)

    val sw = SwitchCompat(this)
    sw.isChecked = checked
    sw.setOnCheckedChangeListener { _, isChecked -> onCheckedChange(isChecked) }
    row.addView(sw, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    return row
}

/** 主按钮（裙装蓝底、奶白字、圆角、固定高度、48dp 最小触控）。 */
fun Context.makePrimaryButton(label: String, onClick: () -> Unit): TextView {
    val btn = TextView(this)
    btn.text = label
    btn.gravity = Gravity.CENTER
    btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, UiDims.buttonSp)
    btn.setTextColor(UiColors.onPrimary)
    btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    btn.background = roundedBg(UiColors.skirtBlue, dpF(UiDims.smallCornerDp))
    btn.isClickable = true
    btn.isFocusable = true
    btn.minimumHeight = dp(UiDims.touchTargetDp)
    btn.setOnClickListener { onClick() }
    btn.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(UiDims.primaryButtonHeightDp)
    )
    return btn
}

/** 次级/描边小按钮。 */
fun Context.makeOutlineButton(label: String, onClick: () -> Unit): TextView {
    val btn = TextView(this)
    btn.text = label
    btn.gravity = Gravity.CENTER
    btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, UiDims.bodySp)
    btn.setTextColor(UiColors.skirtBlue)
    btn.background = roundedBg(UiColors.creamWhite, dpF(UiDims.smallCornerDp), UiColors.skirtBlue, dp(1f))
    btn.isClickable = true
    btn.isFocusable = true
    btn.minimumHeight = dp(UiDims.touchTargetDp)
    val padH = dp(16f)
    btn.setPadding(padH, dp(10f), padH, dp(10f))
    btn.setOnClickListener { onClick() }
    return btn
}

/** 垂直间距用的透明 View。 */
fun Context.makeSpacer(heightDp: Float): View {
    val v = View(this)
    v.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(heightDp)
    )
    return v
}
