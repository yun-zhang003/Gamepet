package com.skypet.pet.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.PowerManager
import android.provider.Settings

/**
 * 用 UsageStatsManager 判定当前前台应用包名，供“只在游戏里出现”。
 * FROZEN by CONTRACT §4.5. 不申请权限本身（只声明 + 跳系统设置）。
 */
class ForegroundApp(private val context: Context) {

    private val resumedActivities = mutableSetOf<String>()
    private var lastEventTime = 0L
    private var lastForeground: String? = null

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 当前前台包名；无法判定返回 null。
     * 先试 queryEvents 的 ACTIVITY_RESUMED（API 29+，更准），再退回 queryUsageStats 的 lastTimeUsed 最大值。
     */
    fun currentPackage(): String? {
        // A stale RESUMED event must not keep a game visible after the display turns off.
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power?.isInteractive == false) {
            resumedActivities.clear()
            lastForeground = null
            lastEventTime = System.currentTimeMillis()
            return null
        }
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val begin = now - 10 * 60 * 1000L // last 10 minutes

        // Track the current resumed activities across polls; a pause/stop clears the matching activity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val events = usm.queryEvents(if (lastEventTime > 0 && lastEventTime >= begin) lastEventTime + 1 else begin, now)
                if (lastEventTime < begin) {
                    resumedActivities.clear()
                    lastForeground = null
                }
                val event = UsageEvents.Event()
                var sawEvent = false
                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    sawEvent = true
                    val component = "${event.packageName}/${event.className}"
                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            resumedActivities.add(component)
                            lastForeground = event.packageName
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                            resumedActivities.remove(component)
                            if (lastForeground == event.packageName &&
                                resumedActivities.none { it.startsWith("${event.packageName}/") }
                            ) lastForeground = null
                        }
                    }
                    lastEventTime = maxOf(lastEventTime, event.timeStamp)
                }
                if (sawEvent || lastEventTime > 0) return lastForeground
                // No recent activity event means the foreground package is unknown.
                return null
            } catch (_: Exception) {
                return null
            }
        }

        // Fallback: package with the greatest lastTimeUsed in the window.
        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, now)
                ?: return null
            var bestPkg: String? = null
            var bestTime = 0L
            for (s in stats) {
                if (s.lastTimeUsed > bestTime) {
                    bestTime = s.lastTimeUsed
                    bestPkg = s.packageName
                }
            }
            if (bestPkg != null) return bestPkg
        } catch (_: Throwable) {
            return null
        }
        return null
    }

    /**
     * 判定“是否应显示桌宠”（核心降级规则，冻结）：
     * - 无 UsageStats 授权 → true（降级“始终显示”）。
     * - currentPackage()==null → true（无法判定→不隐藏）。
     * - gamePackages 为空 → true（通用插件）。
     * - 否则 currentPackage() in gamePackages。
     */
    fun shouldShowFor(gamePackages: List<String>): Boolean {
        if (!hasUsageAccess()) return true
        val current = currentPackage() ?: return true
        if (gamePackages.isEmpty()) return true
        return current in gamePackages
    }

    /** 跳转 Usage Access 设置页。 */
    fun openUsageAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Throwable) {
            // Some ROMs do not expose this page; nothing else we can do without special access.
        }
    }
}
