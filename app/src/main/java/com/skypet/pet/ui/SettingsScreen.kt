package com.skypet.pet.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.skypet.pet.core.ForegroundApp
import com.skypet.pet.core.PetImageStore
import com.skypet.pet.core.Prefs

/**
 * 「设置」页：形象 / 陪伴方式 / 权限 / 关于。
 * 开关即时生效并持久化；尺寸滑杆 72–144（默认 108）；导入/清除宠物图片；权限引导按钮。
 * CONTRACT §4.7。权限行展示真实状态并打开系统授权页，绝不用本地开关伪造已授权。
 */
class SettingsScreen(
    private val ctx: Context,
    private val prefs: Prefs,
    private val imageStore: PetImageStore
) : FrameLayout(ctx) {

    private val scroll = ScrollView(ctx)
    private val content = LinearLayout(ctx)
    private val foregroundApp = ForegroundApp(ctx)
    private val imageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val imageHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var imageGeneration = 0

    /** 由宿主（MainActivity）注入：调起系统相册选择器。 */
    var onPickImage: (() -> Unit)? = null

    /** 设置变更回调（尺寸/开关），供宿主同步到悬浮服务。 */
    var onChanged: (() -> Unit)? = null

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

    fun releasePending() {
        imageGeneration++
        imageExecutor.shutdownNow()
    }

    /** 导入完成后由宿回调，再刷新页面。 */
    fun onImageImported() { build() }

    private fun build() {
        content.removeAllViews()
        content.addView(ctx.makeTextView("设置", UiDims.titleSp, UiColors.deepBlueText, bold = true))
        content.addView(ctx.makeSpacer(16f))

        // ---- 形象 ----
        content.addView(groupTitle("形象"))
        val imageCard = ctx.makeCardLayout()
        val preview = PetPreviewView(ctx)
        val generation = ++imageGeneration
        try {
            imageExecutor.execute {
                val base = imageStore.loadPetBitmap()
                val look = com.skypet.pet.core.plugin.PluginRegistry.byId(prefs.pluginId).look
                val bitmap = PetArt.loadBitmap(ctx, look, base)
                imageHandler.post {
                    if (generation == imageGeneration) preview.setBitmap(bitmap)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }
        imageCard.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(140f)
        ))
        imageCard.addView(ctx.makeSpacer(8f))
        val imported = prefs.importedImageName != null
        imageCard.addView(ctx.makeTextView(
            if (imported) "当前：自定义导入图片" else "当前：内置鲸鱼娘（含 pet_whale.png 兜底）",
            UiDims.noteSp, UiColors.mutedInk
        ))
        imageCard.addView(ctx.makeSpacer(8f))

        val btnRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val pickBtn = ctx.makeOutlineButton("从相册换形象") { onPickImage?.invoke() }
        btnRow.addView(pickBtn, LinearLayout.LayoutParams(0, ctx.dp(UiDims.touchTargetDp), 1f))
        val gap = View(ctx); gap.layoutParams = LinearLayout.LayoutParams(ctx.dp(8f), 1)
        btnRow.addView(gap)
        val resetBtn = ctx.makeOutlineButton("恢复默认形象") {
            imageStore.clearImported()
            onChanged?.invoke()
            build()
        }
        btnRow.addView(resetBtn, LinearLayout.LayoutParams(0, ctx.dp(UiDims.touchTargetDp), 1f))
        imageCard.addView(btnRow)
        content.addView(imageCard)
        content.addView(ctx.makeSpacer(UiDims.sectionGapDp))

        // ---- 陪伴方式 ----
        content.addView(groupTitle("陪伴方式"))
        val modeCard = ctx.makeCardLayout()
        modeCard.addView(ctx.makeSwitchRow("安静陪伴", prefs.quietCompanion) {
            prefs.quietCompanion = it; onChanged?.invoke()
        })
        modeCard.addView(ctx.makeSwitchRow("只在游戏里出现", prefs.onlyInGame) {
            prefs.onlyInGame = it; onChanged?.invoke()
        })
        modeCard.addView(ctx.makeSwitchRow("休息提醒（每 45 分钟）", prefs.restReminder) {
            prefs.restReminder = it; onChanged?.invoke()
        })
        content.addView(modeCard)
        content.addView(ctx.makeSpacer(UiDims.sectionGapDp))

        // ---- 权限 ----
        content.addView(groupTitle("权限"))
        val permCard = ctx.makeCardLayout()
        permCard.addView(permissionRow(
            "悬浮窗权限",
            if (Settings.canDrawOverlays(ctx)) "已授权" else "未授权",
            hasIt = Settings.canDrawOverlays(ctx)
        ) {
            openOverlaySettings()
        })
        val usage = foregroundApp.hasUsageAccess()
        permCard.addView(permissionRow("使用情况访问", if (usage) "已授权" else "未授权（将退化为始终显示）", usage) {
            foregroundApp.openUsageAccessSettings()
        })
        permCard.addView(permissionRow("通知权限", "用于常驻陪伴通知", true) {
            openNotificationSettings()
        })
        content.addView(permCard)
        content.addView(ctx.makeSpacer(UiDims.sectionGapDp))

        // ---- 关于 ----
        content.addView(groupTitle("关于"))
        val about = ctx.makeCardLayout()
        about.addView(ctx.makeTextView("鲸伴 SkyPet v0.2", UiDims.bodySp, UiColors.deepBlueText, bold = true))
        about.addView(ctx.makeTextView(
            "本地陪伴应用：不联网、不读取游戏画面、不含任何密钥。所有数据保存在本机。",
            UiDims.noteSp, UiColors.mutedInk
        ))
        content.addView(about)
    }

    private fun groupTitle(text: String): TextView {
        val tv = ctx.makeTextView(text, UiDims.sectionTitleSp, UiColors.skirtBlue, bold = true)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = ctx.dp(8f)
        tv.layoutParams = lp
        return tv
    }

    private fun permissionRow(label: String, value: String, hasIt: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = ctx.dp(UiDims.listRowMinHeightDp)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        row.addView(ctx.makeTextView(label, UiDims.bodySp, UiColors.deepBlueText, bold = true))
        row.addView(ctx.makeTextView(
            value,
            UiDims.noteSp,
            if (hasIt) UiColors.skirtBlue else UiColors.mutedInk
        ))
        return row
    }

    private fun openOverlaySettings() {
        try {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) {
        }
    }

    private fun openNotificationSettings() {
        try {
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            i.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Throwable) {
        }
    }

    /** 形象预览：优先导入图 > pet_whale.png > 矢量（走 PetImageStore/PetArt）。 */
    private inner class PetPreviewView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private var bitmap: Bitmap? = null

        fun setBitmap(value: Bitmap?) {
            bitmap = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            bg.color = UiColors.lightPink
            canvas.drawRoundRect(RectF(0f, 0f, w, h), ctx.dpF(UiDims.smallCornerDp), ctx.dpF(UiDims.smallCornerDp), bg)

            val size = minOf(w, h).toInt()
            val left = (width - size) / 2f
            val top = (height - size) / 2f
            canvas.save()
            canvas.translate(left, top)
            PetArt.render(canvas, size, com.skypet.pet.core.plugin.PluginRegistry.byId(prefs.pluginId).look, bitmap)
            canvas.restore()
        }
    }
}
