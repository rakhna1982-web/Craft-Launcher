package com.example

import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.craftlauncher.android.ui.components.LaunchPreflightSheet
import com.craftlauncher.android.ui.game.GameActivity
import com.craftlauncher.android.ui.screens.AccountsScreen
import com.craftlauncher.android.ui.screens.ContentScreen
import com.craftlauncher.android.ui.screens.HomeScreen
import com.craftlauncher.android.ui.screens.SettingsScreen
import com.craftlauncher.android.ui.screens.VersionsScreen
import com.craftlauncher.android.ui.theme.CraftLauncherTheme
import com.craftlauncher.android.ui.viewmodel.LauncherViewModel
import java.util.Locale

sealed class CraftDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Home : CraftDestination("home", R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home, "nav_home")
    data object Versions : CraftDestination("versions", R.string.nav_versions, Icons.Rounded.Layers, Icons.Outlined.Layers, "nav_versions")
    data object Content : CraftDestination("content", R.string.nav_content, Icons.Rounded.Extension, Icons.Outlined.Extension, "nav_content")
    data object Accounts : CraftDestination("accounts", R.string.nav_accounts, Icons.Rounded.AccountCircle, Icons.Outlined.AccountCircle, "nav_accounts")
    data object Settings : CraftDestination("settings", R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings, "nav_settings")
}

