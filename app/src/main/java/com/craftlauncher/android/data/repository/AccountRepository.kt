package com.craftlauncher.android.data.repository

import android.content.Context
import android.net.Uri
import com.craftlauncher.android.data.local.AccountDao
import com.craftlauncher.android.data.local.AccountEntity
import com.craftlauncher.android.data.local.CryptoHelper
import com.craftlauncher.android.data.local.LauncherPreferences
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.AvatarPreset
import com.craftlauncher.android.data.remote.MicrosoftAuthService
import com.craftlauncher.android.data.remote.MicrosoftMinecraftSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AccountRepository(
    private val context: Context,
    private val accountDao: AccountDao,
    private val preferences: LauncherPreferences,
    private val microsoftAuthService: MicrosoftAuthService
) {
    val accountsFlow: Flow<List<AccountEntity>> = accountDao.observeAllAccounts()

    private val avatarsDir: File
        get() = File(context.filesDir, "craftlauncher/avatars").apply { mkdirs() }

    fun generateOfflineUuid(username: String): String {
        return UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()
    }

    suspend fun createLocalProfile(
        username: String,
        avatarUri: String?,
        selectedInstanceId: String? = null
    ): AccountEntity = withContext(Dispatchers.IO) {
        val cleanName = username.trim().take(16)
        require(cleanName.length >= 2) { "Username must be at least 2 characters." }
        val id = "local-${UUID.randomUUID()}"
        val persistedAvatar = persistAvatarIfContentUri(id, avatarUri)
        val account = AccountEntity(
            id = id,
            type = AccountType.LOCAL,
            username = cleanName,
            uuid = generateOfflineUuid(cleanName),
            avatarUri = persistedAvatar ?: AvatarPreset.STEVE.key,
            ownsMinecraft = false,
            selectedInstanceId = selectedInstanceId,
            lastUsedAt = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        accountDao.upsertAccount(account)
        preferences.setActiveAccountId(account.id)
        account
    }

    suspend fun updateLocalProfile(
        accountId: String,
        newUsername: String,
        newAvatarUri: String?,
        preferredRamMb: Int?
    ): AccountEntity = withContext(Dispatchers.IO) {
        val existing = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Profile not found.")
        val cleanName = newUsername.trim().take(16)
        require(cleanName.length >= 2) { "Username must be at least 2 characters." }
        val persistedAvatar = persistAvatarIfContentUri(accountId, newAvatarUri) ?: existing.avatarUri
        val updated = existing.copy(
            username = cleanName,
            uuid = if (existing.type == AccountType.LOCAL) generateOfflineUuid(cleanName) else existing.uuid,
            avatarUri = persistedAvatar,
            preferredRamMb = preferredRamMb,
            lastUsedAt = System.currentTimeMillis()
        )
        accountDao.upsertAccount(updated)
        updated
    }

    suspend fun saveMicrosoftSession(
        session: MicrosoftMinecraftSession,
        selectedInstanceId: String?
    ): AccountEntity = withContext(Dispatchers.IO) {
        val id = "ms-${session.uuid}"
        val existing = accountDao.getAccountById(id)
        val account = AccountEntity(
            id = id,
            type = AccountType.MICROSOFT,
            username = session.username,
            uuid = session.uuid,
            avatarUri = existing?.avatarUri ?: "https://mc-heads.net/avatar/${session.uuid.replace("-", "")}/128",
            encryptedMsAccessToken = CryptoHelper.encrypt(session.msAccessToken),
            encryptedMsRefreshToken = CryptoHelper.encrypt(session.msRefreshToken),
            encryptedMcAccessToken = CryptoHelper.encrypt(session.mcAccessToken),
            tokenExpiresAt = session.expiresAtMillis,
            ownsMinecraft = session.ownsMinecraft,
            selectedInstanceId = existing?.selectedInstanceId ?: selectedInstanceId,
            preferredRamMb = existing?.preferredRamMb,
            lastUsedAt = System.currentTimeMillis(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        accountDao.upsertAccount(account)
        preferences.setActiveAccountId(account.id)
        account
    }

    suspend fun refreshMicrosoftAccountIfNeeded(
        account: AccountEntity,
        clientId: String
    ): Result<AccountEntity> = withContext(Dispatchers.IO) {
        if (account.type != AccountType.MICROSOFT) return@withContext Result.success(account)
        val now = System.currentTimeMillis()
        if (account.tokenExpiresAt > now + 5 * 60_000L && !account.encryptedMcAccessToken.isNullOrBlank()) {
            return@withContext Result.success(account)
        }
        val refreshToken = CryptoHelper.decrypt(account.encryptedMsRefreshToken)
        if (refreshToken.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Microsoft refresh token unavailable. Please sign in again."))
        }
        return@withContext runCatching {
            val session = microsoftAuthService.refreshSession(clientId, refreshToken)
            saveMicrosoftSession(session, account.selectedInstanceId)
        }
    }

    suspend fun switchAccount(accountId: String) {
        val now = System.currentTimeMillis()
        accountDao.touchAccount(accountId, now)
        preferences.setActiveAccountId(accountId)
        val acc = accountDao.getAccountById(accountId)
        if (!acc?.selectedInstanceId.isNullOrBlank()) {
            preferences.setActiveInstanceId(acc?.selectedInstanceId)
        }
    }

    suspend fun bindInstanceToActiveAccount(accountId: String?, instanceId: String) {
        preferences.setActiveInstanceId(instanceId)
        if (!accountId.isNullOrBlank()) {
            accountDao.updateSelectedInstance(accountId, instanceId)
        }
    }

    suspend fun deleteAccount(accountId: String, currentActiveId: String?) = withContext(Dispatchers.IO) {
        accountDao.deleteAccountById(accountId)
        File(avatarsDir, "$accountId.png").delete()
        if (currentActiveId == accountId) {
            val next = accountDao.getMostRecentAccount()
            preferences.setActiveAccountId(next?.id)
        }
    }

    fun decryptMcToken(account: AccountEntity): String? =
        CryptoHelper.decrypt(account.encryptedMcAccessToken)

    private fun persistAvatarIfContentUri(accountId: String, rawUri: String?): String? {
        if (rawUri.isNullOrBlank()) return null
        if (rawUri.startsWith("preset:") || rawUri.startsWith("http") || rawUri.startsWith("file:")) {
            return rawUri
        }
        return try {
            val parsed = Uri.parse(rawUri)
            val destFile = File(avatarsDir, "$accountId.png")
            context.contentResolver.openInputStream(parsed)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0L) Uri.fromFile(destFile).toString() else null
        } catch (_: Throwable) {
            null
        }
    }
}
