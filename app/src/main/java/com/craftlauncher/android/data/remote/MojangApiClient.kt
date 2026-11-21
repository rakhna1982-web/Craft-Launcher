package com.craftlauncher.android.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class MojangApiClient(
    private val context: Context,
    val okHttpClient: OkHttpClient = defaultHttpClient()
) {
    companion object {
        const val VERSION_MANIFEST_V2_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
        const val FABRIC_LOADER_URL = "https://meta.fabricmc.net/v2/versions/loader"
        const val RESOURCES_BASE_URL = "https://resources.download.minecraft.net"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val manifestCacheFile: File
        get() = File(context.cacheDir, "mojang_version_manifest_v2.json")

    suspend fun fetchVersionManifest(forceRefresh: Boolean = false): MojangManifestResult =
        withContext(Dispatchers.IO) {
            if (!forceRefresh && manifestCacheFile.exists()) {
                val ageMs = System.currentTimeMillis() - manifestCacheFile.lastModified()
                if (ageMs < 15 * 60 * 1000L) {
                    val cached = manifestCacheFile.readText()
                    return@withContext MojangJsonParser.parseManifest(cached, fromCache = true)
                }
            }

            try {
                val request = Request.Builder()
                    .url(VERSION_MANIFEST_V2_URL)
                    .header("User-Agent", "CraftLauncher-Android/1.0")
                    .build()
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Mojang manifest HTTP ${response.code}")
                    }
                    val body = response.body?.string()
                        ?: throw IOException("Empty response from Mojang piston-meta")
                    manifestCacheFile.writeText(body)
                    MojangJsonParser.parseManifest(body, fromCache = false)
                }
            } catch (e: Exception) {
                if (manifestCacheFile.exists()) {
                    val cached = manifestCacheFile.readText()
                    MojangJsonParser.parseManifest(cached, fromCache = true)
                } else {
                    throw e
                }
            }
        }

    suspend fun fetchString(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CraftLauncher-Android/1.0")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} fetching $url")
            }
            response.body?.string() ?: throw IOException("Empty response body from $url")
        }
    }

    suspend fun fetchFabricLoaders(mcVersion: String): List<FabricLoaderOption> =
        withContext(Dispatchers.IO) {
            val url = "$FABRIC_LOADER_URL/$mcVersion"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CraftLauncher-Android/1.0")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val raw = response.body?.string() ?: return@withContext emptyList()
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val loader = item.optJSONObject("loader") ?: continue
                        val ver = loader.optString("version")
                        val stable = loader.optBoolean("stable", false)
                        if (ver.isNotBlank()) add(FabricLoaderOption(ver, stable))
                    }
                }
            }
        }

    suspend fun downloadFile(
        url: String,
        targetFile: File,
        expectedSha1: String = "",
        onProgress: ((downloadedBytes: Long, totalBytes: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (targetFile.exists() && expectedSha1.isNotBlank()) {
            if (computeSha1(targetFile).equals(expectedSha1, ignoreCase = true)) {
                val len = targetFile.length()
                onProgress?.invoke(len, len)
                return@withContext
            }
        }

        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.part")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CraftLauncher-Android/1.0")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed (HTTP ${response.code}): $url")
            }
            val body = response.body ?: throw IOException("Empty download body: $url")
            val totalBytes = body.contentLength()
            var downloaded = 0L
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, totalBytes)
                    }
                }
            }
        }

        if (expectedSha1.isNotBlank()) {
            val actualSha1 = computeSha1(tempFile)
            if (!actualSha1.equals(expectedSha1, ignoreCase = true)) {
                tempFile.delete()
                throw IOException("SHA-1 mismatch for ${targetFile.name}: expected $expectedSha1 got $actualSha1")
            }
        }

        if (targetFile.exists()) targetFile.delete()
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
    }

    fun computeSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
