package com.skypet.pet.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/**
 * 气泡视图：奶白底（CreamWhite）、深蓝字（InkBlue）、圆角、留白、可带小尾巴。
 * FROZEN by CONTRACT §4.7 / UI-SPEC §5（14sp，最大宽 200dp，内距 12dp，圆角 16dp，约 3s 消失由 Service 控制）。
 */
class SpeechBubble(context: Context) : TextView(context) {

    init {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, UiDims.bubbleSp)
        setTextColor(UiColors.deepBlueText)
        typeface = Typeface.DEFAULT
        gravity = Gravity.CENTER_VERTICAL
        maxWidth = context.dp(200f)
        val padH = context.dp(UiDims.bubblePadDp)
        val padV = context.dp(UiDims.bubblePadDp - 4f)
        setPadding(padH, padV, padH, padV)
        // 奶白底、圆角、1dp 海盐蓝细描边（尾巴以阴影风格近似，不额外占位）。
        background = roundedBg(
            UiColors.creamWhite,
            context.dpF(UiDims.bubbleCornerDp),
            UiColors.seaSaltBlue,
            context.dp(1f)
        )
        elevation = context.dpF(4f)
        visibility = GONE
    }

    /** 更新气泡文字（空串视为隐藏）。 */
    fun show(text: String) {
        if (text.isBlank()) {
            visibility = GONE
            return
        }
        this.text = text
        visibility = VISIBLE
    }
}
