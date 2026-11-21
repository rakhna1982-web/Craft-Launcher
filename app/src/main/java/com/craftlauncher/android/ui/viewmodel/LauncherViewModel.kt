package com.craftlauncher.android.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.craftlauncher.android.CraftLauncherApp
import com.craftlauncher.android.data.local.AccountEntity
import com.craftlauncher.android.data.local.CraftDatabase
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.local.JavaRuntimeEntity
import com.craftlauncher.android.data.local.LauncherSettings
import com.craftlauncher.android.data.model.LaunchPreflightReport
import com.craftlauncher.android.data.model.ModItem
import com.craftlauncher.android.data.model.ModLoaderType
import com.craftlauncher.android.data.model.MojangVersionSummary
import com.craftlauncher.android.data.model.RendererBackend
import com.craftlauncher.android.data.model.ResourcePackItem
import com.craftlauncher.android.data.model.StorageBreakdown
import com.craftlauncher.android.data.model.ThemeMode
import com.craftlauncher.android.data.model.UiAsyncState
import com.craftlauncher.android.data.remote.DeviceCodePrompt
import com.craftlauncher.android.data.remote.FabricLoaderOption
import com.craftlauncher.android.data.remote.MojangManifestResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MicrosoftLoginState {
    data object Idle : MicrosoftLoginState
    data object RequestingCode : MicrosoftLoginState
    data class WaitingForUser(
        val prompt: DeviceCodePrompt,
        val statusText: String
    ) : MicrosoftLoginState
    data class Error(val message: String) : MicrosoftLoginState
}

