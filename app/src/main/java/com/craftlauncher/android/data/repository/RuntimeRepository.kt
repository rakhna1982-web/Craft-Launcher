package com.craftlauncher.android.data.repository

import android.content.Context
import android.net.Uri
import com.craftlauncher.android.data.local.CraftDatabase
import com.craftlauncher.android.data.local.JavaRuntimeDao
import com.craftlauncher.android.data.local.JavaRuntimeEntity
import com.craftlauncher.android.data.remote.MojangApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class RuntimeRepository(
    private val context: Context,
    private val runtimeDao: JavaRuntimeDao,
    private val mojangApiClient: MojangApiClient
) {
    val runtimesFlow: Flow<List<JavaRuntimeEntity>> = runtimeDao.observeAllRuntimes()

    suspend fun ensureSeededRuntimes() {
        runtimeDao.insertAllIgnore(CraftDatabase.buildDefaultRuntimeCatalog(context))
    }

    suspend fun ensureRuntimeInstalledForMajor(
        majorVersion: Int,
        onProgress: (Float, String) -> Unit
    ): Result<JavaRuntimeEntity> {
        val existing = runtimeDao.getInstalledByMajor(majorVersion)
        if (existing != null && verifyRuntimeDirectory(File(existing.installPath))) {
            return Result.success(existing)
        }
        val abi = CraftDatabase.detectPrimaryAbi()
        val expectedId = "jre-$majorVersion-$abi"
        if (runtimeDao.getRuntimeById(expectedId) == null) {
            runtimeDao.upsertRuntime(CraftDatabase.createRuntimeEntryForMajor(context, majorVersion))
        }
        return downloadAndInstallRuntime(expectedId, onProgress)
    }

    suspend fun downloadAndInstallRuntime(
        runtimeId: String,
        onProgress: (Float, String) -> Unit
    ): Result<JavaRuntimeEntity> = withContext(Dispatchers.IO) {
        val runtime = runtimeDao.getRuntimeById(runtimeId)
            ?: return@withContext Result.failure(IllegalArgumentException("Runtime not found"))

        return@withContext try {
            val archiveFile = File(context.cacheDir, "${runtime.id}.tar.gz")
            onProgress(0.05f, "Connecting to Adoptium repository…")
            mojangApiClient.downloadFile(
                url = runtime.downloadUrl,
                targetFile = archiveFile
            ) { downloaded, total ->
                if (total > 0) {
                    val p = 0.05f + 0.65f * (downloaded.toFloat() / total.toFloat())
                    onProgress(p, "Downloading Java ${runtime.majorVersion} (${downloaded / (1024 * 1024)} MB)…")
                } else {
                    onProgress(0.35f, "Downloading Java ${runtime.majorVersion} (${downloaded / (1024 * 1024)} MB)…")
                }
            }

            onProgress(0.75f, "Computing SHA-256 checksum…")
            val sha256 = computeSha256(archiveFile)

            onProgress(0.82f, "Extracting runtime files…")
            val targetDir = File(runtime.installPath).apply {
                deleteRecursively()
                mkdirs()
            }
            extractTarGz(archiveFile, targetDir)
            archiveFile.delete()

            val verified = verifyRuntimeDirectory(targetDir)
            val updated = runtime.copy(
                sha256 = sha256,
                sizeBytes = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                isInstalled = true,
                isVerified = verified,
                lastVerifiedAt = System.currentTimeMillis()
            )
            runtimeDao.upsertRuntime(updated)
            onProgress(1f, if (verified) "Java ${runtime.majorVersion} verified!" else "Extracted (warning: binary layout incomplete)")
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importRuntimeArchive(
        runtimeId: String,
        uri: Uri,
        onProgress: (Float, String) -> Unit
    ): Result<JavaRuntimeEntity> = withContext(Dispatchers.IO) {
        val runtime = runtimeDao.getRuntimeById(runtimeId)
            ?: return@withContext Result.failure(IllegalArgumentException("Runtime slot not found"))

        return@withContext try {
            onProgress(0.15f, "Copying selected JRE archive…")
            val tempArchive = File(context.cacheDir, "import_${runtime.id}.archive")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempArchive.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Unable to read selected file")

            val sha256 = computeSha256(tempArchive)
            val targetDir = File(runtime.installPath).apply {
                deleteRecursively()
                mkdirs()
            }

            onProgress(0.55f, "Extracting JRE package…")
            val isGzip = tempArchive.inputStream().use { ins ->
                val b0 = ins.read()
                val b1 = ins.read()
                b0 == 0x1f && b1 == 0x8b
            }
            if (isGzip) {
                extractTarGz(tempArchive, targetDir)
            } else {
                extractZip(tempArchive, targetDir)
            }
            tempArchive.delete()

            val verified = verifyRuntimeDirectory(targetDir)
            val updated = runtime.copy(
                sha256 = sha256,
                sizeBytes = targetDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                isInstalled = true,
                isVerified = verified,
                lastVerifiedAt = System.currentTimeMillis()
            )
            runtimeDao.upsertRuntime(updated)
            onProgress(1f, if (verified) "Imported & verified!" else "Imported (missing libjvm.so)")
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyInstalledRuntime(runtimeId: String): JavaRuntimeEntity? =
        withContext(Dispatchers.IO) {
            val runtime = runtimeDao.getRuntimeById(runtimeId) ?: return@withContext null
            val dir = File(runtime.installPath)
            val installed = dir.exists() && dir.listFiles()?.isNotEmpty() == true
            val verified = installed && verifyRuntimeDirectory(dir)
            val updated = runtime.copy(
                isInstalled = installed,
                isVerified = verified,
                lastVerifiedAt = System.currentTimeMillis()
            )
            runtimeDao.upsertRuntime(updated)
            updated
        }

    suspend fun deleteRuntime(runtimeId: String) = withContext(Dispatchers.IO) {
        val runtime = runtimeDao.getRuntimeById(runtimeId) ?: return@withContext
        File(runtime.installPath).deleteRecursively()
        runtimeDao.upsertRuntime(
            runtime.copy(isInstalled = false, isVerified = false, lastVerifiedAt = null)
        )
    }

    fun findJavaHomeDir(installRoot: File): File? {
        if (!installRoot.exists()) return null
        val direct = File(installRoot, "bin/java")
        if (direct.exists()) return installRoot
        // Adoptium tarballs extract a single top-level folder (e.g. jdk-21.0.6+7-jre)
        return installRoot.listFiles()?.firstOrNull { child ->
            child.isDirectory && (File(child, "bin/java").exists() || File(child, "lib").exists())
        } ?: installRoot
    }

    fun verifyRuntimeDirectory(installRoot: File): Boolean {
        val home = findJavaHomeDir(installRoot) ?: return false
        val hasJavaBin = File(home, "bin/java").exists()
        val hasLibJvm = home.walkTopDown().any { it.isFile && it.name == "libjvm.so" }
        return hasJavaBin || hasLibJvm
    }

    private fun extractZip(archive: File, destDir: File) {
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(destDir, entry.name)
                if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                    throw SecurityException("Zip Slip blocked: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /**
     * Pure-Kotlin POSIX/USTAR .tar.gz extractor (no external commons-compress needed).
     */
    private fun extractTarGz(archive: File, destDir: File) {
        GZIPInputStream(BufferedInputStream(archive.inputStream(), 32 * 1024)).use { tarIn ->
            val header = ByteArray(512)
            while (readFully(tarIn, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = parseTarString(header, 0, 100)
                val prefix = parseTarString(header, 345, 155)
                val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name
                val sizeStr = parseTarString(header, 124, 12).trim()
                val fileSize = sizeStr.toLongOrNull(8) ?: 0L
                val typeFlag = header[156].toInt().toChar()

                if (fullName.isNotBlank()) {
                    val outFile = File(destDir, fullName)
                    if (!outFile.canonicalPath.startsWith(destDir.canonicalPath)) {
                        throw SecurityException("Tar Slip blocked: $fullName")
                    }
                    when (typeFlag) {
                        '5' -> outFile.mkdirs()
                        '0', '\u0000' -> {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { out ->
                                var remaining = fileSize
                                val buf = ByteArray(8192)
                                while (remaining > 0) {
                                    val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                    val r = tarIn.read(buf, 0, toRead)
                                    if (r <= 0) break
                                    out.write(buf, 0, r)
                                    remaining -= r
                                }
                            }
                            if (fullName.endsWith("/bin/java") || fullName.endsWith(".so")) {
                                outFile.setExecutable(true, false)
                            }
                        }
                        else -> skipFully(tarIn, fileSize)
                    }
                } else {
                    skipFully(tarIn, fileSize)
                }

                val remainder = (512 - (fileSize % 512)) % 512
                if (remainder > 0) skipFully(tarIn, remainder)
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read == -1) return false
            offset += read
        }
        return true
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        val buf = ByteArray(4096)
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(remaining, buf.size.toLong()).toInt())
            if (r <= 0) break
            remaining -= r
        }
    }

    private fun parseTarString(buf: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = (offset + length).coerceAtMost(buf.size)
        while (end < limit && buf[end] != 0.toByte()) end++
        return String(buf, offset, end - offset, Charsets.UTF_8)
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r == -1) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
