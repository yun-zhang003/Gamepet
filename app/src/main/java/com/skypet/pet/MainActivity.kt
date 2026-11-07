package com.skypet.pet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.skypet.pet.core.PetImageStore
import com.skypet.pet.core.Prefs
import com.skypet.pet.core.plugin.GamePlugin
import com.skypet.pet.core.plugin.PluginRegistry
import com.skypet.pet.overlay.PetOverlayService
import com.skypet.pet.ui.HomeScreen
import com.skypet.pet.ui.Icons
import com.skypet.pet.ui.PacksScreen
import com.skypet.pet.ui.SettingsScreen
import com.skypet.pet.ui.UiColors
import com.skypet.pet.ui.UiDims
import com.skypet.pet.ui.dp
import com.skypet.pet.ui.dpF
import com.skypet.pet.ui.roundedBg

/**
 * 唯一 Activity（launcher），承载三页内嵌导航：陪伴 / 陪伴包 / 设置。
 * 负责权限申请/引导、首页“开始陪伴”启动 Service、相册导入。
 * CONTRACT §1.1：不做悬浮逻辑、不持有 WindowManager。
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
        const val TAB_HOME = 0
        const val TAB_PACKS = 1
        const val TAB_SETTINGS = 2
        /** Q5：启动后核对服务 attach 真实状态的延迟。 */
        private const val START_VERIFY_DELAY_MS = 900L
    }

    private lateinit var prefs: Prefs
    private lateinit var imageStore: PetImageStore
    private val importExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private lateinit var contentHost: FrameLayout
    private lateinit var navItems: List<LinearLayout>
    private lateinit var navIcons: List<ImageView>
    private lateinit var navLabels: List<TextView>

    private lateinit var homeScreen: HomeScreen
    private lateinit var packsScreen: PacksScreen
    private lateinit var settingsScreen: SettingsScreen

    private var currentTab = TAB_HOME

    private val uiHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Q5：观察 prefs.started 变化并刷新 UI（不仅依赖 onResume）。
     * 服务在 attach 成功/失败/停止时写 started，这里监听到即刷新首页按钮。
     * 直接监听同一 SharedPreferences 文件（Prefs.PREFS_NAME），无需改动 core/Prefs.kt。
     */
    private val appSharedPrefs by lazy {
        getSharedPreferences(Prefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    private val startedObserver =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefs.KEY_STARTED) uiHandler.post { refreshCurrentScreen() }
        }

    /** S1：监听注册/注销为成对 onResume/onPause，用布尔防重（幂等）。 */
    private var startedObserverRegistered = false

    private fun registerStartedObserver() {
        if (!startedObserverRegistered) {
            appSharedPrefs.registerOnSharedPreferenceChangeListener(startedObserver)
            startedObserverRegistered = true
        }
    }

    private fun unregisterStartedObserver() {
        if (startedObserverRegistered) {
            appSharedPrefs.unregisterOnSharedPreferenceChangeListener(startedObserver)
            startedObserverRegistered = false
        }
    }

    private val notifPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* not blocking */ }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) handlePickedImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        imageStore = PetImageStore(this)
        prefs.resetTodayIfNewDay()

        buildRoot()
        val requestedTab = intent?.getIntExtra(EXTRA_OPEN_TAB, TAB_HOME) ?: TAB_HOME
        selectTab(savedInstanceState?.getInt("current_tab") ?: requestedTab)

        // S1：监听在 onResume 注册、onPause 注销（成对、幂等）；onCreate 不再注册，避免重复/不配对。
        maybeRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        prefs.resetTodayIfNewDay()
        // Re-check permission-dependent state (e.g. after returning from overlay settings).
        refreshCurrentScreen()
        // S1：成对注册（首次进入与每次回到前台都确保已注册，布尔防重）。
        registerStartedObserver()
    }

    override fun onPause() {
        // S1：与 onResume 成对注销（离开前台即注销，切回时 onResume 再注册）。
        unregisterStartedObserver()
        super.onPause()
    }

    override fun onDestroy() {
        // 兜底注销 + 清理延时回调，避免泄漏。
        unregisterStartedObserver()
        uiHandler.removeCallbacksAndMessages(null)
        if (::homeScreen.isInitialized) homeScreen.releasePending()
        if (::settingsScreen.isInitialized) settingsScreen.releasePending()
        importExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("current_tab", currentTab)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectTab(intent.getIntExtra(EXTRA_OPEN_TAB, currentTab))
    }

    // ---- Root layout: content host + bottom nav ---------------------------------------------

    private fun buildRoot() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(UiColors.creamWhite)
        }

        contentHost = FrameLayout(this)
        root.addView(contentHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(UiColors.paperWhite)
            gravity = Gravity.CENTER_VERTICAL
        }
        val navHeightPx = dp(UiDims.navHeightDp)
        root.addView(nav, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, navHeightPx
        ))

        // Build the three nav items.
        val items = mutableListOf<LinearLayout>()
        val icons = mutableListOf<ImageView>()
        val labels = mutableListOf<TextView>()
        val labelsText = listOf(
            getString(R.string.nav_home),
            getString(R.string.nav_packs),
            getString(R.string.nav_settings)
        )
        for (i in 0..2) {
            val (item, icon, label) = buildNavItem(i, labelsText[i])
            items.add(item); icons.add(icon); labels.add(label)
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        navItems = items; navIcons = icons; navLabels = labels

        setContentView(root)

        // Respect system bars so the bottom nav clears the gesture area (UI-SPEC §2 / §4).
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        // Instantiate screens once.
        homeScreen = HomeScreen(this, { currentPlugin() }, prefs).apply {
            setOnToggle { running -> toggleCompanion(running) }
            setOnRequestOverlayPermission { requestOverlayPermission() }
            setOnOpenPacks { selectTab(TAB_PACKS) }
            // R1: 尺寸落定/换包配色变化 -> 重配服务宠物窗（服务 ACTION_START 依最新 sizeDp/look 重建）。
            setOnSizeChanged { refreshServiceIfRunning() }
            setOnThemeChanged { refreshServiceIfRunning() }
        }
        packsScreen = PacksScreen(this, prefs, { plugin: GamePlugin ->
            onPluginChanged(plugin)
        })
        settingsScreen = SettingsScreen(this, prefs, imageStore).apply {
            onPickImage = { pickImageLauncher.launch("image/*") }
            onChanged = {
                homeScreen.refresh()
                refreshServiceIfRunning()
            }
        }
    }

    private fun buildNavItem(index: Int, labelText: String): Triple<LinearLayout, ImageView, TextView> {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = labelText
            setPadding(0, dp(8f), 0, dp(8f))
            setOnClickListener { selectTab(index) }
        }

        val iconWrap = FrameLayout(this)
        val iv = ImageView(this)
        iv.layoutParams = FrameLayout.LayoutParams(dp(24f), dp(24f), Gravity.CENTER)
        val iconPad = dp(6f)
        iconWrap.setPadding(iconPad, iconPad, iconPad, iconPad)
        iconWrap.addView(iv)
        item.addView(iconWrap, LinearLayout.LayoutParams(dp(48f), dp(48f)))

        val label = TextView(this).apply {
            text = labelText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, UiDims.noteSp)
            gravity = Gravity.CENTER
            setTextColor(UiColors.mutedInk)
        }
        item.addView(label)

        // Keep a reference to iconWrap for selected capsule background.
        item.tag = iconWrap
        return Triple(item, iv, label)
    }

    // ---- Tab switching ----------------------------------------------------------------------

    private fun selectTab(tab: Int) {
        currentTab = tab
        contentHost.removeAllViews()
        val screen: View = when (tab) {
            TAB_PACKS -> packsScreen
            TAB_SETTINGS -> settingsScreen
            else -> homeScreen
        }
        // refresh before showing so it reflects latest prefs
        when (tab) {
            TAB_PACKS -> packsScreen.refresh()
            TAB_SETTINGS -> settingsScreen.refresh()
            else -> homeScreen.refresh()
        }
        contentHost.addView(screen, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        updateNavState()
    }

    private fun updateNavState() {
        for (i in 0..2) {
            val selected = i == currentTab
            val icon: android.graphics.drawable.Drawable = when (i) {
                0 -> Icons.home(selected)
                1 -> Icons.packs(selected)
                else -> Icons.settings(selected)
            }
            navIcons[i].setImageDrawable(icon)
            navLabels[i].setTextColor(if (selected) UiColors.skirtBlue else UiColors.mutedInk)
            // Selected capsule background (light mist blue) on the icon wrap.
            val wrap = navItems[i].tag as? View
            wrap?.background = if (selected) {
                roundedBg(UiColors.mistBlue, dpF(16f))
            } else {
                null
            }
        }
    }

    private fun refreshCurrentScreen() {
        if (!::homeScreen.isInitialized) return
        when (currentTab) {
            TAB_PACKS -> packsScreen.refresh()
            TAB_SETTINGS -> settingsScreen.refresh()
            else -> homeScreen.refresh()
        }
    }

    // ---- Companion start/stop ---------------------------------------------------------------

    private fun currentPlugin(): GamePlugin = PluginRegistry.byId(prefs.pluginId)

    private fun toggleCompanion(running: Boolean) {
        if (running) {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return
            }
            // Q5：不乐观写入 started。started 由 Service 在 attach 成功后置 true。
            // 先清一次，避免上次残留；随后请求启动并在稍后核对真实状态。
            prefs.started = false
            try {
                PetOverlayService.start(this)
            } catch (t: Throwable) {
                prefs.started = false
                Toast.makeText(this, "启动失败：${t.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                homeScreen.refresh()
                return
            }
            // 等首帧 attach（异步）后重读真实状态并刷新按钮。
            uiHandler.postDelayed({
                refreshCurrentScreen()
                if (!prefs.started) {
                    Toast.makeText(this, "启动失败，请检查悬浮窗权限", Toast.LENGTH_SHORT).show()
                }
            }, START_VERIFY_DELAY_MS)
        } else {
            prefs.started = false
            PetOverlayService.stop(this)
            homeScreen.refresh()
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "请授予悬浮窗权限后返回", Toast.LENGTH_SHORT).show()
        try {
            val i = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(i)
        } catch (_: Throwable) {
            Toast.makeText(this, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onPluginChanged(plugin: GamePlugin) {
        // Persist already handled inside PacksScreen; refresh dependent surfaces.
        homeScreen.refresh()
        settingsScreen.refresh()
        refreshServiceIfRunning()
    }

    private fun refreshServiceIfRunning() {
        if (prefs.started) {
            // Re-issue START so the service re-reads prefs (plugin/size/quiet). Harmless if already running.
            try { PetOverlayService.start(this) } catch (_: Throwable) {}
        }
    }

    // ---- Image import -----------------------------------------------------------------------

    private fun handlePickedImage(uri: Uri) {
        importExecutor.execute {
            val name = imageStore.importFromUri(uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (name == null) {
                    Toast.makeText(this, "图片加载失败，请选择不超过 12 MB 的图片", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "已更换形象", Toast.LENGTH_SHORT).show()
                    settingsScreen.onImageImported()
                    homeScreen.refresh()
                    refreshServiceIfRunning()
                }
            }
        }
    }

    // ---- Permissions ------------------------------------------------------------------------

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
