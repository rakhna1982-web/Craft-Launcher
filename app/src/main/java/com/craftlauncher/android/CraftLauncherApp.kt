package com.craftlauncher.android

import android.app.Application
import com.craftlauncher.android.data.local.CraftDatabase
import com.craftlauncher.android.data.local.LauncherPreferences
import com.craftlauncher.android.data.remote.MicrosoftAuthService
import com.craftlauncher.android.data.remote.MojangApiClient
import com.craftlauncher.android.data.repository.AccountRepository
import com.craftlauncher.android.data.repository.ContentRepository
import com.craftlauncher.android.data.repository.RuntimeRepository
import com.craftlauncher.android.data.repository.VersionRepository
import com.craftlauncher.android.engine.LaunchPreflightEngine

class AppContainer(app: Application) {
    val database: CraftDatabase by lazy { CraftDatabase.getInstance(app) }
    val preferences: LauncherPreferences by lazy { LauncherPreferences(app) }
    val mojangApiClient: MojangApiClient by lazy { MojangApiClient(app) }
    val microsoftAuthService: MicrosoftAuthService by lazy {
        MicrosoftAuthService(mojangApiClient.okHttpClient)
    }
    val accountRepository: AccountRepository by lazy {
        AccountRepository(
            context = app,
            accountDao = database.accountDao(),
            preferences = preferences,
            microsoftAuthService = microsoftAuthService
        )
    }
    val versionRepository: VersionRepository by lazy {
        VersionRepository(
            context = app,
            instanceDao = database.instanceDao(),
            mojangApiClient = mojangApiClient
        )
    }
    val runtimeRepository: RuntimeRepository by lazy {
        RuntimeRepository(
            context = app,
            runtimeDao = database.javaRuntimeDao(),
            mojangApiClient = mojangApiClient
        )
    }
    val contentRepository: ContentRepository by lazy { ContentRepository(app) }
    val launchPreflightEngine: LaunchPreflightEngine by lazy {
        LaunchPreflightEngine(
            context = app,
            versionRepository = versionRepository,
            runtimeRepository = runtimeRepository,
            accountRepository = accountRepository
        )
    }
}

class CraftLauncherApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
