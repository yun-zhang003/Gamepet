package com.skypet.pet.core

import android.content.Context
import android.content.SharedPreferences
import com.skypet.pet.core.plugin.PluginRegistry
import com.skypet.pet.ui.UiDims
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 所有持久化读写集中于此（单文件 SharedPreferences("skypet_prefs")）。
 * FROZEN key table & semantics per CONTRACT §4.3. Other classes only touch storage through Prefs.
 */
class Prefs(context: Context) {

    companion object {
        const val PREFS_NAME = "skypet_prefs"

        const val KEY_PLUGIN_ID = "plugin_id"
        const val KEY_CHECK_PREFIX = "check_"
        const val KEY_CHECK_DATE = "check_date"
        const val KEY_POS_X = "pos_x"
        const val KEY_POS_Y = "pos_y"
        const val KEY_SIZE_DP = "size_dp"
        const val KEY_QUIET = "quiet_companion"
        const val KEY_ONLY_IN_GAME = "only_in_game"
        const val KEY_REST_REMINDER = "rest_reminder"
        const val KEY_VOICE_LINES = "voice_lines"
        const val KEY_IMPORTED_IMAGE = "imported_image"
        const val KEY_STARTED = "started"

        /** Sentinel meaning "position not yet placed"; the Service computes & persists a default. */
        const val POS_UNSET = -1
    }

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    // ---- Simple typed accessors -------------------------------------------------------------

    var pluginId: String
        get() = sp.getString(KEY_PLUGIN_ID, null) ?: PluginRegistry.default().id
        set(value) = sp.edit().putString(KEY_PLUGIN_ID, value).apply()

    var posX: Int
        get() = sp.getInt(KEY_POS_X, POS_UNSET)
        set(value) = sp.edit().putInt(KEY_POS_X, value).apply()

    var posY: Int
        get() = sp.getInt(KEY_POS_Y, POS_UNSET)
        set(value) = sp.edit().putInt(KEY_POS_Y, value).apply()

    /** 桌宠边长 dp，写入恒 clamp 到 72..144，默认 108。 */
    var sizeDp: Int
        get() = sp.getInt(KEY_SIZE_DP, UiDims.PET_SIZE_DEFAULT_DP)
        set(value) = sp.edit()
            .putInt(KEY_SIZE_DP, value.coerceIn(UiDims.PET_SIZE_MIN_DP, UiDims.PET_SIZE_MAX_DP))
            .apply()

    var quietCompanion: Boolean
        get() = sp.getBoolean(KEY_QUIET, false)
        set(value) = sp.edit().putBoolean(KEY_QUIET, value).apply()

    var onlyInGame: Boolean
        get() = sp.getBoolean(KEY_ONLY_IN_GAME, true)
        set(value) = sp.edit().putBoolean(KEY_ONLY_IN_GAME, value).apply()

    var restReminder: Boolean
        get() = sp.getBoolean(KEY_REST_REMINDER, true)
        set(value) = sp.edit().putBoolean(KEY_REST_REMINDER, value).apply()

    var voiceLines: Boolean
        get() = sp.getBoolean(KEY_VOICE_LINES, true)
        set(value) = sp.edit().putBoolean(KEY_VOICE_LINES, value).apply()

    /** 导入图私有目录文件名；无则 null。 */
    var importedImageName: String?
        get() {
            val v = sp.getString(KEY_IMPORTED_IMAGE, null)
            return if (v.isNullOrEmpty()) null else v
        }
        set(value) = sp.edit().putString(KEY_IMPORTED_IMAGE, value).apply()

    var started: Boolean
        get() = sp.getBoolean(KEY_STARTED, false)
        set(value) = sp.edit().putBoolean(KEY_STARTED, value).apply()

    // ---- Daily checklist (cross-day reset, FROZEN semantics) --------------------------------

    private fun checkKey(pluginId: String, sectionId: String, index: Int): String =
        "$KEY_CHECK_PREFIX${pluginId}_${sectionId}_$index"

    /**
     * 跨天统一重置：读取 check_date，若 ≠ 今日 → 清空所有 check_* 项并写入今日日期。
     * Service/Activity 启动时调用；isChecked/setChecked 内部也会惰性调用以保证一致性。
     */
    fun resetTodayIfNewDay() {
        val today = today()
        if (sp.getString(KEY_CHECK_DATE, null) != today) {
            val editor = sp.edit()
            for (key in sp.all.keys) {
                if (key.startsWith(KEY_CHECK_PREFIX)) editor.remove(key)
            }
            editor.putString(KEY_CHECK_DATE, today)
            editor.apply()
        }
    }

    /**
     * 读取某勾选状态。跨天（check_date ≠ 今日）视为全部未勾，返回 false。
     */
    fun isChecked(pluginId: String, sectionId: String, index: Int): Boolean {
        resetTodayIfNewDay()
        return sp.getBoolean(checkKey(pluginId, sectionId, index), false)
    }

    /**
     * 写入某勾选状态。先确保 check_date == 今日（跨天则先重置）。
     */
    fun setChecked(pluginId: String, sectionId: String, index: Int, checked: Boolean) {
        resetTodayIfNewDay()
        sp.edit().putBoolean(checkKey(pluginId, sectionId, index), checked).apply()
    }

    /** 清空所有 check_* 项（不动 check_date）。 */
    fun clearAllChecks() {
        val editor = sp.edit()
        for (key in sp.all.keys) {
            if (key.startsWith(KEY_CHECK_PREFIX)) editor.remove(key)
        }
        editor.apply()
    }
}
