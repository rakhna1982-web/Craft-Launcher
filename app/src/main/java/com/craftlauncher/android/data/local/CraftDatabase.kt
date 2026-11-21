package com.craftlauncher.android.data.local

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.data.model.ModLoaderType
import java.io.File

class CraftTypeConverters {
    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType =
        runCatching { AccountType.valueOf(value) }.getOrDefault(AccountType.LOCAL)

    @TypeConverter
    fun fromModLoaderType(value: ModLoaderType): String = value.name

    @TypeConverter
    fun toModLoaderType(value: String): ModLoaderType =
        runCatching { ModLoaderType.valueOf(value) }.getOrDefault(ModLoaderType.VANILLA)

    @TypeConverter
    fun fromInstallStatus(value: InstallStatus): String = value.name

    @TypeConverter
    fun toInstallStatus(value: String): InstallStatus =
        runCatching { InstallStatus.valueOf(value) }.getOrDefault(InstallStatus.NOT_INSTALLED)
}

@Database(
    entities = [AccountEntity::class, GameInstanceEntity::class, JavaRuntimeEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(CraftTypeConverters::class)
abstract class CraftDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun instanceDao(): InstanceDao
    abstract fun javaRuntimeDao(): JavaRuntimeDao

    companion object {
        @Volatile
        private var INSTANCE: CraftDatabase? = null

        fun getInstance(context: Context): CraftDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CraftDatabase::class.java,
                    "craftlauncher.db"
                ).build().also { INSTANCE = it }
            }
        }

        fun detectPrimaryAbi(): String {
            val abis = Build.SUPPORTED_ABIS ?: emptyArray()
            return when {
                abis.contains("arm64-v8a") -> "arm64-v8a"
                abis.contains("armeabi-v7a") -> "armeabi-v7a"
                abis.contains("x86_64") -> "x86_64"
                abis.contains("x86") -> "x86"
                else -> "arm64-v8a"
            }
        }

        fun buildDefaultRuntimeCatalog(context: Context): List<JavaRuntimeEntity> {
            val abi = detectPrimaryAbi()
            val runtimesRoot = File(context.filesDir, "craftlauncher/runtimes")
            val archSlug = when (abi) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x64"
                "x86" -> "x86-32"
                else -> "aarch64"
            }
            // Official Adoptium Temurin / OpenJDK API v3 Linux/Android-compatible tarball endpoints
            // Note: Android bionic execution requires OpenJDK Mobile builds (e.g. Pojav/Amethyst JRE)
            // or glibc/proot compat. Users can download directly or import a custom JRE archive.
            return listOf(
                JavaRuntimeEntity(
                    id = "jre-21-$abi",
                    name = "OpenJDK 21 (MC 1.20.5+)",
                    majorVersion = 21,
                    versionString = "21.0.6+7",
                    architecture = abi,
                    installPath = File(runtimesRoot, "jre21-$abi").absolutePath,
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/21/ga/linux/$archSlug/jre/hotspot/normal/eclipse",
                    sha256 = "",
                    sizeBytes = 46_500_000L
                ),
                JavaRuntimeEntity(
                    id = "jre-17-$abi",
                    name = "OpenJDK 17 (MC 1.18 - 1.20.4)",
                    majorVersion = 17,
                    versionString = "17.0.14+7",
                    architecture = abi,
                    installPath = File(runtimesRoot, "jre17-$abi").absolutePath,
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/17/ga/linux/$archSlug/jre/hotspot/normal/eclipse",
                    sha256 = "",
                    sizeBytes = 43_200_000L
                ),
                JavaRuntimeEntity(
                    id = "jre-8-$abi",
                    name = "OpenJDK 8 (MC 1.7.10 - 1.16.5)",
                    majorVersion = 8,
                    versionString = "8u442-b06",
                    architecture = abi,
                    installPath = File(runtimesRoot, "jre8-$abi").absolutePath,
                    downloadUrl = "https://api.adoptium.net/v3/binary/latest/8/ga/linux/$archSlug/jre/hotspot/normal/eclipse",
                    sha256 = "",
                    sizeBytes = 38_400_000L
                )
            )
        }
    }
}
