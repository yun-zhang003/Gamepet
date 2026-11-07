package com.skypet.pet.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.skypet.pet.core.plugin.Accessory
import com.skypet.pet.core.plugin.PetLook

/**
 * 原创简化鲸鱼娘矢量绘制（look 配色驱动，纯 Canvas）。
 *
 * 配色规则（UI-SPEC「配色规则（冻结 v2）」：
 *  - hair = look.body；belly = look.belly；dress = look.accent；
 *  - blush / 光晕(halo) = look.glow；accessory 形状 = look.accessory，配色 = look.accent。
 * 切换陪伴包只改配色（look）与点缀，不改变角色轮廓身份。
 * UiColors.skirtBlue 仅作默认兜底，不覆盖插件 look。
 *
 * FROZEN entry: fun draw(canvas, size, look)  (CONTRACT §4.7)。
 * 另提供带表情/眨眼参数的绘制重载（供动画层使用，默认参数保持兼容）。
 */
object WhaleGirlArt {

    /** 表情状态（供点击笑 / 眨眼动画）。 */
    enum class Expression { NORMAL, SMILE }

    /** 冻结入口：睁眼、常态表情。 */
    fun draw(canvas: Canvas, size: Int, look: PetLook) {
        drawWith(canvas, size, look, blinkFraction = 1f, expression = Expression.NORMAL)
    }

    /**
     * 带表情参数的绘制（供动画层使用；与冻结的 [draw] 分开命名，避免重载歧义）。
     * @param blinkFraction 1f=睁眼，0f=闭眼（闭眼呈细线）。
     * @param expression 表情；SMILE 时画笑嘴，NORMAL 时画平嘴。
     */
    fun drawWith(
        canvas: Canvas,
        size: Int,
        look: PetLook,
        blinkFraction: Float = 1f,
        expression: Expression = Expression.NORMAL
    ) {
        val w = size.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cx = w / 2f

        // ---- Halo / 光晕 (look.glow) ----
        paint.style = Paint.Style.FILL
        paint.color = look.glow
        paint.alpha = 45
        canvas.drawCircle(cx, w * 0.52f, w * 0.44f, paint)
        paint.alpha = 255

        // ---- Hair (look.body) ----
        paint.color = look.body
        canvas.drawCircle(cx, w * 0.40f, w * 0.30f, paint)
        val hairPath = Path().apply {
            moveTo(cx - w * 0.30f, w * 0.40f)
            quadTo(cx - w * 0.40f, w * 0.72f, cx - w * 0.20f, w * 0.80f)
            lineTo(cx - w * 0.10f, w * 0.55f)
            close()
        }
        canvas.drawPath(hairPath, paint)
        val hairPath2 = Path().apply {
            moveTo(cx + w * 0.30f, w * 0.40f)
            quadTo(cx + w * 0.40f, w * 0.72f, cx + w * 0.20f, w * 0.80f)
            lineTo(cx + w * 0.10f, w * 0.55f)
            close()
        }
        canvas.drawPath(hairPath2, paint)

        // ---- Dress (look.accent) : 配色由插件 look 驱动，直接用 accent，不再对 body 做暗化 ----
        paint.color = look.accent
        val dress = Path().apply {
            moveTo(cx - w * 0.08f, w * 0.52f)
            lineTo(cx + w * 0.08f, w * 0.52f)
            lineTo(cx + w * 0.26f, w * 0.92f)
            lineTo(cx - w * 0.26f, w * 0.92f)
            close()
        }
        canvas.drawPath(dress, paint)

        // ---- Face (look.belly) ----
        paint.color = look.belly
        canvas.drawCircle(cx, w * 0.42f, w * 0.20f, paint)

        // ---- Eyes (blink 由 blinkFraction 控制：椭圆高度缩放；极小时画线) ----
        paint.color = UiColors.deepBlueText
        val eyeRx = w * 0.028f
        val eyeRy = w * 0.028f * blinkFraction.coerceIn(0.06f, 1f)
        val leftEyeX = cx - w * 0.075f
        val rightEyeX = cx + w * 0.075f
        val eyeY = w * 0.41f
        if (blinkFraction <= 0.12f) {
            // 闭眼：一条细横线
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = w * 0.012f
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(leftEyeX - eyeRx, eyeY, leftEyeX + eyeRx, eyeY, paint)
            canvas.drawLine(rightEyeX - eyeRx, eyeY, rightEyeX + eyeRx, eyeY, paint)
        } else {
            paint.style = Paint.Style.FILL
            canvas.drawOval(RectF(leftEyeX - eyeRx, eyeY - eyeRy, leftEyeX + eyeRx, eyeY + eyeRy), paint)
            canvas.drawOval(RectF(rightEyeX - eyeRx, eyeY - eyeRy, rightEyeX + eyeRx, eyeY + eyeRy), paint)
        }

        // ---- Blush (look.glow) ----
        paint.style = Paint.Style.FILL
        paint.color = look.glow
        canvas.drawCircle(cx - w * 0.13f, w * 0.47f, w * 0.035f, paint)
        canvas.drawCircle(cx + w * 0.13f, w * 0.47f, w * 0.035f, paint)

        // ---- Mouth (笑 / 平) ----
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = w * 0.012f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = UiColors.deepBlueText
        val mouthY = w * 0.49f
        if (expression == Expression.SMILE) {
            val mouth = RectF(cx - w * 0.055f, mouthY - w * 0.03f, cx + w * 0.055f, mouthY + w * 0.02f)
            canvas.drawArc(mouth, 20f, 140f, false, paint)
        } else {
            canvas.drawLine(cx - w * 0.03f, mouthY, cx + w * 0.03f, mouthY, paint)
        }
        paint.style = Paint.Style.FILL

        // ---- Whale tail fin (look.glow) ----
        paint.color = look.glow
        val fin = Path().apply {
            moveTo(cx + w * 0.18f, w * 0.86f)
            lineTo(cx + w * 0.40f, w * 0.80f)
            lineTo(cx + w * 0.34f, w * 0.94f)
            close()
        }
        canvas.drawPath(fin, paint)

        // ---- Accessory (形状=look.accessory, 配色=look.accent) ----
        when (look.accessory) {
            is Accessory.Star -> drawStar(canvas, cx + w * 0.24f, w * 0.20f, w * 0.075f, paint, look.accent)
            is Accessory.Bubble -> {
                paint.color = look.accent
                canvas.drawCircle(cx + w * 0.26f, w * 0.18f, w * 0.05f, paint)
            }
            is Accessory.Note -> {
                paint.color = look.accent
                canvas.drawRoundRect(
                    RectF(cx + w * 0.22f, w * 0.14f, cx + w * 0.26f, w * 0.30f),
                    w * 0.02f, w * 0.02f, paint
                )
            }
            else -> { /* none */ }
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint, color: Int) {
        val path = Path()
        val spikes = 5
        var rot = -Math.PI.toFloat() / 2f
        val step = Math.PI.toFloat() / spikes
        path.moveTo(cx + Math.cos(rot.toDouble()).toFloat() * r, cy + Math.sin(rot.toDouble()).toFloat() * r)
        for (i in 0 until spikes) {
            rot += step
            path.lineTo(cx + Math.cos(rot.toDouble()).toFloat() * r, cy + Math.sin(rot.toDouble()).toFloat() * r)
            rot += step
            val inner = r * 0.45f
            path.lineTo(cx + Math.cos(rot.toDouble()).toFloat() * inner, cy + Math.sin(rot.toDouble()).toFloat() * inner)
        }
        path.close()
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
    }
}