data class RuntimeDownloadProgress(
    val runtimeId: String,
    val progress: Float,
    val status: String
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as CraftLauncherApp).container
    private val prefs = container.preferences
    private val accountRepo = container.accountRepository
    private val versionRepo = container.versionRepository
    private val runtimeRepo = container.runtimeRepository
    private val contentRepo = container.contentRepository
    private val authService = container.microsoftAuthService
    private val preflightEngine = container.launchPreflightEngine

    val deviceAbi: String = CraftDatabase.detectPrimaryAbi()
    val maxRecommendedRamMb: Int = prefs.recommendedMaxRamMb

    val settings: StateFlow<LauncherSettings> = prefs.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LauncherSettings()
    )

    val accounts: StateFlow<List<AccountEntity>> = accountRepo.accountsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val instances: StateFlow<List<GameInstanceEntity>> = versionRepo.instancesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val recentlyPlayed: StateFlow<List<GameInstanceEntity>> = versionRepo.recentlyPlayedFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val runtimes: StateFlow<List<JavaRuntimeEntity>> = runtimeRepo.runtimesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val activeAccount: StateFlow<AccountEntity?> = combine(accounts, settings) { list, s ->
        list.firstOrNull { it.id == s.activeAccountId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeInstance: StateFlow<GameInstanceEntity?> = combine(instances, settings) { list, s ->
        list.firstOrNull { it.id == s.activeInstanceId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _manifestState = MutableStateFlow<UiAsyncState<MojangManifestResult>>(UiAsyncState.Loading)
    val manifestState: StateFlow<UiAsyncState<MojangManifestResult>> = _manifestState.asStateFlow()

    private val _fabricLoaders = MutableStateFlow<List<FabricLoaderOption>>(emptyList())
    val fabricLoaders: StateFlow<List<FabricLoaderOption>> = _fabricLoaders.asStateFlow()

    private val _msLoginState = MutableStateFlow<MicrosoftLoginState>(MicrosoftLoginState.Idle)
    val msLoginState: StateFlow<MicrosoftLoginState> = _msLoginState.asStateFlow()

    private val _runtimeDownload = MutableStateFlow<RuntimeDownloadProgress?>(null)
    val runtimeDownload: StateFlow<RuntimeDownloadProgress?> = _runtimeDownload.asStateFlow()

    private val _mods = MutableStateFlow<List<ModItem>>(emptyList())
    val mods: StateFlow<List<ModItem>> = _mods.asStateFlow()

    private val _resourcePacks = MutableStateFlow<List<ResourcePackItem>>(emptyList())
    val resourcePacks: StateFlow<List<ResourcePackItem>> = _resourcePacks.asStateFlow()

    private val _storageBreakdown = MutableStateFlow<StorageBreakdown?>(null)
    val storageBreakdown: StateFlow<StorageBreakdown?> = _storageBreakdown.asStateFlow()

    private val _preflightReport = MutableStateFlow<LaunchPreflightReport?>(null)
    val preflightReport: StateFlow<LaunchPreflightReport?> = _preflightReport.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages = _snackbarMessages.asSharedFlow()

    private var msLoginJob: Job? = null

    init {
        viewModelScope.launch {
            runtimeRepo.ensureSeededRuntimes()
            versionRepo.ensureStarterInstance()
            loadMojangManifest(forceRefresh = false)
            refreshStorageBreakdown()
        }
        viewModelScope.launch {
            activeInstance.collect { instance ->
                if (instance != null) {
                    refreshInstanceContent(instance.gameDirPath)
                }
            }
        }
    }

    fun loadMojangManifest(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _manifestState.value = UiAsyncState.Loading
            runCatching {
                versionRepo.fetchMojangManifest(forceRefresh)
            }.onSuccess { result ->
                _manifestState.value = UiAsyncState.Success(result)
            }.onFailure { err ->
                _manifestState.value = UiAsyncState.Error(
                    err.message ?: "Unable to fetch Mojang version manifest."
                )
            }
        }
    }

    fun loadFabricLoadersFor(mcVersion: String) {
        viewModelScope.launch {
            _fabricLoaders.value = emptyList()
            runCatching {
                versionRepo.fetchFabricLoaders(mcVersion)
            }.onSuccess { list ->
                _fabricLoaders.value = list
            }
        }
    }

    fun createAndSelectInstance(
        name: String,
        version: MojangVersionSummary,
        modLoader: ModLoaderType,
        modLoaderVersion: String?,
        installImmediately: Boolean
    ) {
        viewModelScope.launch {
            val created = versionRepo.createInstance(name, version, modLoader, modLoaderVersion)
            accountRepo.bindInstanceToActiveAccount(activeAccount.value?.id, created.id)
            _snackbarMessages.tryEmit("Instance '${created.name}' created")
            if (installImmediately) {
                installInstance(created.id, version.url)
            }
        }
    }

    fun installActiveOrSelectedInstance(instance: GameInstanceEntity) {
        viewModelScope.launch {
            val manifest = (_manifestState.value as? UiAsyncState.Success)?.data
            val match = manifest?.versions?.firstOrNull { it.id == instance.minecraftVersion }
            if (match == null) {
                // Attempt direct manifest refresh first
                runCatching { versionRepo.fetchMojangManifest(true) }
                    .onSuccess { fresh ->
                        _manifestState.value = UiAsyncState.Success(fresh)
                        val found = fresh.versions.firstOrNull { it.id == instance.minecraftVersion }
                        if (found != null) {
                            installInstance(instance.id, found.url)
                        } else {
                            _snackbarMessages.tryEmit("Version ${instance.minecraftVersion} not found in Mojang manifest.")
                        }
                    }
                    .onFailure { err ->
                        _snackbarMessages.tryEmit(err.message ?: "Network error loading Mojang manifest.")
                    }
            } else {
                installInstance(instance.id, match.url)
            }
        }
    }

    private fun installInstance(instanceId: String, versionUrl: String) {
        viewModelScope.launch {
            val res = versionRepo.installInstance(instanceId, versionUrl)
            res.onSuccess {
                _snackbarMessages.tryEmit("${it.name} installed & verified!")
                refreshStorageBreakdown()
            }.onFailure { err ->
                _snackbarMessages.tryEmit("Install failed: ${err.message}")
            }
        }
    }

    fun selectInstance(instanceId: String) {
        viewModelScope.launch {
            accountRepo.bindInstanceToActiveAccount(activeAccount.value?.id, instanceId)
        }
    }

    fun deleteInstance(instanceId: String) {
        viewModelScope.launch {
            versionRepo.deleteInstance(instanceId)
            if (settings.value.activeInstanceId == instanceId) {
                val fallback = instances.value.firstOrNull { it.id != instanceId }
                prefs.setActiveInstanceId(fallback?.id)
            }
            refreshStorageBreakdown()
            _snackbarMessages.tryEmit("Instance removed")
        }
    }

    // --- Accounts ---

    fun createLocalProfile(username: String, avatarUri: String?) {
        viewModelScope.launch {
            runCatching {
                accountRepo.createLocalProfile(
                    username = username,
                    avatarUri = avatarUri,
                    selectedInstanceId = activeInstance.value?.id
                )
            }.onSuccess { acc ->
                _snackbarMessages.tryEmit("Local profile '${acc.username}' ready")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Invalid profile details")
            }
        }
    }

    fun updateLocalProfile(accountId: String, username: String, avatarUri: String?, preferredRamMb: Int?) {
        viewModelScope.launch {
            runCatching {
                accountRepo.updateLocalProfile(accountId, username, avatarUri, preferredRamMb)
            }.onSuccess {
                _snackbarMessages.tryEmit("Profile updated")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Failed to update profile")
            }
        }
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            accountRepo.switchAccount(accountId)
        }
    }

    fun deleteAccount(accountId: String) {
        viewModelScope.launch {
            accountRepo.deleteAccount(accountId, activeAccount.value?.id)
            _snackbarMessages.tryEmit("Profile deleted")
        }
    }

    fun startMicrosoftDeviceLogin(clientIdInput: String) {
        msLoginJob?.cancel()
        msLoginJob = viewModelScope.launch {
            val cleanClientId = clientIdInput.trim()
            if (cleanClientId.isNotEmpty()) {
                prefs.setMicrosoftClientId(cleanClientId)
            }
            val effectiveClientId = cleanClientId.ifEmpty { settings.value.microsoftClientId }
            _msLoginState.value = MicrosoftLoginState.RequestingCode
            runCatching {
                val prompt = authService.requestDeviceCode(effectiveClientId)
                _msLoginState.value = MicrosoftLoginState.WaitingForUser(
                    prompt = prompt,
                    statusText = "Open ${prompt.verificationUri} and enter code ${prompt.userCode}"
                )
                val session = authService.pollForMicrosoftTokenAndCompleteChain(
                    clientId = effectiveClientId,
                    prompt = prompt
                ) { status ->
                    _msLoginState.value = MicrosoftLoginState.WaitingForUser(prompt, status)
                }
                accountRepo.saveMicrosoftSession(session, activeInstance.value?.id)
            }.onSuccess { acc ->
                _msLoginState.value = MicrosoftLoginState.Idle
                _snackbarMessages.tryEmit("Signed in as ${acc.username} (Licensed)")
            }.onFailure { err ->
                _msLoginState.value = MicrosoftLoginState.Error(
                    err.message ?: "Microsoft authentication failed."
                )
            }
        }
    }

    fun cancelMicrosoftLogin() {
        msLoginJob?.cancel()
        _msLoginState.value = MicrosoftLoginState.Idle
    }

    // --- Java Runtimes ---

    fun downloadRuntime(runtimeId: String) {
        viewModelScope.launch {
            _runtimeDownload.value = RuntimeDownloadProgress(runtimeId, 0.02f, "Starting download…")
            val res = runtimeRepo.downloadAndInstallRuntime(runtimeId) { progress, status ->
                _runtimeDownload.value = RuntimeDownloadProgress(runtimeId, progress, status)
            }
            _runtimeDownload.value = null
            res.onSuccess {
                refreshStorageBreakdown()
                _snackbarMessages.tryEmit("${it.name} ready")
            }.onFailure { err ->
                _snackbarMessages.tryEmit("Runtime download failed: ${err.message}")
            }
        }
    }

    fun importRuntimeArchive(runtimeId: String, uri: Uri) {
        viewModelScope.launch {
            _runtimeDownload.value = RuntimeDownloadProgress(runtimeId, 0.1f, "Importing archive…")
            val res = runtimeRepo.importRuntimeArchive(runtimeId, uri) { progress, status ->
                _runtimeDownload.value = RuntimeDownloadProgress(runtimeId, progress, status)
            }
            _runtimeDownload.value = null
            res.onSuccess {
                refreshStorageBreakdown()
                _snackbarMessages.tryEmit("${it.name} imported")
            }.onFailure { err ->
                _snackbarMessages.tryEmit("Import failed: ${err.message}")
            }
        }
    }

    fun verifyRuntime(runtimeId: String) {
        viewModelScope.launch {
            val res = runtimeRepo.verifyInstalledRuntime(runtimeId)
            val msg = if (res?.isVerified == true) {
                "${res.name} verified (libjvm.so OK)"
            } else {
                "Verification incomplete: bin/java or libjvm.so missing"
            }
            _snackbarMessages.tryEmit(msg)
        }
    }

    fun deleteRuntime(runtimeId: String) {
        viewModelScope.launch {
            runtimeRepo.deleteRuntime(runtimeId)
            refreshStorageBreakdown()
            _snackbarMessages.tryEmit("Runtime deleted")
        }
    }

    // --- Mods & Resource Packs ---

    fun refreshInstanceContent(gameDirPath: String) {
        viewModelScope.launch {
            _mods.value = contentRepo.listModsForInstance(gameDirPath)
            _resourcePacks.value = contentRepo.listResourcePacksForInstance(gameDirPath)
        }
    }

    fun importMod(uri: Uri) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.importModJar(dir, uri)
                .onSuccess {
                    refreshInstanceContent(dir)
                    refreshStorageBreakdown()
                    _snackbarMessages.tryEmit("Added mod: ${it.name} (${it.version})")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit("Mod import failed: ${err.message}")
                }
        }
    }

    fun toggleMod(mod: ModItem) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.toggleModEnabled(mod)
            refreshInstanceContent(dir)
        }
    }

    fun deleteMod(mod: ModItem) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.deleteMod(mod)
            refreshInstanceContent(dir)
            refreshStorageBreakdown()
        }
    }

    fun importResourcePack(uri: Uri) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.importResourcePack(dir, uri)
                .onSuccess {
                    refreshInstanceContent(dir)
                    refreshStorageBreakdown()
                    _snackbarMessages.tryEmit("Added resource pack: ${it.displayName}")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit("Pack import failed: ${err.message}")
                }
        }
    }

    fun toggleResourcePack(pack: ResourcePackItem) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.toggleResourcePack(dir, pack)
            refreshInstanceContent(dir)
        }
    }

    fun deleteResourcePack(pack: ResourcePackItem) {
        val dir = activeInstance.value?.gameDirPath ?: return
        viewModelScope.launch {
            contentRepo.deleteResourcePack(dir, pack)
            refreshInstanceContent(dir)
            refreshStorageBreakdown()
        }
    }

    // --- Launch Preflight ---

    fun prepareLaunchPreflight() {
        viewModelScope.launch {
            val acc = activeAccount.value
            val refreshedAcc = if (acc != null && acc.type == com.craftlauncher.android.data.model.AccountType.MICROSOFT) {
                accountRepo.refreshMicrosoftAccountIfNeeded(acc, settings.value.microsoftClientId)
                    .getOrElse { acc }
            } else {
                acc
            }
            val report = preflightEngine.evaluateLaunchReadiness(
                account = refreshedAcc,
                instance = activeInstance.value,
                runtimes = runtimes.value,
                settings = settings.value
            )
            _preflightReport.value = report
        }
    }

    fun dismissPreflightReport() {
        _preflightReport.value = null
    }

    fun markLaunchedInHistory(instanceId: String) {
        viewModelScope.launch {
            versionRepo.recordPlaySession(instanceId)
        }
    }

    // --- Settings & Storage ---

    fun updateRam(ramMb: Int) = viewModelScope.launch { prefs.setRamAllocationMb(ramMb) }
    fun updateResolution(scale: Int, w: Int, h: Int) = viewModelScope.launch { prefs.setResolution(scale, w, h) }
    fun updateJavaMode(mode: String) = viewModelScope.launch { prefs.setJavaRuntimeMode(mode) }
    fun updateThemeMode(mode: ThemeMode) = viewModelScope.launch { prefs.setThemeMode(mode) }
    fun updateLanguage(code: String) = viewModelScope.launch { prefs.setLanguageCode(code) }
    fun updateRenderer(backend: RendererBackend) = viewModelScope.launch { prefs.setRendererBackend(backend) }
    fun updateTouchControls(opacity: Float, scale: Float) = viewModelScope.launch { prefs.setTouchControls(opacity, scale) }
    fun updateAudioEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setAudioEnabled(enabled) }
    fun updateShowFps(show: Boolean) = viewModelScope.launch { prefs.setShowFpsOverlay(show) }
    fun updateMicrosoftClientId(id: String) = viewModelScope.launch { prefs.setMicrosoftClientId(id) }

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            _storageBreakdown.value = contentRepo.calculateStorageBreakdown()
        }
    }

    fun clearLauncherCache() {
        viewModelScope.launch {
            val freed = contentRepo.clearCache()
            refreshStorageBreakdown()
            _snackbarMessages.tryEmit("Cleared ${freed / 1024} KB of cache")
        }
    }
}
