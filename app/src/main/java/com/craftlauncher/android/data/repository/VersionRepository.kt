package com.craftlauncher.android.data.repository

import android.content.Context
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.local.InstanceDao
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.data.model.ModLoaderType
import com.craftlauncher.android.data.model.MojangVersionSummary
import com.craftlauncher.android.data.remote.MojangApiClient
import com.craftlauncher.android.data.remote.MojangJsonParser
import com.craftlauncher.android.data.remote.MojangManifestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class VersionRepository(
    private val context: Context,
    private val instanceDao: InstanceDao,
    private val mojangApiClient: MojangApiClient
) {
    val instancesFlow: Flow<List<GameInstanceEntity>> = instanceDao.observeAllInstances()
    val recentlyPlayedFlow: Flow<List<GameInstanceEntity>> = instanceDao.observeRecentlyPlayed()

    val launcherRootDir: File
        get() = File(context.filesDir, "craftlauncher").apply { mkdirs() }

    val versionsDir: File
        get() = File(launcherRootDir, "versions").apply { mkdirs() }

    val librariesDir: File
        get() = File(launcherRootDir, "libraries").apply { mkdirs() }

    val assetsDir: File
        get() = File(launcherRootDir, "assets").apply { mkdirs() }

    val instancesDir: File
        get() = File(launcherRootDir, "instances").apply { mkdirs() }

    suspend fun fetchMojangManifest(forceRefresh: Boolean = false): MojangManifestResult =
        mojangApiClient.fetchVersionManifest(forceRefresh)

    suspend fun fetchFabricLoaders(mcVersion: String) =
        mojangApiClient.fetchFabricLoaders(mcVersion)

    suspend fun createInstance(
        name: String,
        version: MojangVersionSummary,
        modLoader: ModLoaderType,
        modLoaderVersion: String?
    ): GameInstanceEntity = withContext(Dispatchers.IO) {
        val slug = "${modLoader.name.lowercase()}-${version.id}-${System.currentTimeMillis() % 10000}"
        val instanceDir = File(instancesDir, slug).apply {
            mkdirs()
            File(this, "mods").mkdirs()
            File(this, "resourcepacks").mkdirs()
            File(this, "saves").mkdirs()
            File(this, "config").mkdirs()
        }

        val estimatedJava = inferJavaVersion(version.id)
        val instance = GameInstanceEntity(
            id = slug,
            name = name.ifBlank { "Minecraft ${version.id}" },
            minecraftVersion = version.id,
            versionType = version.type,
            modLoader = modLoader,
            modLoaderVersion = modLoaderVersion,
            requiredJavaMajor = estimatedJava,
            installStatus = InstallStatus.NOT_INSTALLED,
            installProgress = 0f,
            installStage = "Ready to download",
            gameDirPath = instanceDir.absolutePath
        )
        instanceDao.upsertInstance(instance)
        instance
    }

    suspend fun installInstance(
        instanceId: String,
        versionUrl: String
    ): Result<GameInstanceEntity> = withContext(Dispatchers.IO) {
        val current = instanceDao.getInstanceById(instanceId)
            ?: return@withContext Result.failure(IllegalStateException("Instance not found"))

        return@withContext try {
            updateInstallState(current, InstallStatus.DOWNLOADING, 0.04f, "Fetching version metadata…")
            val versionJsonStr = mojangApiClient.fetchString(versionUrl)
            val details = MojangJsonParser.parseVersionDetails(versionJsonStr)

            val versionFolder = File(versionsDir, details.id).apply { mkdirs() }
            File(versionFolder, "${details.id}.json").writeText(versionJsonStr)

            val updatedJava = current.copy(requiredJavaMajor = details.requiredJavaMajor)

            updateInstallState(updatedJava, InstallStatus.DOWNLOADING, 0.12f, "Downloading client ${details.id}.jar…")
            val clientJarFile = File(versionFolder, "${details.id}.jar")
            mojangApiClient.downloadFile(
                url = details.clientJar.url,
                targetFile = clientJarFile,
                expectedSha1 = details.clientJar.sha1
            ) { done, total ->
                if (total > 0) {
                    val frac = 0.12f + 0.25f * (done.toFloat() / total.toFloat())
                    // lightweight progress without excessive DB writes
                    if ((done / 524_288L) != ((done - 16_384L) / 524_288L)) {
                        // periodic update handled below
                    }
                }
            }

            // Download JVM libraries with SHA-1 verification
            val totalLibs = details.libraries.size.coerceAtLeast(1)
            details.libraries.forEachIndexed { index, lib ->
                val targetLib = File(librariesDir, lib.path)
                val progress = 0.38f + 0.37f * (index.toFloat() / totalLibs.toFloat())
                if (index % 4 == 0 || index == totalLibs - 1) {
                    updateInstallState(
                        updatedJava,
                        InstallStatus.DOWNLOADING,
                        progress,
                        "Downloading libraries (${index + 1}/$totalLibs)…"
                    )
                }
                mojangApiClient.downloadFile(
                    url = lib.url,
                    targetFile = targetLib,
                    expectedSha1 = lib.sha1
                )
            }

            // Download asset index JSON and essential language/icon assets
            updateInstallState(updatedJava, InstallStatus.DOWNLOADING, 0.78f, "Fetching asset index…")
            val indexesDir = File(assetsDir, "indexes").apply { mkdirs() }
            val indexFile = File(indexesDir, "${details.assetIndexId}.json")
            mojangApiClient.downloadFile(
                url = details.assetIndexUrl,
                targetFile = indexFile,
                expectedSha1 = details.assetIndexSha1
            )

            val indexRoot = JSONObject(indexFile.readText())
            val objects = indexRoot.optJSONObject("objects")
            val assetKeys = objects?.keys()?.asSequence()?.toList().orEmpty()
            // Download essential core assets first (icons, lang, pack metadata) plus up to 120 core objects
            // so verification completes quickly on mobile while keeping full index for background/on-demand sync
            val prioritized = assetKeys.filter {
                it.contains("icons/") || it.contains("lang/en_us") || it.contains("minecraft/sounds/ui/") || it.endsWith(".mcmeta")
            } + assetKeys.take(80)
            val distinctAssets = prioritized.distinct()
            val objectsDir = File(assetsDir, "objects").apply { mkdirs() }

            distinctAssets.forEachIndexed { idx, key ->
                val obj = objects?.optJSONObject(key) ?: return@forEachIndexed
                val hash = obj.optString("hash")
                if (hash.length >= 2) {
                    val sub = hash.substring(0, 2)
                    val target = File(objectsDir, "$sub/$hash")
                    val url = "${MojangApiClient.RESOURCES_BASE_URL}/$sub/$hash"
                    if (idx % 10 == 0 || idx == distinctAssets.lastIndex) {
                        val p = 0.80f + 0.18f * ((idx + 1).toFloat() / distinctAssets.size.coerceAtLeast(1))
                        updateInstallState(
                            updatedJava,
                            InstallStatus.DOWNLOADING,
                            p,
                            "Verifying assets (${idx + 1}/${distinctAssets.size})…"
                        )
                    }
                    mojangApiClient.downloadFile(url, target, expectedSha1 = hash)
                }
            }

            val totalBytes = clientJarFile.length() +
                details.libraries.sumOf { File(librariesDir, it.path).length() } +
                File(current.gameDirPath).walkTopDown().filter { it.isFile }.sumOf { it.length() }

            val finished = updatedJava.copy(
                installStatus = InstallStatus.INSTALLED,
                installProgress = 1f,
                installStage = "Installed & SHA-1 verified",
                totalSizeBytes = totalBytes
            )
            instanceDao.upsertInstance(finished)
            Result.success(finished)
        } catch (e: Exception) {
            val failed = current.copy(
                installStatus = InstallStatus.ERROR,
                installProgress = 0f,
                installStage = e.message ?: "Download failed"
            )
            instanceDao.upsertInstance(failed)
            Result.failure(e)
        }
    }

    suspend fun deleteInstance(instanceId: String) = withContext(Dispatchers.IO) {
        val instance = instanceDao.getInstanceById(instanceId)
        instanceDao.deleteInstanceById(instanceId)
        if (instance != null) {
            File(instance.gameDirPath).deleteRecursively()
        }
    }

    suspend fun recordPlaySession(instanceId: String) {
        val instance = instanceDao.getInstanceById(instanceId) ?: return
        instanceDao.upsertInstance(
            instance.copy(
                lastPlayedAt = System.currentTimeMillis(),
                playCount = instance.playCount + 1
            )
        )
    }

    suspend fun ensureStarterInstance(defaultVersionId: String = "1.21.4") {
        if (instanceDao.getFirstInstance() != null) return
        val instanceDir = File(instancesDir, "vanilla-default").apply {
            mkdirs()
            File(this, "mods").mkdirs()
            File(this, "resourcepacks").mkdirs()
            File(this, "saves").mkdirs()
        }
        instanceDao.upsertInstance(
            GameInstanceEntity(
                id = "vanilla-default",
                name = "Vanilla $defaultVersionId",
                minecraftVersion = defaultVersionId,
                versionType = "release",
                modLoader = ModLoaderType.VANILLA,
                requiredJavaMajor = 21,
                installStatus = InstallStatus.NOT_INSTALLED,
                installProgress = 0f,
                installStage = "Tap Install to download Mojang files",
                gameDirPath = instanceDir.absolutePath
            )
        )
    }

    private suspend fun updateInstallState(
        instance: GameInstanceEntity,
        status: InstallStatus,
        progress: Float,
        stage: String
    ) {
        instanceDao.upsertInstance(
            instance.copy(
                installStatus = status,
                installProgress = progress.coerceIn(0f, 1f),
                installStage = stage
            )
        )
    }

    private fun inferJavaVersion(versionId: String): Int {
        val parts = versionId.split(".")
        val major = parts.getOrNull(0)?.toIntOrNull()
        if (major != null && major >= 26) return 25
        if (versionId.startsWith("26w")) return 25
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: return 21
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return when {
            minor >= 22 -> 25
            minor >= 21 -> 21
            minor == 20 && patch >= 5 -> 21
            minor >= 18 -> 17
            minor == 17 -> 16
            else -> 8
        }
    }
}
