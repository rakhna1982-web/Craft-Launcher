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

        fun createRuntimeEntryForMajor(context: Context, majorVersion: Int): JavaRuntimeEntity {
            val abi = detectPrimaryAbi()
            val runtimesRoot = File(context.filesDir, "craftlauncher/runtimes")
            val archSlug = when (abi) {
                "arm64-v8a" -> "aarch64"
                "armeabi-v7a" -> "arm"
                "x86_64" -> "x64"
                "x86" -> "x86-32"
                else -> "aarch64"
            }
            val (label, verStr, estSize) = when (majorVersion) {
                25 -> Triple("OpenJDK 25 (Latest / Snapshots)", "25.0.0+36", 48_000_000L)
                21 -> Triple("OpenJDK 21 (MC 1.20.5+)", "21.0.6+7", 46_500_000L)
                17 -> Triple("OpenJDK 17 (MC 1.18 - 1.20.4)", "17.0.14+7", 43_200_000L)
                8 -> Triple("OpenJDK 8 (MC 1.7.10 - 1.16.5)", "8u442-b06", 38_400_000L)
                else -> Triple("OpenJDK $majorVersion", "$majorVersion-latest", 45_000_000L)
            }
            return JavaRuntimeEntity(
                id = "jre-$majorVersion-$abi",
                name = label,
                majorVersion = majorVersion,
                versionString = verStr,
                architecture = abi,
                installPath = File(runtimesRoot, "jre$majorVersion-$abi").absolutePath,
                downloadUrl = "https://api.adoptium.net/v3/binary/latest/$majorVersion/ga/linux/$archSlug/jre/hotspot/normal/eclipse",
                sha256 = "",
                sizeBytes = estSize
            )
        }

        fun buildDefaultRuntimeCatalog(context: Context): List<JavaRuntimeEntity> {
            return listOf(25, 21, 17, 8).map { major ->
                createRuntimeEntryForMajor(context, major)
            }
        }
    }
}
