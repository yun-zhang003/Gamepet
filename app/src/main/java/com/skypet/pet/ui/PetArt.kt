package com.skypet.pet.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.core.content.ContextCompat
import com.skypet.pet.core.plugin.PetLook
import kotlin.random.Random

/**
 * 统一绘制入口。
 *
 * 图片优先级（README §7 / CONTRACT §4.4，融合 imageName）：
 *  1) 用户导入图（由调用方经 PetImageStore.loadPetBitmap() 取到的最优先位图，含内置 pet_whale.png 兜底）
 *  2) plugin.look.imageName 指向的 drawable（getIdentifier 动态查；无效/null 跳过）
 *  3) 矢量鲸鱼娘（WhaleGirlArt 兜底）
 *
 * 图片一律按比例缩放（fitCenter），不拉伸、不裁剪，透明背景（不设 View 背景）。
 * 矢量层带 UI-SPEC 动画：呼吸浮动（周期 3.6s，scaleY 0.98–1.02，浮动约 2dp）、眨眼（4–7s，闭眼 120ms）、
 * 点击笑（约 1s）。尊重“减少动画”（移除循环呼吸/浮动/眨眼），并不改变点击热区（热区由监听它的父窗口决定）。
 */
object PetArt {

    /** 静态绘制：有 bitmap 则 fitCenter，否则画矢量（默认表情/睁眼）。 */
    fun render(canvas: Canvas, size: Int, look: PetLook, bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) {
            drawBitmapFitCenter(canvas, size, bitmap)
        } else {
            WhaleGirlArt.draw(canvas, size, look)
        }
    }

    /**
     * 解析应显示的位图：先用户/内置位图，其次 look.imageName 指向的 drawable，最后 null（矢量）。
     * 供需要 context 解析 imageName 的调用方（buildView / HomeScreen / Settings）使用。
     */
    fun loadBitmap(context: Context, look: PetLook, baseBitmap: Bitmap?): Bitmap? {
        if (baseBitmap != null && !baseBitmap.isRecycled) return baseBitmap
        val name = look.imageName
        if (name.isNullOrBlank()) return null
        val resId = try {
            context.resources.getIdentifier(name, "drawable", context.packageName)
        } catch (_: Throwable) {
            0
        }
        if (resId == 0) return null
        return try {
            val d: Drawable? = ContextCompat.getDrawable(context, resId)
            if (d == null) null else drawableToBitmap(d)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 构建可放进悬浮窗的方形宠物 View（透明背景，按 sizeDp 渲染，带矢量动画）。
     * bitmap 为已解析的用户/内置位图（null 时还会尝试 look.imageName，再回退矢量）。
     */
    fun buildView(context: Context, sizeDp: Int, look: PetLook, bitmap: Bitmap?): View {
        val sizePx = context.dp(sizeDp.toFloat())
        val resolved = loadBitmap(context, look, bitmap)
        return PetView(context, sizePx, look, resolved)
    }

    /** 带动画的宠物视图。仅供悬浮窗与服务使用；点击笑通过 [smile] 触发。 */
    class PetView(
        private val ctx: Context,
        private val sizePx: Int,
        private val look: PetLook,
        private var bitmap: Bitmap?
    ) : View(ctx) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val src = Rect()
        private val dst = RectF()
        private val handler = Handler(Looper.getMainLooper())

        private var breathPhase = 0f        // 0..1 呼吸相位
        private var blinkFraction = 1f      // 1 睁 0 闭
        private var expression = WhaleGirlArt.Expression.NORMAL
        private var breathing: ValueAnimator? = null

        /** 减少动画时不做循环呼吸/浮动/眨眼（UI-SPEC §2）。 */
        private val reducedMotion: Boolean = try {
            Settings.Global.getFloat(
                ctx.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(sizePx, sizePx)
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (!reducedMotion) {
                startBreathing()
                scheduleBlink()
            }
        }

        override fun onDetachedFromWindow() {
            releaseAnimations()
            super.onDetachedFromWindow()
        }

        /** 生命周期释放：停止一切动画与回调（避免泄漏）。 */
        fun releaseAnimations() {
            try { breathing?.cancel() } catch (_: Throwable) {}
            breathing = null
            handler.removeCallbacksAndMessages(null)
        }

        /** Apply a decoded image on the main thread without rebuilding the overlay window. */
        fun setBitmap(value: Bitmap?) {
            bitmap = value
            invalidate()
        }

        private fun startBreathing() {
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1800L // 半周期；REVERSE 合计 3.6s
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener {
                    breathPhase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
            breathing = anim
        }

        private val blinkRunnable = Runnable {
            blinkFraction = 0f
            invalidate()
            handler.postDelayed({
                blinkFraction = 1f
                invalidate()
                scheduleBlink()
            }, 120L)
        }

        private fun scheduleBlink() {
            if (reducedMotion) return
            handler.postDelayed(blinkRunnable, Random.nextLong(4000L, 7000L))
        }

        /** 点击笑约 1s。减少动画时直接略过（保持静态）。 */
        fun smile() {
            if (reducedMotion) return
            expression = WhaleGirlArt.Expression.SMILE
            invalidate()
            handler.postDelayed({
                expression = WhaleGirlArt.Expression.NORMAL
                invalidate()
            }, 1000L)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()

            // 呼吸浮动：整体上下约 2dp + scaleY 0.98..1.02，围绕底部中心缩放（脚底贴近站点）。
            val breathDy = if (reducedMotion) 0f else ctx.dpF(2f) * breathPhase
            val scaleY = if (reducedMotion) 1f else 0.98f + 0.04f * breathPhase

            canvas.save()
            canvas.translate(0f, -breathDy)
            canvas.scale(1f, scaleY, w / 2f, h)
            val currentBitmap = bitmap
            if (currentBitmap != null && !currentBitmap.isRecycled) {
                // 图片：fitCenter 等比缩放（R9，避免拉伸）
                src.set(0, 0, currentBitmap.width, currentBitmap.height)
                val scale = minOf(w / currentBitmap.width, h / currentBitmap.height)
                val drawW = currentBitmap.width * scale
                val drawH = currentBitmap.height * scale
                val left = (w - drawW) / 2f
                val top = (h - drawH) / 2f
                dst.set(left, top, left + drawW, top + drawH)
                canvas.drawBitmap(currentBitmap, src, dst, paint)
            } else {
                WhaleGirlArt.drawWith(canvas, w.toInt(), look, blinkFraction, expression)
            }
            canvas.restore()
        }
    }

    private fun drawBitmapFitCenter(canvas: Canvas, size: Int, bitmap: Bitmap) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        val scale = minOf(size.toFloat() / bitmap.width, size.toFloat() / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        val left = (size - drawW) / 2f
        val top = (size - drawH) / 2f
        val dst = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(bitmap, src, dst, paint)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
        val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 256
        val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }
}
