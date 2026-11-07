package com.skypet.pet.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.skypet.pet.MainActivity
import com.skypet.pet.R
import com.skypet.pet.core.ForegroundApp
import com.skypet.pet.core.PetImageStore
import com.skypet.pet.core.Prefs
import com.skypet.pet.core.plugin.GamePlugin
import com.skypet.pet.core.plugin.PluginRegistry
import com.skypet.pet.ui.PetArt
import com.skypet.pet.ui.PetPanel
import com.skypet.pet.ui.SpeechBubble
import kotlin.math.abs
import kotlin.random.Random

/**
 * 提醒/休息状态机的**纯函数**不变量（无副作用，便于 reviewer 静态核验，Q1/Q2 根因）。
 * 与服务里的字段解耦：输入即全部状态，输出即应否触发 / 应显示文案。
 */
internal object ReminderLogic {

    /** 计时门：关闭提醒 / 休息中 都不触发；否则 elapsed 达阈值即触发（与 reminderDue 显示标志无关，Q2）。 */
    fun shouldFireReminder(restReminder: Boolean, resting: Boolean, elapsedMs: Long, intervalMs: Long): Boolean =
        restReminder && !resting && elapsedMs >= intervalMs

    /** 面板提醒文案单一来源（Q1）：只按状态决定，不受按钮/调用顺序影响。 */
    fun reminderText(resting: Boolean, restReminder: Boolean, reminderDue: Boolean): String = when {
        resting -> "休息中，45 分钟周期已暂停"
        !restReminder -> "久坐提醒已关闭"
        reminderDue -> "已到 45 分钟，记得起身活动"
        else -> "久坐提醒：每 45 分钟"
    }
}

/**
 * 悬浮桌宠前台服务：三个悬浮窗 + 手势 + 气泡 + 计时 + 前台通知。
 * FROZEN skeleton/semantics per CONTRACT §4.6.
 *
 * 三个窗口：
 *  1) petView   —— PetArt（图片/矢量，带呼吸/眨眼/点击笑动画），可拖拽/点击
 *  2) bubbleView—— SpeechBubble，FLAG_NOT_TOUCHABLE，贴 pet 上方，顶部夹到 y=0
 *  3) panelView —— PetPanel，点宠物后弹出，可点击（含休息卡与「我歇好了」）
 *
 * 不读游戏画面、不联网、无密钥。位置只存 x/y。
 *
 * 本轮修复（t5 R1/R2/R3/R7/R8；t7 Q1–Q6 根因重构）：
 *  - 尺寸：ACTION_START 依据最新 Prefs.sizeDp 重建宠物窗并按当前屏夹取位置（即时 72–144）。
 *  - 休息：resting 状态暂停 45min 周期；面板休息卡「我歇好了」退出休息并重置计时。
 *  - started：attach 成功置 true；所有失败/停止路径统一置 false（Activity 观察 KEY_STARTED 刷新）。
 *  - 前台规则同时约束三窗与主动气泡；启动/配置刷新立即判定可见性，隐藏时不建可见窗口、不弹泡。
 *  - 提醒：ReminderLogic 纯函数单一来源；onReminderDue 每满 45min 推进 baseline（45/90/135 均触发、无堆积）。
 */
class PetOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.skypet.pet.action.START"
        const val ACTION_STOP = "com.skypet.pet.action.STOP"
        const val ACTION_REST = "com.skypet.pet.action.REST" // 歇一会儿
        const val NOTIF_ID = 1001
        const val CHANNEL_ID = "skypet_overlay"

        private const val TICK_MS = 15_000L          // 15s tick
        private const val REST_INTERVAL_MS = 45 * 60 * 1000L // 45min
        private const val BUBBLE_HIDE_MS = 3_000L    // bubble disappears in ~3s

        fun start(ctx: Context) {
            val intent = Intent(ctx, PetOverlayService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PetOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var imageStore: PetImageStore
    private lateinit var foregroundApp: ForegroundApp

    private val handler = Handler(Looper.getMainLooper())
    private val imageExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var imageGeneration = 0

    private var petView: View? = null
    private var petAnim: PetArt.PetView? = null
    private var bubbleView: SpeechBubble? = null
    private var panelView: PetPanel? = null

    private lateinit var petParams: WindowManager.LayoutParams
    private var petAttached = false
    private var bubbleAttached = false
    private var panelAttached = false
    private var panelOpen = false

    private var plugin: GamePlugin = PluginRegistry.default()

    // Gesture bookkeeping
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var downTime = 0L
    private var moved = false
    private val touchSlop by lazy { android.view.ViewConfiguration.get(this).scaledTouchSlop }

    // Timing (monotonic clock — date changes do not affect)
    private var restBaseline = SystemClock.elapsedRealtime()
    private var resting = false          // 休息中：暂停 45min 周期
    private var reminderDue = false       // 到期提醒状态（由面板渲染，避免 quiet 丢失）
    private var lastQuiet = false
    private var lastRestReminder = true

    private val ticker = object : Runnable {
        override fun run() {
            onTick()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        imageStore = PetImageStore(this)
        foregroundApp = ForegroundApp(this)
        plugin = PluginRegistry.byId(prefs.pluginId)
        prefs.resetTodayIfNewDay()
        // A process restart can skip onDestroy; a new instance has no attached pet yet.
        prefs.started = false
        createChannel()
        restBaseline = SystemClock.elapsedRealtime()
        lastQuiet = prefs.quietCompanion
        lastRestReminder = prefs.restReminder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must call startForeground within the FGS window. API 34+ requires the typed overload.
        startForegroundSafely()

        if (intent?.action == ACTION_STOP) {
            prefs.started = false
            stopSelf()
            return START_NOT_STICKY
        }
        // A notification action may recreate the Service after process death.
        if (intent?.action == ACTION_REST && !petAttached) {
            prefs.started = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REST) {
            enterRest()
            return START_STICKY
        }

        // (Re)load config from Prefs so settings/plugin/size changes take effect immediately:
        // rebuild the pet window with the current plugin look and size (R1). This clamps the
        // stored x/y to the new size within the current screen bounds.
        plugin = PluginRegistry.byId(prefs.pluginId)
        if (petAttached) detachAll()
        val generation = ++imageGeneration
        val look = plugin.look
        attachPet()
        if (!petAttached) return START_NOT_STICKY
        try {
            imageExecutor.execute {
                val bitmap = imageStore.loadPetBitmap()
                val resolved = PetArt.loadBitmap(this, look, bitmap)
                handler.post {
                    if (generation == imageGeneration && petAttached) petAnim?.setBitmap(resolved)
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { }

        // Decide visibility before the first window frame is drawn.
        applyVisibility(computeShouldShow())

        // start ticking (idempotent: remove then post)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MS)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        // R3: any stop path funnels here — clear started so the Activity/refresh logic stays consistent.
        prefs.started = false
        handler.removeCallbacksAndMessages(null)
        imageGeneration++
        imageExecutor.shutdownNow()
        petAnim?.releaseAnimations() // R5: release animation callbacks to avoid leaks
        detachAll()
        super.onDestroy()
    }

    // ---- Foreground notification ------------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val restPi = PendingIntent.getService(
            this, 1,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_REST),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("鲸伴正在桌面上陪你")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.notif_action_rest), restPi)
            .addAction(0, getString(R.string.notif_action_stop), stopPi)
            .build()
    }

    private fun startForegroundSafely() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            // Android 14+: foreground service type REQUIRED (specialUse).
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // ---- Window 1: pet ----------------------------------------------------------------------

    private fun screenW(): Int = resources.displayMetrics.widthPixels
    private fun screenH(): Int = resources.displayMetrics.heightPixels
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun attachPet() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            // Overlay permission missing — cannot show the pet. Mark not-started and stop gracefully.
            prefs.started = false
            stopSelf()
            return
        }
        val sizePx = dp(prefs.sizeDp.toFloat())
        val view = PetArt.PetView(this, sizePx, plugin.look, null)
        if (!computeShouldShow()) view.visibility = View.GONE
        petView = view
        petAnim = view as? PetArt.PetView

        val x: Int
        val y: Int
        if (prefs.posX == Prefs.POS_UNSET || prefs.posY == Prefs.POS_UNSET) {
            // Default: right side, upper-middle; avoid the left joystick in landscape.
            val margin = dp(24f)
            x = (screenW() - sizePx - margin).coerceAtLeast(0)
            y = (screenH() / 3).coerceIn(0, (screenH() - sizePx).coerceAtLeast(0))
            prefs.posX = x
            prefs.posY = y
        } else {
            x = prefs.posX.coerceIn(0, (screenW() - sizePx).coerceAtLeast(0))
            y = prefs.posY.coerceIn(0, (screenH() - sizePx).coerceAtLeast(0))
            prefs.posX = x
            prefs.posY = y
        }

        petParams = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        view.setOnTouchListener { _, event -> handlePetTouch(event) }

        try {
            wm.addView(view, petParams)
            petAttached = true
            // Q5: 以服务 attach 成功作为运行状态更新点（Activity 只是观察者）。
            prefs.started = true
        } catch (_: Throwable) {
            petAttached = false
            prefs.started = false
            stopSelf()
        }
    }

    private fun handlePetTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = petParams.x
                startY = petParams.y
                downTime = SystemClock.uptimeMillis()
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - downRawX).toInt()
                val dy = (event.rawY - downRawY).toInt()
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                val sizePx = dp(prefs.sizeDp.toFloat())
                petParams.x = (startX + dx).coerceIn(0, (screenW() - sizePx).coerceAtLeast(0))
                petParams.y = (startY + dy).coerceIn(0, (screenH() - sizePx).coerceAtLeast(0))
                petView?.let { v ->
                    try { wm.updateViewLayout(v, petParams) } catch (_: Throwable) {}
                }
            }
            MotionEvent.ACTION_UP -> {
                val duration = SystemClock.uptimeMillis() - downTime
                if (!moved && duration < 200) {
                    onClickPet()
                } else {
                    // Persist position (only x/y, no orientation).
                    prefs.posX = petParams.x
                    prefs.posY = petParams.y
                }
                repositionBubble()
            }
        }
        return true
    }

    private fun onClickPet() {
        // Click always responds (even in quiet mode): 切换面板 + 冒一句 + 笑一下（R5）。
        petAnim?.smile()
        if (panelOpen) {
            hidePanel()
        } else {
            showPanel()
        }
        speak(randomPetLine())
    }

    // ---- Window 2: bubble -------------------------------------------------------------------

    private fun attachBubbleIfNeeded() {
        if (bubbleAttached) return
        val bubble = SpeechBubble(this)
        bubbleView = bubble
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(bubble, params)
            bubbleAttached = true
        } catch (_: Throwable) {
            bubbleAttached = false
        }
    }

    /** 主动/交互冒泡。交互（点击）不受 quiet 限制；proactive 由调用方自行判定 quiet/可见。 */
    private fun speak(text: String) {
        if (text.isBlank()) return
        attachBubbleIfNeeded()
        val bubble = bubbleView ?: return
        bubble.show(text)
        bubble.visibility = View.VISIBLE
        try { wm.updateViewLayout(bubble, bubble.layoutParams as WindowManager.LayoutParams) } catch (_: Throwable) {}
        repositionBubble()
        handler.removeCallbacks(hideBubbleRunnable)
        handler.postDelayed(hideBubbleRunnable, BUBBLE_HIDE_MS)
    }

    private val hideBubbleRunnable = Runnable {
        bubbleView?.show("")
    }

    private fun repositionBubble() {
        if (!bubbleAttached) return
        val bubble = bubbleView ?: return
        // measure to know its height for the y calculation
        bubble.measure(
            View.MeasureSpec.makeMeasureSpec(screenW(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val bh = bubble.measuredHeight
        val bw = bubble.measuredWidth
        val params = bubble.layoutParams as WindowManager.LayoutParams
        params.x = petParams.x.coerceIn(0, (screenW() - bw).coerceAtLeast(0))
        // bubble sits above the pet; clamp top to y=0
        params.y = (petParams.y - bh - dp(8f)).coerceAtLeast(0)
        try { wm.updateViewLayout(bubble, params) } catch (_: Throwable) {}
    }

    // ---- Window 3: panel --------------------------------------------------------------------

    private fun showPanel() {
        attachPanelIfNeeded()
        if (!panelAttached) return
        val panel = panelView ?: return
        panel.bind(plugin, reminderText())
        if (resting) panel.showRestMode() else panel.hideRestMode()
        val params = panel.layoutParams as WindowManager.LayoutParams
        val sizePx = dp(prefs.sizeDp.toFloat())
        val panelWidth = dp(220f)
        // Prefer the pet's right side (8dp gap); flip left if it would overflow.
        val rightX = petParams.x + sizePx + dp(8f)
        params.x = if (rightX + panelWidth <= screenW()) {
            rightX
        } else {
            (petParams.x - panelWidth - dp(8f)).coerceAtLeast(0)
        }
        params.y = petParams.y.coerceIn(0, (screenH() - dp(220f)).coerceAtLeast(0))
        try { wm.updateViewLayout(panel, params) } catch (_: Throwable) {}
        panel.visibility = View.VISIBLE
        panelOpen = true
    }

    private fun attachPanelIfNeeded() {
        if (panelAttached) return
        val panel = PetPanel(
            this,
            onRest = { enterRest() },
            onRestDone = { completeRest() },
            onOpenPacks = { openAppToPacks() },
            onClose = { hidePanel() }
        )
        panelView = panel
        panel.visibility = View.GONE
        val params = WindowManager.LayoutParams(
            dp(220f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(panel, params)
            panelAttached = true
            panel.visibility = View.GONE
        } catch (_: Throwable) {
            panelAttached = false
            panelView = null
        }
    }

    private fun hidePanel() {
        panelOpen = false
        panelView?.visibility = View.GONE
    }

    /** 确保面板已创建并同步到期状态（quiet 到期时也能在用户打开面板时看到）。 */
    private fun refreshPanelState() {
        attachPanelIfNeeded()
        val panel = panelView ?: return
        panel.bind(plugin, reminderText())
        if (resting) panel.showRestMode() else panel.hideRestMode()
    }

    // ---- Rest / timing ----------------------------------------------------------------------

    private fun reminderText(): String =
        ReminderLogic.reminderText(resting, prefs.restReminder, reminderDue)

    private fun enterRest() {
        // 进入休息：暂停 45min 周期、清除到期显示状态、重开下一周期。
        resting = true
        reminderDue = false
        restBaseline = SystemClock.elapsedRealtime()
        // Q4：展示（showPanel / speak）必须以当前前台可见性为准；隐藏时只更新内部状态、不建可见窗口。
        if (isPetVisibleNow()) {
            showPanel()
            panelView?.showRestMode()
            if (!prefs.quietCompanion) {
                speak("站起来伸个懒腰，让眼睛看看远处吧。")
            }
        }
        // 隐藏时：不创建/不显示任何浮层；resting 状态已保存，返回可见后 showPanel 渲染休息卡。
    }

    private fun completeRest() {
        // 「我歇好了」：退出休息 + 重新完整计时（重新开下一周期）。
        resting = false
        reminderDue = false
        restBaseline = SystemClock.elapsedRealtime()
        panelView?.bind(plugin, reminderText())
        panelView?.hideRestMode()
    }

    private fun onTick() {
        // 1) 前台规则：同时决定三窗与主动气泡的可见性（R7）。
        applyVisibility(computeShouldShow())

        // 2) 设置变化检测：退出安静 / 重新开启提醒时重置 baseline，避免立即误触发（R2 补充）。
        if (prefs.quietCompanion != lastQuiet || prefs.restReminder != lastRestReminder) {
            restBaseline = SystemClock.elapsedRealtime()
            reminderDue = false
            lastQuiet = prefs.quietCompanion
            lastRestReminder = prefs.restReminder
            if (panelAttached) refreshPanelState()
        }

        // 3) 45min 周期计时（Q2 根因修复）：计时门**不**依赖 reminderDue（否则首个 45min 后永不再触发）。
        val elapsed = SystemClock.elapsedRealtime() - restBaseline
        if (ReminderLogic.shouldFireReminder(prefs.restReminder, resting, elapsed, REST_INTERVAL_MS)) {
            onReminderDue()
        }
    }

    /**
     * 到期提醒（Q2）：置 reminderDue 供显示，并**立即推进 baseline 重开下一周期**（45/90/135…每周期都触发，无堆积）。
     * 展示受可见性约束（Q4）：仅在宠物当前可见时更新面板/冒泡；隐藏时仅保存状态，返回可见后渲染。
     */
    private fun onReminderDue() {
        reminderDue = true
        restBaseline = SystemClock.elapsedRealtime() // 推进单调时钟 baseline（不补发堆积）
        if (isPetVisibleNow()) {
            refreshPanelState() // 仅可见时更新面板（不新建可见窗口）
            if (!prefs.quietCompanion) {
                speak("该歇一会儿啦。")
            }
        }
    }

    private fun computeShouldShow(): Boolean =
        if (!prefs.onlyInGame) true else foregroundApp.shouldShowFor(plugin.gamePackages)

    /** 当前是否真的可见（前台规则 + 宠物窗未隐藏）。 */
    private fun isPetVisibleNow(): Boolean = petView?.visibility == View.VISIBLE

    private fun applyVisibility(visible: Boolean) {
        val pet = petView ?: return
        if (visible) {
            pet.visibility = View.VISIBLE
            // 返回可见时，若面板开着则按当前真实状态重渲染（Q4：返回游戏再展示有效状态）。
            if (panelOpen) refreshPanelState()
        } else {
            // 隐藏：三窗都不可见/不可点，且清空气泡，避免残留可触摸/可见气泡（R7）。
            pet.visibility = View.GONE
            bubbleView?.show("")
            bubbleView?.visibility = View.GONE
            hidePanel()
        }
    }

    private fun randomPetLine(): String {
        val lines = plugin.idleLines
        val base = if (lines.isEmpty()) plugin.greeting() else lines[Random.nextInt(lines.size)]
        return base
    }

    private fun openAppToPacks() {
        try {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_PACKS)
            startActivity(i)
        } catch (_: Throwable) {
        }
        hidePanel()
    }

    private fun detachAll() {
        try { bubbleView?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { panelView?.let { wm.removeView(it) } } catch (_: Throwable) {}
        try { petView?.let { wm.removeView(it) } } catch (_: Throwable) {}
        petView = null; petAnim = null; bubbleView = null; panelView = null
        petAttached = false; bubbleAttached = false; panelAttached = false
        panelOpen = false
    }
}
