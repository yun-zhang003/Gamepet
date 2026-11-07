package com.skypet.pet.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.skypet.pet.core.ForegroundApp
import com.skypet.pet.core.PetImageStore
import com.skypet.pet.core.Prefs
import com.skypet.pet.core.plugin.GamePlugin

/**
 * 「陪伴」页。展示宠物预览（PetArt：导入图>imageName>矢量）、greeting、tagline、
 * 一键开始/停止陪伴、尺寸滑杆（72–144）、显示范围（含 UsageStats 降级说明）、当前陪伴包、今日任务摘要。
 *
 * R1：尺寸拖动即时更新舞台预览；松手时经 onSizeChanged 回调通知宿主重配服务宠物窗。
 * R10：舞台点缀使用 look.accent（由插件 look 驱动，换包即换配色）。
 */
class HomeScreen(
    private val ctx: Context,
    private val pluginProvider: () -> GamePlugin,
    private val prefs: Prefs
) : FrameLayout(ctx) {

    private companion object {
        /** Q6：尺寸调整去抖窗口（ms）。 */
        const val SIZE_DEBOUNCE_MS = 300L
    }

    private val imageStore = PetImageStore(ctx)
    private val imageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var imageGeneration = 0
    private var onToggle: (Boolean) -> Unit = {}
    private var onRequestOverlayPermission: () -> Unit = {}
    private var onOpenPacks: () -> Unit = {}
    private var onSizeChangedCb: (Int) -> Unit = {}
    private var onThemeChangedCb: () -> Unit = {}

    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx)
    private var stageView: StageView? = null
    private var valueText: TextView? = null

    // Q6：尺寸去抖 —— 拖动/键盘/TalkBack 连续调整时合并，避免每次重建服务窗。
    private val sizeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSizeDp: Int = 0
    private val sizeReconfigRunnable = Runnable { commitSizeReconfig() }

    private fun scheduleSizeReconfig(dp: Int) {
        pendingSizeDp = dp
        sizeHandler.removeCallbacks(sizeReconfigRunnable)
        sizeHandler.postDelayed(sizeReconfigRunnable, SIZE_DEBOUNCE_MS)
    }

    private fun flushSizeReconfig() {
        sizeHandler.removeCallbacks(sizeReconfigRunnable)
        commitSizeReconfig()
    }

    private fun commitSizeReconfig() {
        val dp = if (pendingSizeDp > 0) pendingSizeDp else prefs.sizeDp
        onSizeChangedCb(dp.coerceIn(UiDims.PET_SIZE_MIN_DP, UiDims.PET_SIZE_MAX_DP))
    }

    /** 释放去抖回调（由宿主在切换/销毁时调用，避免泄漏）。 */
    fun releasePending() {
        sizeHandler.removeCallbacks(sizeReconfigRunnable)
        imageGeneration++
        imageExecutor.shutdownNow()
    }

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

    fun setOnToggle(l: (Boolean) -> Unit) { onToggle = l }
    fun setOnRequestOverlayPermission(l: () -> Unit) { onRequestOverlayPermission = l }
    fun setOnOpenPacks(l: () -> Unit) { onOpenPacks = l }
    /** R1：尺寸落定（72–144）时回调宿主，重建服务宠物窗以即时生效。 */
    fun setOnSizeChanged(l: (Int) -> Unit) { onSizeChangedCb = l }
    /** R10：插件配色变化（换包）时，供宿主通知服务刷新外观。 */
    fun setOnThemeChanged(l: () -> Unit) { onThemeChangedCb = l }

    /** 插件/设置变化后重建内容。 */
    fun refresh() { build() }

    private fun resolveBitmap(plugin: GamePlugin) {
        val generation = ++imageGeneration
        try {
            imageExecutor.execute {
                val bitmap = PetArt.loadBitmap(ctx, plugin.look, imageStore.loadPetBitmap())
                sizeHandler.post {
                    if (generation == imageGeneration) stageView?.setBitmap(bitmap)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
    }

    private fun build() {
        val plugin = pluginProvider()
        resolveBitmap(plugin)
        content.removeAllViews()
        stageView = null
        valueText = null

        // Title
        val title = ctx.makeTextView("今天，也陪着你", UiDims.titleSp, UiColors.deepBlueText, bold = true)
        content.addView(title)
        content.addView(ctx.makeSpacer(16f))

        // Stage (light pink, 252dp, corner 28dp, horizon at ~76%)
        val stage = StageView(ctx, plugin, null)
        stageView = stage
        stage.contentDescription = "陪伴舞台：鲸鱼娘与招呼语「${plugin.greeting()}」"
        content.addView(stage, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(UiDims.stageHeightDp)
        ))
        content.addView(ctx.makeSpacer(UiDims.sectionGapDp))

        // Primary start/stop button
        val running = prefs.started
        val startBtn = ctx.makePrimaryButton(if (running) "暂停陪伴" else "开启陪伴") {
            if (!running && !canDrawOverlay()) {
                onRequestOverlayPermission()
            } else {
                onToggle(!running)
            }
        }
        content.addView(startBtn)
        content.addView(ctx.makeSpacer(16f))

        // Size slider 72–144
        addSizeSlider()
        content.addView(ctx.makeSpacer(16f))

        // Display scope
        addScopeRow(plugin)
        content.addView(ctx.makeSpacer(16f))

        // Current pack
        addPackRow(plugin)
        content.addView(ctx.makeSpacer(UiDims.sectionGapDp))

        // Daily tasks summary
        addDailySummary(plugin)
    }

    private fun canDrawOverlay(): Boolean =
        android.provider.Settings.canDrawOverlays(ctx)

    private fun addSizeSlider() {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val labelRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        labelRow.addView(
            ctx.makeTextView("桌宠大小", UiDims.bodySp, UiColors.deepBlueText, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        val value = ctx.makeTextView("${prefs.sizeDp} dp", UiDims.bodySp, UiColors.mutedInk)
        valueText = value
        labelRow.addView(value)
        row.addView(labelRow)

        val seek = SeekBar(ctx)
        seek.contentDescription = "桌宠大小滑杆，范围 ${UiDims.PET_SIZE_MIN_DP} 到 ${UiDims.PET_SIZE_MAX_DP} dp"
        seek.max = UiDims.PET_SIZE_MAX_DP - UiDims.PET_SIZE_MIN_DP
        seek.progress = (prefs.sizeDp - UiDims.PET_SIZE_MIN_DP)
            .coerceIn(0, seek.max)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val dp = (UiDims.PET_SIZE_MIN_DP + progress)
                    .coerceIn(UiDims.PET_SIZE_MIN_DP, UiDims.PET_SIZE_MAX_DP)
                value.text = "$dp dp"
                if (fromUser) {
                    // Q6：触摸/键盘/TalkBack 调整都走这里（fromUser=true），**立即持久化**并去抖通知宿主；
                    // 不依赖 onStopTrackingTouch（键盘/TalkBack 可能不触发 stop）。
                    prefs.sizeDp = dp // setter clamps 72..144 → Prefs 与 stage 预览一致
                    stageView?.setPreviewSizeDp(dp) // 舞台预览即时更新
                    scheduleSizeReconfig(dp)         // 去抖：避免拖动频繁重建服务窗
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                // 松手立即 flush 一次（确保最终值生效）。
                flushSizeReconfig()
            }
        })
        row.addView(seek, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ctx.dp(UiDims.touchTargetDp)
        ))
        content.addView(row)
    }

    private fun addScopeRow(plugin: GamePlugin) {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        val scope = if (prefs.onlyInGame) "只在游戏里出现" else "所有应用"
        row.addView(ctx.makeTextView("显示范围：$scope", UiDims.bodySp, UiColors.deepBlueText, bold = true))

        val hasUsage = ForegroundApp(ctx).hasUsageAccess()
        if (prefs.onlyInGame && !hasUsage) {
            row.addView(ctx.makeTextView(
                "无使用情况权限，已退化为始终显示",
                UiDims.noteSp, UiColors.mutedInk
            ))
        } else if (prefs.onlyInGame && plugin.gamePackages.isEmpty()) {
            row.addView(ctx.makeTextView(
                "当前陪伴包为通用包，所有应用均显示",
                UiDims.noteSp, UiColors.mutedInk
            ))
        }
        content.addView(row)
    }

    private fun addPackRow(plugin: GamePlugin) {
        val card = ctx.makeCardLayout()
        card.addView(ctx.makeTextView("当前陪伴包", UiDims.noteSp, UiColors.mutedInk))
        card.addView(ctx.makeTextView(plugin.name, UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true))
        card.addView(ctx.makeTextView(plugin.tagline, UiDims.noteSp, UiColors.mutedInk))
        card.addView(ctx.makeSpacer(8f))
        val switchBtn = ctx.makeOutlineButton("切换陪伴包") { onOpenPacks() }
        card.addView(switchBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ctx.dp(UiDims.touchTargetDp)
        ))
        content.addView(card)
    }

    private fun addDailySummary(plugin: GamePlugin) {
        val section = plugin.sections.firstOrNull()
        val card = ctx.makeCardLayout()
        card.addView(ctx.makeTextView("今日小目标", UiDims.sectionTitleSp, UiColors.deepBlueText, bold = true))
        if (section != null) {
            var checked = 0
            for (i in section.checklist.indices) {
                if (prefs.isChecked(plugin.id, section.id, i)) checked++
            }
            val summary = "${section.title}：已勾选 $checked / ${section.checklist.size}"
            card.contentDescription = "今日任务摘要，$summary"
            card.addView(ctx.makeTextView(summary, UiDims.bodySp, UiColors.mutedInk))
        } else {
            card.addView(ctx.makeTextView("今天也一起加油呀。", UiDims.bodySp, UiColors.mutedInk))
        }
        content.addView(card)
    }

    /** 舞台：浅粉底 + 海盐蓝地平线（76% 处）+ 站在线上的鲸鱼娘 + 头顶奶白招呼语 + look.accent 点缀。 */
    private inner class StageView(
        context: Context,
        private var plugin: GamePlugin,
        private var bitmap: android.graphics.Bitmap?
    ) : View(context) {
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = UiColors.deepBlueText
            textSize = ctx.sp(UiDims.bodySp)
        }
        private var previewSizeDp = prefs.sizeDp

        fun setBitmap(value: Bitmap?) {
            bitmap = value
            invalidate()
        }

        fun setPreviewSizeDp(dp: Int) {
            previewSizeDp = dp.coerceIn(UiDims.PET_SIZE_MIN_DP, UiDims.PET_SIZE_MAX_DP)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = ctx.dpF(UiDims.stageCornerDp)

            // pink stage
            bgPaint.color = UiColors.lightPink
            canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, bgPaint)

            // sea salt horizon at ~76%
            val horizonY = h * 0.76f
            linePaint.color = UiColors.seaSaltBlue
            linePaint.strokeWidth = ctx.dpF(2f)
            canvas.drawLine(ctx.dpF(12f), horizonY, w - ctx.dpF(12f), horizonY, linePaint)

            // pet stands on the horizon line (live preview size, R1)
            val petSize = ctx.dp(previewSizeDp.toFloat())
                .coerceAtMost((h * 0.78f).toInt())
            val left = (w - petSize) / 2f
            val top = horizonY - petSize
            canvas.save()
            canvas.translate(left, top)
            PetArt.render(canvas, petSize, plugin.look, bitmap)
            canvas.restore()

            // greeting bubble above head
            val greeting = plugin.greeting()
            val padding = ctx.dpF(UiDims.bubblePadDp)
            val maxTextW = w - ctx.dpF(32f)
            val textW = textPaint.measureText(greeting).coerceAtMost(maxTextW)
            val bh = textPaint.textSize + padding * 2
            val bw = textW + padding * 2
            val bx = (w - bw) / 2f
            val by = (top - bh - ctx.dpF(8f)).coerceAtLeast(ctx.dpF(8f))
            bubblePaint.color = UiColors.creamWhite
            canvas.drawRoundRect(RectF(bx, by, bx + bw, by + bh), ctx.dpF(16f), ctx.dpF(16f), bubblePaint)
            val ty = by + bh / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.save()
            canvas.clipRect(bx, by, bx + bw, by + bh)
            canvas.drawText(greeting, bx + padding, ty, textPaint)
            canvas.restore()

            // tiny star decorations — 配色由 look.accent 驱动（R10，换包即换色）
            bgPaint.color = plugin.look.accent
            canvas.drawCircle(w * 0.14f, h * 0.18f, ctx.dpF(2.5f), bgPaint)
            canvas.drawCircle(w * 0.86f, h * 0.28f, ctx.dpF(2f), bgPaint)
        }
    }
}
