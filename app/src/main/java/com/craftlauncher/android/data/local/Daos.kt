package com.craftlauncher.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY lastUsedAt DESC LIMIT 1")
    suspend fun getMostRecentAccount(): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccount(account: AccountEntity)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("UPDATE accounts SET lastUsedAt = :timestamp WHERE id = :id")
    suspend fun touchAccount(id: String, timestamp: Long)

    @Query("UPDATE accounts SET selectedInstanceId = :instanceId WHERE id = :accountId")
    suspend fun updateSelectedInstance(accountId: String, instanceId: String)
}

@Dao
interface InstanceDao {
    @Query("SELECT * FROM instances ORDER BY COALESCE(lastPlayedAt, 0) DESC, name ASC")
    fun observeAllInstances(): Flow<List<GameInstanceEntity>>

    @Query("SELECT * FROM instances WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 6")
    fun observeRecentlyPlayed(): Flow<List<GameInstanceEntity>>

    @Query("SELECT * FROM instances WHERE id = :id LIMIT 1")
    suspend fun getInstanceById(id: String): GameInstanceEntity?

    @Query("SELECT * FROM instances LIMIT 1")
    suspend fun getFirstInstance(): GameInstanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstance(instance: GameInstanceEntity)

    @Query("DELETE FROM instances WHERE id = :id")
    suspend fun deleteInstanceById(id: String)
}

@Dao
interface JavaRuntimeDao {
    @Query("SELECT * FROM java_runtimes ORDER BY majorVersion DESC")
    fun observeAllRuntimes(): Flow<List<JavaRuntimeEntity>>

    @Query("SELECT * FROM java_runtimes WHERE id = :id LIMIT 1")
    suspend fun getRuntimeById(id: String): JavaRuntimeEntity?

    @Query("SELECT * FROM java_runtimes WHERE majorVersion = :majorVersion AND isInstalled = 1 LIMIT 1")
    suspend fun getInstalledByMajor(majorVersion: Int): JavaRuntimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRuntime(runtime: JavaRuntimeEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(runtimes: List<JavaRuntimeEntity>)
}
