package com.sdm.videoplayer.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdm.videoplayer.data.model.SortOrder
import com.sdm.videoplayer.data.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("sdm_vp_prefs")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

enum class AccentChoice(val key: String, val label: String) {
    PURPLE("purple", "بنفسجي"),
    BLUE("blue", "أزرق"),
    CYAN("cyan", "فيروزي"),
    MAGENTA("magenta", "وردي"),
    AMBER("amber", "كهرماني")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accent: AccentChoice = AccentChoice.PURPLE,
    val sortOrder: SortOrder = SortOrder.DATE_NEWEST,
    val viewMode: ViewMode = ViewMode.GRID,
    val defaultSpeed: Float = 1f,
    val seekStepMs: Long = 10_000L,
    val autoResume: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val doubleTapSeek: Boolean = true,
    val lockTouchOnStart: Boolean = false,
    val pipOnLeave: Boolean = true,
    val backgroundPlayback: Boolean = true,
    val subtitleSize: Float = 16f,
    val subtitleColor: Int = 0xFFFFFFFF.toInt(),
    val customFolders: List<String> = emptyList(),
    val folderNames: Map<String, String> = emptyMap()
)

/** تفضيلات التطبيق محفوظة محليًا عبر DataStore. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val sort = stringPreferencesKey("sort")
        val view = stringPreferencesKey("view")
        val speed = floatPreferencesKey("speed")
        val seekStep = floatPreferencesKey("seek_step")
        val autoResume = booleanPreferencesKey("auto_resume")
        val gestureBrightness = booleanPreferencesKey("gesture_brightness")
        val gestureVolume = booleanPreferencesKey("gesture_volume")
        val gestureSeek = booleanPreferencesKey("gesture_seek")
        val doubleTapSeek = booleanPreferencesKey("double_tap_seek")
        val lockTouch = booleanPreferencesKey("lock_touch")
        val pip = booleanPreferencesKey("pip")
        val background = booleanPreferencesKey("background")
        val subtitleSize = floatPreferencesKey("subtitle_size")
        val subtitleColor = stringPreferencesKey("subtitle_color")
        val folders = stringSetPreferencesKey("folders")
        val folderNames = stringSetPreferencesKey("folder_names")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "DARK") }.getOrDefault(ThemeMode.DARK),
            accent = AccentChoice.entries.firstOrNull { it.key == p[Keys.accent] } ?: AccentChoice.PURPLE,
            sortOrder = SortOrder.fromKey(p[Keys.sort]),
            viewMode = if (p[Keys.view] == "list") ViewMode.LIST else ViewMode.GRID,
            defaultSpeed = p[Keys.speed] ?: 1f,
            seekStepMs = ((p[Keys.seekStep] ?: 10f).toLong()) * 1000L,
            autoResume = p[Keys.autoResume] ?: true,
            gestureBrightness = p[Keys.gestureBrightness] ?: true,
            gestureVolume = p[Keys.gestureVolume] ?: true,
            gestureSeek = p[Keys.gestureSeek] ?: true,
            doubleTapSeek = p[Keys.doubleTapSeek] ?: true,
            lockTouchOnStart = p[Keys.lockTouch] ?: false,
            pipOnLeave = p[Keys.pip] ?: true,
            backgroundPlayback = p[Keys.background] ?: true,
            subtitleSize = p[Keys.subtitleSize] ?: 16f,
            subtitleColor = p[Keys.subtitleColor]?.toIntOrNull() ?: 0xFFFFFFFF.toInt(),
            customFolders = (p[Keys.folders] ?: emptySet()).toList(),
            folderNames = (p[Keys.folderNames] ?: emptySet()).associate {
                val sep = it.indexOf('|')
                if (sep > 0) it.substring(0, sep) to it.substring(sep + 1) else it to it
            }
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setAccent(a: AccentChoice) = context.dataStore.edit { it[Keys.accent] = a.key }
    suspend fun setSort(o: SortOrder) = context.dataStore.edit { it[Keys.sort] = o.key }
    suspend fun setViewMode(v: ViewMode) = context.dataStore.edit { it[Keys.view] = if (v == ViewMode.GRID) "grid" else "list" }
    suspend fun setDefaultSpeed(s: Float) = context.dataStore.edit { it[Keys.speed] = s }
    suspend fun setSeekStepSeconds(s: Int) = context.dataStore.edit { it[Keys.seekStep] = s.toFloat() }
    suspend fun setAutoResume(b: Boolean) = context.dataStore.edit { it[Keys.autoResume] = b }
    suspend fun setGestureBrightness(b: Boolean) = context.dataStore.edit { it[Keys.gestureBrightness] = b }
    suspend fun setGestureVolume(b: Boolean) = context.dataStore.edit { it[Keys.gestureVolume] = b }
    suspend fun setGestureSeek(b: Boolean) = context.dataStore.edit { it[Keys.gestureSeek] = b }
    suspend fun setDoubleTapSeek(b: Boolean) = context.dataStore.edit { it[Keys.doubleTapSeek] = b }
    suspend fun setLockTouch(b: Boolean) = context.dataStore.edit { it[Keys.lockTouch] = b }
    suspend fun setPip(b: Boolean) = context.dataStore.edit { it[Keys.pip] = b }
    suspend fun setBackground(b: Boolean) = context.dataStore.edit { it[Keys.background] = b }
    suspend fun setSubtitleSize(f: Float) = context.dataStore.edit { it[Keys.subtitleSize] = f }
    suspend fun setSubtitleColor(c: Int) = context.dataStore.edit { it[Keys.subtitleColor] = c.toString() }

    suspend fun addFolder(uri: String, name: String) = context.dataStore.edit {
        it[Keys.folders] = (it[Keys.folders] ?: emptySet()) + uri
        it[Keys.folderNames] = (it[Keys.folderNames] ?: emptySet()) + "$uri|$name"
    }

    suspend fun removeFolder(uri: String) = context.dataStore.edit {
        it[Keys.folders] = (it[Keys.folders] ?: emptySet()) - uri
        it[Keys.folderNames] = (it[Keys.folderNames] ?: emptySet()).filterNot { it.startsWith("$uri|") }.toSet()
    }
}
