package com.craftlauncher.android.data.remote

import com.craftlauncher.android.data.model.MojangVersionSummary
import org.json.JSONObject

data class MojangManifestResult(
    val latestRelease: String,
    val latestSnapshot: String,
    val versions: List<MojangVersionSummary>,
    val fromCache: Boolean
)

data class MojangDownloadArtifact(
    val url: String,
    val sha1: String,
    val size: Long,
    val path: String
)

data class MojangVersionDetails(
    val id: String,
    val type: String,
    val mainClass: String,
    val requiredJavaMajor: Int,
    val clientJar: MojangDownloadArtifact,
    val assetIndexId: String,
    val assetIndexUrl: String,
    val assetIndexSha1: String,
    val assetIndexSize: Long,
    val libraries: List<MojangDownloadArtifact>
)

data class FabricLoaderOption(
    val loaderVersion: String,
    val stable: Boolean
)

object MojangJsonParser {

    fun parseManifest(jsonStr: String, fromCache: Boolean): MojangManifestResult {
        val root = JSONObject(jsonStr)
        val latest = root.optJSONObject("latest")
        val latestRelease = latest?.optString("release").orEmpty()
        val latestSnapshot = latest?.optString("snapshot").orEmpty()
        val versionsArr = root.optJSONArray("versions")
        val versions = buildList {
            if (versionsArr != null) {
                for (i in 0 until versionsArr.length()) {
                    val obj = versionsArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val type = obj.optString("type")
                    val url = obj.optString("url")
                    val releaseTime = obj.optString("releaseTime")
                    val sha1 = obj.optString("sha1")
                    if (id.isNotBlank() && url.isNotBlank()) {
                        add(
                            MojangVersionSummary(
                                id = id,
                                type = type,
                                url = url,
                                releaseTime = releaseTime,
                                sha1 = sha1
                            )
                        )
                    }
                }
            }
        }
        return MojangManifestResult(
            latestRelease = latestRelease,
            latestSnapshot = latestSnapshot,
            versions = versions,
            fromCache = fromCache
        )
    }

    fun parseVersionDetails(jsonStr: String): MojangVersionDetails {
        val root = JSONObject(jsonStr)
        val id = root.getString("id")
        val type = root.optString("type", "release")
        val mainClass = root.optString("mainClass", "net.minecraft.client.main.Main")
        val javaMajor = root.optJSONObject("javaVersion")?.optInt("majorVersion", 8) ?: 8

        val downloads = root.getJSONObject("downloads")
        val clientObj = downloads.getJSONObject("client")
        val clientArtifact = MojangDownloadArtifact(
            url = clientObj.getString("url"),
            sha1 = clientObj.optString("sha1", ""),
            size = clientObj.optLong("size", 0L),
            path = "$id/$id.jar"
        )

        val assetIndex = root.getJSONObject("assetIndex")
        val assetIndexId = assetIndex.getString("id")
        val assetIndexUrl = assetIndex.getString("url")
        val assetIndexSha1 = assetIndex.optString("sha1", "")
        val assetIndexSize = assetIndex.optLong("size", 0L)

        val libsArray = root.optJSONArray("libraries")
        val libraries = buildList {
            if (libsArray != null) {
                for (i in 0 until libsArray.length()) {
                    val lib = libsArray.optJSONObject(i) ?: continue
                    if (!isRuleAllowedForLinuxAndroid(lib.optJSONArray("rules"))) continue
                    val libDownloads = lib.optJSONObject("downloads") ?: continue
                    val artifact = libDownloads.optJSONObject("artifact") ?: continue
                    val url = artifact.optString("url")
                    val path = artifact.optString("path")
                    if (url.isNotBlank() && path.isNotBlank()) {
                        add(
                            MojangDownloadArtifact(
                                url = url,
                                sha1 = artifact.optString("sha1", ""),
                                size = artifact.optLong("size", 0L),
                                path = path
                            )
                        )
                    }
                }
            }
        }

        return MojangVersionDetails(
            id = id,
            type = type,
            mainClass = mainClass,
            requiredJavaMajor = javaMajor,
            clientJar = clientArtifact,
            assetIndexId = assetIndexId,
            assetIndexUrl = assetIndexUrl,
            assetIndexSha1 = assetIndexSha1,
            assetIndexSize = assetIndexSize,
            libraries = libraries
        )
    }

    private fun isRuleAllowedForLinuxAndroid(rules: org.json.JSONArray?): Boolean {
        if (rules == null || rules.length() == 0) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            val action = rule.optString("action")
            val os = rule.optJSONObject("os")
            val matches = if (os == null) {
                true
            } else {
                val osName = os.optString("name")
                osName == "linux"
            }
            if (matches) {
                allowed = (action == "allow")
            }
        }
        return allowed
    }
}
