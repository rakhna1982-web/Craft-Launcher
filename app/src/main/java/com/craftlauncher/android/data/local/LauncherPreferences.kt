package com.craftlauncher.android.data.local

import android.app.ActivityManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.craftlauncher.android.data.model.RendererBackend
import com.craftlauncher.android.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "craft_launcher_prefs")

data class LauncherSettings(
    val activeAccountId: String? = null,
    val activeInstanceId: String? = null,
    val ramAllocationMb: Int = 2048,
    val resolutionScalePercent: Int = 85,
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val defaultJavaRuntimeMode: String = "AUTO",
    val themeMode: ThemeMode = ThemeMode.OBSIDIAN_DARK,
    val languageCode: String = "en",
    val rendererBackend: RendererBackend = RendererBackend.GL4ES,
    val touchControlsOpacity: Float = 0.72f,
    val touchControlsScale: Float = 1.0f,
    val microsoftClientId: String = "",
    val enableAudio: Boolean = true,
    val showFpsOverlay: Boolean = true
)

class LauncherPreferences(private val context: Context) {

    private object Keys {
        val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        val ACTIVE_INSTANCE_ID = stringPreferencesKey("active_instance_id")
        val RAM_MB = intPreferencesKey("ram_mb")
        val RES_SCALE = intPreferencesKey("res_scale_percent")
        val RES_WIDTH = intPreferencesKey("res_width")
        val RES_HEIGHT = intPreferencesKey("res_height")
        val JAVA_MODE = stringPreferencesKey("java_runtime_mode")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val RENDERER = stringPreferencesKey("renderer_backend")
        val TOUCH_OPACITY = floatPreferencesKey("touch_opacity")
        val TOUCH_SCALE = floatPreferencesKey("touch_scale")
        val MS_CLIENT_ID = stringPreferencesKey("microsoft_client_id")
        val ENABLE_AUDIO = booleanPreferencesKey("enable_audio")
        val SHOW_FPS_OVERLAY = booleanPreferencesKey("show_fps_overlay")
    }

    val recommendedMaxRamMb: Int by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(info)
        val totalMb = (info.totalMem / (1024L * 1024L)).toInt().coerceAtLeast(2048)
        // Cap recommended max at 55% of total device RAM, between 1536 and 8192 MB
        ((totalMb * 0.55f).toInt() / 256 * 256).coerceIn(1536, 8192)
    }

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        val defaultRam = (recommendedMaxRamMb / 2).coerceIn(1024, 3072)
        LauncherSettings(
            activeAccountId = prefs[Keys.ACTIVE_ACCOUNT_ID],
            activeInstanceId = prefs[Keys.ACTIVE_INSTANCE_ID],
            ramAllocationMb = prefs[Keys.RAM_MB] ?: defaultRam,
            resolutionScalePercent = prefs[Keys.RES_SCALE] ?: 85,
            resolutionWidth = prefs[Keys.RES_WIDTH] ?: 1280,
            resolutionHeight = prefs[Keys.RES_HEIGHT] ?: 720,
            defaultJavaRuntimeMode = prefs[Keys.JAVA_MODE] ?: "AUTO",
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.OBSIDIAN_DARK,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "en",
            rendererBackend = prefs[Keys.RENDERER]?.let {
                runCatching { RendererBackend.valueOf(it) }.getOrNull()
            } ?: RendererBackend.GL4ES,
            touchControlsOpacity = prefs[Keys.TOUCH_OPACITY] ?: 0.72f,
            touchControlsScale = prefs[Keys.TOUCH_SCALE] ?: 1.0f,
            microsoftClientId = prefs[Keys.MS_CLIENT_ID] ?: "",
            enableAudio = prefs[Keys.ENABLE_AUDIO] ?: true,
            showFpsOverlay = prefs[Keys.SHOW_FPS_OVERLAY] ?: true
        )
    }

    suspend fun setActiveAccountId(accountId: String?) {
        context.dataStore.edit { prefs ->
            if (accountId == null) prefs.remove(Keys.ACTIVE_ACCOUNT_ID)
            else prefs[Keys.ACTIVE_ACCOUNT_ID] = accountId
        }
    }

    suspend fun setActiveInstanceId(instanceId: String?) {
        context.dataStore.edit { prefs ->
            if (instanceId == null) prefs.remove(Keys.ACTIVE_INSTANCE_ID)
            else prefs[Keys.ACTIVE_INSTANCE_ID] = instanceId
        }
    }

    suspend fun setRamAllocationMb(ramMb: Int) {
        context.dataStore.edit { it[Keys.RAM_MB] = ramMb.coerceIn(512, recommendedMaxRamMb) }
    }

    suspend fun setResolution(scalePercent: Int, width: Int, height: Int) {
        context.dataStore.edit {
            it[Keys.RES_SCALE] = scalePercent.coerceIn(40, 100)
            it[Keys.RES_WIDTH] = width
            it[Keys.RES_HEIGHT] = height
        }
    }

    suspend fun setJavaRuntimeMode(mode: String) {
        context.dataStore.edit { it[Keys.JAVA_MODE] = mode }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }

    suspend fun setRendererBackend(backend: RendererBackend) {
        context.dataStore.edit { it[Keys.RENDERER] = backend.name }
    }

    suspend fun setTouchControls(opacity: Float, scale: Float) {
        context.dataStore.edit {
            it[Keys.TOUCH_OPACITY] = opacity.coerceIn(0.2f, 1.0f)
            it[Keys.TOUCH_SCALE] = scale.coerceIn(0.6f, 1.5f)
        }
    }

    suspend fun setMicrosoftClientId(clientId: String) {
        context.dataStore.edit { it[Keys.MS_CLIENT_ID] = clientId.trim() }
    }

    suspend fun setAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_AUDIO] = enabled }
    }

    suspend fun setShowFpsOverlay(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_FPS_OVERLAY] = show }
    }
}
