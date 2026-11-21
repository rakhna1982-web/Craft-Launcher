package com.craftlauncher.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.data.model.ModLoaderType

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val type: AccountType,
    val username: String,
    val uuid: String,
    val avatarUri: String? = null,
    val encryptedMsAccessToken: String? = null,
    val encryptedMsRefreshToken: String? = null,
    val encryptedMcAccessToken: String? = null,
    val tokenExpiresAt: Long = 0L,
    val ownsMinecraft: Boolean = false,
    val selectedInstanceId: String? = null,
    val preferredRamMb: Int? = null,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "instances")
data class GameInstanceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val minecraftVersion: String,
    val versionType: String,
    val modLoader: ModLoaderType = ModLoaderType.VANILLA,
    val modLoaderVersion: String? = null,
    val requiredJavaMajor: Int = 21,
    val selectedJavaRuntimeId: String? = null,
    val installStatus: InstallStatus = InstallStatus.NOT_INSTALLED,
    val installProgress: Float = 0f,
    val installStage: String = "",
    val totalSizeBytes: Long = 0L,
    val gameDirPath: String,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val customJvmArgs: String = "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions"
)

@Entity(tableName = "java_runtimes")
data class JavaRuntimeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val majorVersion: Int,
    val versionString: String,
    val architecture: String,
    val installPath: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val isInstalled: Boolean = false,
    val isVerified: Boolean = false,
    val lastVerifiedAt: Long? = null
)
