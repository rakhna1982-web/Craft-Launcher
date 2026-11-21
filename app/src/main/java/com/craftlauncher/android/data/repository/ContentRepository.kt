package com.craftlauncher.android.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.craftlauncher.android.data.model.ModItem
import com.craftlauncher.android.data.model.ModLoaderType
import com.craftlauncher.android.data.model.ResourcePackItem
import com.craftlauncher.android.data.model.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

class ContentRepository(private val context: Context) {

    suspend fun listModsForInstance(gameDirPath: String): List<ModItem> = withContext(Dispatchers.IO) {
        val modsDir = File(gameDirPath, "mods").apply { mkdirs() }
        val files = modsDir.listFiles()?.filter {
            it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true))
        }?.sortedBy { it.name.lowercase() }.orEmpty()

        files.map { file -> parseModJarMetadata(file) }
    }

    suspend fun importModJar(gameDirPath: String, uri: Uri): Result<ModItem> = withContext(Dispatchers.IO) {
        runCatching {
            val modsDir = File(gameDirPath, "mods").apply { mkdirs() }
            val originalName = queryDisplayName(uri) ?: "mod_${System.currentTimeMillis()}.jar"
            val safeName = if (originalName.endsWith(".jar", true)) originalName else "$originalName.jar"
            val target = File(modsDir, safeName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to open selected mod file.")
            parseModJarMetadata(target)
        }
    }

    suspend fun toggleModEnabled(mod: ModItem): ModItem = withContext(Dispatchers.IO) {
        val current = File(mod.filePath)
        val newFile = if (mod.isEnabled) {
            File(current.parentFile, "${current.name}.disabled")
        } else {
            File(current.parentFile, current.name.removeSuffix(".disabled"))
        }
        if (current.exists()) current.renameTo(newFile)
        parseModJarMetadata(newFile)
    }

    suspend fun deleteMod(mod: ModItem) = withContext(Dispatchers.IO) {
        File(mod.filePath).delete()
    }

    suspend fun listResourcePacksForInstance(gameDirPath: String): List<ResourcePackItem> =
        withContext(Dispatchers.IO) {
            val packsDir = File(gameDirPath, "resourcepacks").apply { mkdirs() }
            val enabledSet = readEnabledPacksFromOptions(gameDirPath)
            val files = packsDir.listFiles()?.filter {
                it.isFile && it.name.endsWith(".zip", true)
            }?.sortedBy { it.name.lowercase() }.orEmpty()

            files.map { file ->
                val isEnabled = enabledSet.contains("file/${file.name}") || enabledSet.contains(file.name)
                parseResourcePackMetadata(file, isEnabled)
            }
        }

    suspend fun importResourcePack(gameDirPath: String, uri: Uri): Result<ResourcePackItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val packsDir = File(gameDirPath, "resourcepacks").apply { mkdirs() }
                val originalName = queryDisplayName(uri) ?: "pack_${System.currentTimeMillis()}.zip"
                val safeName = if (originalName.endsWith(".zip", true)) originalName else "$originalName.zip"
                val target = File(packsDir, safeName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Failed to open selected resource pack.")
                setResourcePackEnabledInOptions(gameDirPath, safeName, true)
                parseResourcePackMetadata(target, isEnabled = true)
            }
        }

    suspend fun toggleResourcePack(gameDirPath: String, pack: ResourcePackItem): ResourcePackItem =
        withContext(Dispatchers.IO) {
            val nextEnabled = !pack.isEnabled
            setResourcePackEnabledInOptions(gameDirPath, pack.fileName, nextEnabled)
            pack.copy(isEnabled = nextEnabled)
        }

    suspend fun deleteResourcePack(gameDirPath: String, pack: ResourcePackItem) =
        withContext(Dispatchers.IO) {
            setResourcePackEnabledInOptions(gameDirPath, pack.fileName, false)
            File(pack.filePath).delete()
        }

    suspend fun calculateStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "craftlauncher")
        StorageBreakdown(
            instancesBytes = dirSize(File(root, "instances")) + dirSize(File(root, "versions")),
            assetsAndLibrariesBytes = dirSize(File(root, "assets")) + dirSize(File(root, "libraries")),
            runtimesBytes = dirSize(File(root, "runtimes")),
            cacheBytes = dirSize(context.cacheDir),
            avatarsBytes = dirSize(File(root, "avatars"))
        )
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        val before = dirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        before
    }

    private fun parseModJarMetadata(file: File): ModItem {
        val isEnabled = !file.name.endsWith(".disabled", true)
        var modId = file.nameWithoutExtension.removeSuffix(".jar")
        var modName = modId
        var version = "Unknown"
        var description = ""
        var authors = ""
        var loader = ModLoaderType.VANILLA

        try {
            ZipFile(file).use { zip ->
                val fabricEntry = zip.getEntry("fabric.mod.json")
                val neoforgeEntry = zip.getEntry("META-INF/neoforge.mods.toml")
                val forgeTomlEntry = zip.getEntry("META-INF/mods.toml")
                val mcmodInfo = zip.getEntry("mcmod.info")

                when {
                    fabricEntry != null -> {
                        loader = ModLoaderType.FABRIC
                        val raw = zip.getInputStream(fabricEntry).bufferedReader().readText()
                        val json = JSONObject(raw)
                        modId = json.optString("id", modId)
                        modName = json.optString("name", modId)
                        version = json.optString("version", "Unknown")
                        description = json.optString("description", "")
                        val authorsArr = json.optJSONArray("authors")
                        if (authorsArr != null) {
                            authors = (0 until authorsArr.length()).mapNotNull { idx ->
                                val item = authorsArr.opt(idx)
                                when (item) {
                                    is String -> item
                                    is JSONObject -> item.optString("name")
                                    else -> null
                                }
                            }.joinToString(", ")
                        }
                    }
                    neoforgeEntry != null || forgeTomlEntry != null -> {
                        loader = if (neoforgeEntry != null) ModLoaderType.NEOFORGE else ModLoaderType.FORGE
                        val entry = neoforgeEntry ?: forgeTomlEntry!!
                        val toml = zip.getInputStream(entry).bufferedReader().readText()
                        modId = extractTomlValue(toml, "modId") ?: modId
                        modName = extractTomlValue(toml, "displayName") ?: modId
                        version = extractTomlValue(toml, "version") ?: "Unknown"
                        authors = extractTomlValue(toml, "authors") ?: ""
                        description = extractTomlValue(toml, "description") ?: ""
                    }
                    mcmodInfo != null -> {
                        loader = ModLoaderType.FORGE
                        val raw = zip.getInputStream(mcmodInfo).bufferedReader().readText().trim()
                        val firstObj = if (raw.startsWith("[")) {
                            JSONArray(raw).optJSONObject(0)
                        } else {
                            JSONObject(raw).optJSONArray("modList")?.optJSONObject(0)
                        }
                        if (firstObj != null) {
                            modId = firstObj.optString("modid", modId)
                            modName = firstObj.optString("name", modId)
                            version = firstObj.optString("version", "Unknown")
                            description = firstObj.optString("description", "")
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Leave fallback filename metadata if jar cannot be parsed
        }

        return ModItem(
            fileName = file.name,
            filePath = file.absolutePath,
            modId = modId,
            name = modName,
            version = version,
            description = description,
            authors = authors,
            loader = loader,
            isEnabled = isEnabled,
            sizeBytes = file.length()
        )
    }

    private fun extractTomlValue(toml: String, key: String): String? {
        val regex = Regex("""(?m)^\s*$key\s*=\s*["']([^"']+)["']""")
        return regex.find(toml)?.groupValues?.getOrNull(1)
    }

    private fun parseResourcePackMetadata(file: File, isEnabled: Boolean): ResourcePackItem {
        var description = "Minecraft Resource Pack"
        var packFormat: Int? = null
        try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("pack.mcmeta")
                if (entry != null) {
                    val raw = zip.getInputStream(entry).bufferedReader().readText()
                    val pack = JSONObject(raw).optJSONObject("pack")
                    if (pack != null) {
                        packFormat = if (pack.has("pack_format")) pack.optInt("pack_format") else null
                        val descAny = pack.opt("description")
                        description = when (descAny) {
                            is String -> descAny
                            is JSONObject -> descAny.optString("text", description)
                            else -> description
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return ResourcePackItem(
            fileName = file.name,
            filePath = file.absolutePath,
            displayName = file.nameWithoutExtension,
            description = description.replace(Regex("§[0-9a-fk-or]"), ""),
            packFormat = packFormat,
            isEnabled = isEnabled,
            sizeBytes = file.length()
        )
    }

    private fun readEnabledPacksFromOptions(gameDirPath: String): Set<String> {
        val optionsFile = File(gameDirPath, "options.txt")
        if (!optionsFile.exists()) return emptySet()
        val line = optionsFile.readLines().firstOrNull { it.startsWith("resourcePacks:") } ?: return emptySet()
        val arrayStr = line.substringAfter("resourcePacks:").trim()
        return runCatching {
            val arr = JSONArray(arrayStr)
            (0 until arr.length()).mapNotNull { arr.optString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun setResourcePackEnabledInOptions(gameDirPath: String, fileName: String, enabled: Boolean) {
        val optionsFile = File(gameDirPath, "options.txt")
        val lines = if (optionsFile.exists()) optionsFile.readLines().toMutableList() else mutableListOf()
        val idx = lines.indexOfFirst { it.startsWith("resourcePacks:") }
        val currentList = if (idx >= 0) {
            val raw = lines[idx].substringAfter("resourcePacks:").trim()
            runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.getString(it) }.toMutableList()
            }.getOrDefault(mutableListOf("vanilla"))
        } else {
            mutableListOf("vanilla")
        }

        val token = "file/$fileName"
        currentList.remove(fileName)
        if (enabled) {
            if (!currentList.contains(token)) currentList.add(token)
        } else {
            currentList.remove(token)
        }

        val updatedLine = "resourcePacks:${JSONArray(currentList)}"
        if (idx >= 0) lines[idx] = updatedLine else lines.add(updatedLine)
        optionsFile.parentFile?.mkdirs()
        optionsFile.writeText(lines.joinToString("\n") + "\n")
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