private val craftDestinations = listOf(
    CraftDestination.Home,
    CraftDestination.Versions,
    CraftDestination.Content,
    CraftDestination.Accounts,
    CraftDestination.Settings
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LauncherViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            val localizedConfig = remember(settings.languageCode, resources.configuration) {
                Configuration(resources.configuration).apply {
                    setLocale(Locale.forLanguageTag(settings.languageCode))
                }
            }
            val localizedContext = remember(settings.languageCode, localizedConfig) {
                val configCtx = createConfigurationContext(localizedConfig)
                object : ContextWrapper(this@MainActivity) {
                    override fun getResources(): Resources = configCtx.resources
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfig,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalOnBackPressedDispatcherOwner provides this@MainActivity
            ) {
                CraftLauncherTheme(themeMode = settings.themeMode) {
                    CraftLauncherRoot(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CraftLauncherRoot(viewModel: LauncherViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: CraftDestination.Home.route

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val activeInstance by viewModel.activeInstance.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val runtimes by viewModel.runtimes.collectAsStateWithLifecycle()
    val manifestState by viewModel.manifestState.collectAsStateWithLifecycle()
    val fabricLoaders by viewModel.fabricLoaders.collectAsStateWithLifecycle()
    val msLoginState by viewModel.msLoginState.collectAsStateWithLifecycle()
    val runtimeDownload by viewModel.runtimeDownload.collectAsStateWithLifecycle()
    val mods by viewModel.mods.collectAsStateWithLifecycle()
    val resourcePacks by viewModel.resourcePacks.collectAsStateWithLifecycle()
    val storageBreakdown by viewModel.storageBreakdown.collectAsStateWithLifecycle()
    val preflightReport by viewModel.preflightReport.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        craftDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        craftDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = CraftDestination.Home.route
                    ) {
                        composable(CraftDestination.Home.route) {
                            HomeScreen(
                                activeAccount = activeAccount,
                                activeInstance = activeInstance,
                                recentlyPlayed = recentlyPlayed,
                                runtimes = runtimes,
                                settings = settings,
                                modsCount = mods.count { it.isEnabled },
                                packsCount = resourcePacks.count { it.isEnabled },
                                onPlayClick = { viewModel.prepareLaunchPreflight() },
                                onInstallInstance = { viewModel.installActiveOrSelectedInstance(it) },
                                onOpenAccounts = { navigateTo(CraftDestination.Accounts.route) },
                                onOpenVersions = { navigateTo(CraftDestination.Versions.route) },
                                onOpenContent = { navigateTo(CraftDestination.Content.route) },
                                onOpenRuntimes = { navigateTo(CraftDestination.Settings.route) },
                                onSelectRecentInstance = { viewModel.selectInstance(it) }
                            )
                        }
                        composable(CraftDestination.Versions.route) {
                            VersionsScreen(
                                instances = instances,
                                activeInstanceId = activeInstance?.id,
                                manifestState = manifestState,
                                fabricLoaders = fabricLoaders,
                                onRefreshManifest = { viewModel.loadMojangManifest(forceRefresh = true) },
                                onSelectInstance = { viewModel.selectInstance(it) },
                                onInstallInstance = { viewModel.installActiveOrSelectedInstance(it) },
                                onDeleteInstance = { viewModel.deleteInstance(it) },
                                onLoadFabricLoaders = { viewModel.loadFabricLoadersFor(it) },
                                onCreateInstance = { name, ver, loader, loaderVer, installNow ->
                                    viewModel.createAndSelectInstance(name, ver, loader, loaderVer, installNow)
                                }
                            )
                        }
                        composable(CraftDestination.Content.route) {
                            ContentScreen(
                                instances = instances,
                                activeInstance = activeInstance,
                                mods = mods,
                                resourcePacks = resourcePacks,
                                onSelectInstance = { viewModel.selectInstance(it) },
                                onImportMod = { viewModel.importMod(it) },
                                onToggleMod = { viewModel.toggleMod(it) },
                                onDeleteMod = { viewModel.deleteMod(it) },
                                onImportResourcePack = { viewModel.importResourcePack(it) },
                                onToggleResourcePack = { viewModel.toggleResourcePack(it) },
                                onDeleteResourcePack = { viewModel.deleteResourcePack(it) }
                            )
                        }
                        composable(CraftDestination.Accounts.route) {
                            AccountsScreen(
                                accounts = accounts,
                                activeAccountId = activeAccount?.id,
                                msLoginState = msLoginState,
                                savedClientId = settings.microsoftClientId,
                                onCreateLocalProfile = { name, avatar ->
                                    viewModel.createLocalProfile(name, avatar)
                                },
                                onUpdateLocalProfile = { id, name, avatar, ram ->
                                    viewModel.updateLocalProfile(id, name, avatar, ram)
                                },
                                onSwitchAccount = { viewModel.switchAccount(it) },
                                onDeleteAccount = { viewModel.deleteAccount(it) },
                                onStartMicrosoftLogin = { viewModel.startMicrosoftDeviceLogin(it) },
                                onCancelMicrosoftLogin = { viewModel.cancelMicrosoftLogin() }
                            )
                        }
                        composable(CraftDestination.Settings.route) {
                            SettingsScreen(
                                deviceAbi = viewModel.deviceAbi,
                                maxRamMb = viewModel.maxRecommendedRamMb,
                                settings = settings,
                                runtimes = runtimes,
                                runtimeDownload = runtimeDownload,
                                storageBreakdown = storageBreakdown,
                                onUpdateRam = { viewModel.updateRam(it) },
                                onUpdateResolution = { s, w, h -> viewModel.updateResolution(s, w, h) },
                                onUpdateJavaMode = { viewModel.updateJavaMode(it) },
                                onUpdateTheme = { viewModel.updateThemeMode(it) },
                                onUpdateLanguage = { viewModel.updateLanguage(it) },
                                onUpdateRenderer = { viewModel.updateRenderer(it) },
                                onUpdateTouchControls = { o, s -> viewModel.updateTouchControls(o, s) },
                                onUpdateAudio = { viewModel.updateAudioEnabled(it) },
                                onUpdateShowFps = { viewModel.updateShowFps(it) },
                                onDownloadRuntime = { viewModel.downloadRuntime(it) },
                                onImportRuntimeArchive = { id, uri -> viewModel.importRuntimeArchive(id, uri) },
                                onVerifyRuntime = { viewModel.verifyRuntime(it) },
                                onDeleteRuntime = { viewModel.deleteRuntime(it) },
                                onClearCache = { viewModel.clearLauncherCache() }
                            )
                        }
                    }
                }
            }
        }
    }

    preflightReport?.let { report ->
        LaunchPreflightSheet(
            report = report,
            onDismiss = { viewModel.dismissPreflightReport() },
            onOpenGameViewport = { nativeReady ->
                viewModel.dismissPreflightReport()
                if (report.instanceId.isNotBlank()) {
                    viewModel.markLaunchedInHistory(report.instanceId)
                }
                val intent = GameActivity.createIntent(
                    context = context,
                    instanceName = report.instanceName,
                    mcVersion = report.minecraftVersion,
                    accountName = report.accountName,
                    nativeReady = nativeReady,
                    jvmCmdPreview = report.jvmCommandLinePreview.joinToString(" "),
                    missingLibs = report.missingNativeComponents.joinToString(", "),
                    renderer = settings.rendererBackend.id,
                    showFps = settings.showFpsOverlay,
                    enableAudio = settings.enableAudio
                ).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(intent)
            },
            onGoToVersions = { navigateTo(CraftDestination.Versions.route) },
            onGoToRuntimes = { navigateTo(CraftDestination.Settings.route) }
        )
    }
}
