package com.craftlauncher.android.data.model

enum class AccountType {
    MICROSOFT,
    LOCAL
}

enum class ModLoaderType(val displayName: String) {
    VANILLA("Vanilla"),
    FABRIC("Fabric"),
    FORGE("Forge"),
    NEOFORGE("NeoForge")
}

enum class InstallStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    ERROR
}

enum class ThemeMode(val label: String) {
    OBSIDIAN_DARK("Obsidian Dark"),
    AMOLED_BLACK("AMOLED Pitch Black"),
    EMERALD_LIGHT("Emerald Daylight"),
    SYSTEM("Follow System")
}

enum class AvatarPreset(val key: String, val label: String) {
    STEVE("preset:steve", "Emerald Adventurer"),
    ALEX("preset:alex", "Cyber Crafter"),
    ENDERMAN("preset:enderman", "Void Guardian")
}

enum class RendererBackend(val id: String, val label: String, val description: String) {
    GL4ES("GL4ES", "GL4ES (OpenGL 2.1 -> GLES 2/3)", "Best compatibility for Minecraft 1.8 - 1.20.x and older mods"),
    ANGLE("ANGLE", "ANGLE / VirGL", "Vulkan-backed GLES translation for modern shaders"),
    ZINK("ZINK", "Mesa Zink (OpenGL 4.6 -> Vulkan)", "Required for Minecraft 1.21+ core profile and Sodium/Iris")
}

data class MojangVersionSummary(
    val id: String,
    val type: String, // "release", "snapshot", "old_beta", "old_alpha"
    val url: String,
    val releaseTime: String,
    val sha1: String
)

data class ModItem(
    val fileName: String,
    val filePath: String,
    val modId: String,
    val name: String,
    val version: String,
    val description: String,
    val authors: String,
    val loader: ModLoaderType,
    val isEnabled: Boolean,
    val sizeBytes: Long
)

data class ResourcePackItem(
    val fileName: String,
    val filePath: String,
    val displayName: String,
    val description: String,
    val packFormat: Int?,
    val isEnabled: Boolean,
    val sizeBytes: Long
)

data class PreflightCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val passed: Boolean,
    val isWarningOnly: Boolean = false
)

data class LaunchPreflightReport(
    val instanceId: String,
    val instanceName: String,
    val minecraftVersion: String,
    val accountName: String,
    val accountType: AccountType,
    val accountUuid: String,
    val javaMajorRequired: Int,
    val selectedRuntimeName: String?,
    val checks: List<PreflightCheckItem>,
    val canExecuteJvmNatively: Boolean,
    val jvmCommandLinePreview: List<String>,
    val environmentVars: Map<String, String>,
    val missingNativeComponents: List<String>
) {
    val allCoreFilesReady: Boolean
        get() = checks.filter { !it.isWarningOnly && it.id != "native_bridge" }.all { it.passed }
}

data class StorageBreakdown(
    val instancesBytes: Long,
    val assetsAndLibrariesBytes: Long,
    val runtimesBytes: Long,
    val cacheBytes: Long,
    val avatarsBytes: Long
) {
    val totalBytes: Long
        get() = instancesBytes + assetsAndLibrariesBytes + runtimesBytes + cacheBytes + avatarsBytes
}

sealed interface UiAsyncState<out T> {
    data object Loading : UiAsyncState<Nothing>
    data class Success<T>(val data: T) : UiAsyncState<T>
    data class Error(val message: String, val cachedData: Boolean = false) : UiAsyncState<Nothing>
}
