package com.skypet.pet.ui

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.graphics.PathParser

/**
 * 纯 Canvas/Path 绘制的矢量图标（不依赖任何图标字体或网络资源）。
 * 路径数据取自 UI-SPEC §4（24×24 viewBox，2dp 圆头圆角描边；选中实心 / 未选中描边）。
 */
object Icons {

    private const val VIEWBOX = 24f

    // 心形（陪伴）
    private const val HEART =
        "M12 21 C9 18 3 14 3 8 C3 3 9 2 12 7 C15 2 21 3 21 8 C21 14 15 18 12 21 Z"

    // 拼块（陪伴包）
    private const val PUZZLE =
        "M4 4 H9 C8 0 16 0 15 4 H20 V9 C24 8 24 16 20 15 V20 H15 C16 24 8 24 9 20 H4 V15 C0 16 0 8 4 9 Z"

    // 滑杆（设置）
    private const val SLIDER =
        "M4 3 H7 V7 H10 V11 H7 V21 H4 V11 H1 V7 H4 Z M16 3 H19 V13 H22 V17 H19 V21 H16 V17 H13 V13 H16 Z"

    fun home(selected: Boolean): Drawable = PathIconDrawable(HEART, selected)

    fun packs(selected: Boolean): Drawable = PathIconDrawable(PUZZLE, selected)

    fun settings(selected: Boolean): Drawable = PathIconDrawable(SLIDER, selected)

    /** 一个按 24×24 viewBox 解析、按 Drawable 边界等比缩放的手绘图标。 */
    private class PathIconDrawable(
        pathData: String,
        private val selected: Boolean
    ) : Drawable() {

        private val path: Path = PathParser.createPathFromPathData(pathData)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = if (selected) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = 2f
            color = if (selected) UiColors.skirtBlue else UiColors.deepBlueText
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return
            val scale = minOf(b.width(), b.height()) / VIEWBOX
            canvas.save()
            canvas.translate(b.left.toFloat(), b.top.toFloat())
            canvas.scale(scale, scale)
            paint.style = if (selected) Paint.Style.FILL else Paint.Style.STROKE
            paint.strokeWidth = 2f
            paint.color = if (selected) UiColors.skirtBlue else UiColors.deepBlueText
            canvas.drawPath(path, paint)
            canvas.restore()
        }

        // Paint alpha and colorFilter are honoured without extra state.
        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = VIEWBOX.toInt()

        override fun getIntrinsicHeight(): Int = VIEWBOX.toInt()
    }
}
