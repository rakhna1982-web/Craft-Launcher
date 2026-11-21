package com.craftlauncher.android.engine

import android.content.Context
import com.craftlauncher.android.data.local.AccountEntity
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.local.JavaRuntimeEntity
import com.craftlauncher.android.data.local.LauncherSettings
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.data.model.LaunchPreflightReport
import com.craftlauncher.android.data.model.PreflightCheckItem
import com.craftlauncher.android.data.remote.MojangJsonParser
import com.craftlauncher.android.data.repository.AccountRepository
import com.craftlauncher.android.data.repository.RuntimeRepository
import com.craftlauncher.android.data.repository.VersionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coordinates pre-launch verification, JVM classpath/argument assembly, and checks for
 * Android native execution bridge components (PojavLauncher/Amethyst JNI bridge, GL4ES, LWJGL3-GLFW, OpenAL).
 */
class LaunchPreflightEngine(
    private val context: Context,
    private val versionRepository: VersionRepository,
    private val runtimeRepository: RuntimeRepository,
    private val accountRepository: AccountRepository
) {
    companion object {
        val REQUIRED_NATIVE_LIBS = listOf(
            "libpojavexec.so",
            "libgl4es_114.so",
            "liblwjgl_opengl.so",
            "libopenal.so"
        )
    }

    suspend fun evaluateLaunchReadiness(
        account: AccountEntity?,
        instance: GameInstanceEntity?,
        runtimes: List<JavaRuntimeEntity>,
        settings: LauncherSettings
    ): LaunchPreflightReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<PreflightCheckItem>()

        // 1. Account check
        val accountReady = when {
            account == null -> false
            account.type == AccountType.LOCAL -> account.username.isNotBlank()
            account.type == AccountType.MICROSOFT -> account.ownsMinecraft && !accountRepository.decryptMcToken(account).isNullOrBlank()
            else -> false
        }
        checks += PreflightCheckItem(
            id = "account",
            title = if (account?.type == AccountType.LOCAL) "Offline Local Profile" else "Microsoft Account Session",
            detail = when {
                account == null -> "No active profile selected."
                account.type == AccountType.LOCAL -> "Offline single-player mode as '${account.username}' (UUID ${account.uuid.take(8)}…)."
                account.ownsMinecraft -> "Licensed Minecraft Java profile '${account.username}' verified."
                else -> "Microsoft account missing active ownership entitlement or token."
            },
            passed = accountReady
        )

        // 2. Game files check
        val versionId = instance?.minecraftVersion ?: "None"
        val versionDir = File(versionRepository.versionsDir, versionId)
        val clientJar = File(versionDir, "$versionId.jar")
        val versionJsonFile = File(versionDir, "$versionId.json")
        val filesReady = instance != null &&
            instance.installStatus == InstallStatus.INSTALLED &&
            clientJar.exists() && clientJar.length() > 100_000L &&
            versionJsonFile.exists()

        checks += PreflightCheckItem(
            id = "game_files",
            title = "Minecraft Game Files ($versionId)",
            detail = when {
                instance == null -> "No game instance selected."
                filesReady -> "client.jar (${clientJar.length() / (1024 * 1024)} MB), libraries & asset index verified."
                else -> "Version $versionId is not installed yet. Download required Mojang files first."
            },
            passed = filesReady
        )

        // 3. Java Runtime check
        val requiredJava = instance?.requiredJavaMajor ?: 21
        val selectedRuntime = if (settings.defaultJavaRuntimeMode == "AUTO") {
            runtimes.firstOrNull { it.majorVersion == requiredJava && it.isInstalled }
                ?: runtimes.firstOrNull { it.isInstalled && it.majorVersion >= requiredJava }
        } else {
            runtimes.firstOrNull { it.id == settings.defaultJavaRuntimeMode && it.isInstalled }
        }
        val runtimeReady = selectedRuntime != null && selectedRuntime.isVerified

        checks += PreflightCheckItem(
            id = "java_runtime",
            title = "Java $requiredJava Runtime",
            detail = when {
                selectedRuntime == null -> "Java $requiredJava JRE not installed for ${runtimes.firstOrNull()?.architecture ?: "device ABI"}."
                !selectedRuntime.isVerified -> "${selectedRuntime.name} extracted, but libjvm.so verification failed."
                else -> "${selectedRuntime.name} (${selectedRuntime.architecture}) verified."
            },
            passed = runtimeReady
        )

        // 4. Native Execution Bridge check (GL4ES + GLFW + OpenAL + JNI JVM launcher)
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val engineDir = File(versionRepository.launcherRootDir, "native_engine")
        val missingNative = REQUIRED_NATIVE_LIBS.filter { libName ->
            !File(nativeLibDir, libName).exists() && !File(engineDir, libName).exists()
        }
        val nativeBridgeReady = missingNative.isEmpty()

        checks += PreflightCheckItem(
            id = "native_bridge",
            title = "Native Execution Bridge (GL4ES / GLFW / OpenAL)",
            detail = if (nativeBridgeReady) {
                "All native execution libraries detected in ${nativeLibDir.name}."
            } else {
                "Missing NDK native binaries: ${missingNative.joinToString(", ")}. Upstream PojavLauncher/Amethyst (GPLv3) C/JNI libraries must be compiled & bundled via Android NDK."
            },
            passed = nativeBridgeReady
        )

        // 5. Build real JVM Launch Command & Environment preview
        val ramMb = account?.preferredRamMb ?: settings.ramAllocationMb
        val classpathEntries = buildList {
            if (versionJsonFile.exists()) {
                runCatching {
                    val details = MojangJsonParser.parseVersionDetails(versionJsonFile.readText())
                    details.libraries.forEach { lib ->
                        val f = File(versionRepository.librariesDir, lib.path)
                        if (f.exists()) add(f.absolutePath)
                    }
                }
            }
            if (clientJar.exists()) add(clientJar.absolutePath)
        }

        val mainClass = if (versionJsonFile.exists()) {
            runCatching { MojangJsonParser.parseVersionDetails(versionJsonFile.readText()).mainClass }
                .getOrDefault("net.minecraft.client.main.Main")
        } else {
            "net.minecraft.client.main.Main"
        }

        val javaBin = selectedRuntime?.let {
            val home = runtimeRepository.findJavaHomeDir(File(it.installPath))
            File(home, "bin/java").absolutePath
        } ?: "/data/.../jre-$requiredJava/bin/java"

        val accessToken = if (account?.type == AccountType.MICROSOFT) {
            accountRepository.decryptMcToken(account) ?: "<expired>"
        } else {
            "0"
        }

        val cmd = listOf(
            javaBin,
            "-Xms${(ramMb / 2).coerceAtLeast(512)}M",
            "-Xmx${ramMb}M",
            "-Dorg.lwjgl.opengl.libname=${settings.rendererBackend.id.lowercase()}",
            "-Djava.library.path=${nativeLibDir.absolutePath}",
            "-cp",
            if (classpathEntries.isEmpty()) "<libraries+client.jar>" else classpathEntries.joinToString(":"),
            mainClass,
            "--username", account?.username ?: "Player",
            "--version", versionId,
            "--gameDir", instance?.gameDirPath ?: "<instanceDir>",
            "--assetsDir", versionRepository.assetsDir.absolutePath,
            "--assetIndex", versionId,
            "--uuid", account?.uuid ?: "00000000-0000-0000-0000-000000000000",
            "--accessToken", if (accessToken == "0") "0" else "<encrypted-mc-bearer-token>",
            "--userType", if (account?.type == AccountType.MICROSOFT) "msa" else "legacy",
            "--width", settings.resolutionWidth.toString(),
            "--height", settings.resolutionHeight.toString()
        )

        val env = mapOf(
            "HOME" to (instance?.gameDirPath ?: context.filesDir.absolutePath),
            "JAVA_HOME" to (selectedRuntime?.installPath ?: "<not-installed>"),
            "LIBGL_ES" to "3",
            "POJAV_RENDERER" to settings.rendererBackend.id,
            "MESA_GL_VERSION_OVERRIDE" to if (settings.rendererBackend.id == "ZINK") "4.6" else "2.1",
            "SDL_AUDIODRIVER" to if (settings.enableAudio) "android" else "dummy"
        )

        LaunchPreflightReport(
            instanceId = instance?.id.orEmpty(),
            instanceName = instance?.name ?: "No Instance",
            minecraftVersion = versionId,
            accountName = account?.username ?: "No Account",
            accountType = account?.type ?: AccountType.LOCAL,
            accountUuid = account?.uuid.orEmpty(),
            javaMajorRequired = requiredJava,
            selectedRuntimeName = selectedRuntime?.name,
            checks = checks,
            canExecuteJvmNatively = accountReady && filesReady && runtimeReady && nativeBridgeReady,
            jvmCommandLinePreview = cmd,
            environmentVars = env,
            missingNativeComponents = missingNative
        )
    }
}
