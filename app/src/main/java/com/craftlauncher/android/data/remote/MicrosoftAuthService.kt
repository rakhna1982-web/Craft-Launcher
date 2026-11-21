package com.craftlauncher.android.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class DeviceCodePrompt(
    val userCode: String,
    val deviceCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
    val message: String
)

data class MicrosoftMinecraftSession(
    val username: String,
    val uuid: String,
    val msAccessToken: String,
    val msRefreshToken: String,
    val mcAccessToken: String,
    val expiresAtMillis: Long,
    val ownsMinecraft: Boolean
)

sealed class MicrosoftAuthException(message: String) : IOException(message) {
    class MissingClientId : MicrosoftAuthException(
        "A Microsoft Azure OAuth Client ID is required to sign in via Microsoft Device Code Flow. Configure it in the Microsoft Login dialog or use a Local Offline Profile."
    )
    class NoXboxProfile : MicrosoftAuthException(
        "This Microsoft account does not have an Xbox Live profile yet. Sign in at xbox.com once to create a Gamertag."
    )
    class ChildAccountRestricted : MicrosoftAuthException(
        "This Xbox account belongs to a child/family group and must be added to a Microsoft Family or approved by an adult."
    )
    class NoMinecraftOwnership : MicrosoftAuthException(
        "This Microsoft account does not own Minecraft: Java Edition. CraftLauncher does not bypass ownership checks."
    )
    class UserCancelledOrExpired(reason: String) : MicrosoftAuthException(reason)
}

/**
 * Implements the official Microsoft -> Xbox Live -> XSTS -> Minecraft Services authentication chain:
 * 1. Microsoft OAuth 2.0 Device Code Flow (login.microsoftonline.com/consumers)
 * 2. Xbox Live Auth (user.auth.xboxlive.com/user/authenticate)
 * 3. XSTS Token (xsts.auth.xboxlive.com/xsts/authorize)
 * 4. Minecraft Login with Xbox (api.minecraftservices.com/authentication/login_with_xbox)
 * 5. Ownership Verification (api.minecraftservices.com/entitlements/mcstore)
 * 6. Java Profile Fetch (api.minecraftservices.com/minecraft/profile)
 */
class MicrosoftAuthService(
    private val httpClient: OkHttpClient = MojangApiClient.defaultHttpClient()
) {
    companion object {
        private const val MS_DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        private const val MS_TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        private const val XBL_AUTH_URL =
            "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_AUTH_URL =
            "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MC_LOGIN_XBOX_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val MC_ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore"
        private const val MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile"

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val OAUTH_SCOPE = "XboxLive.signin offline_access"
    }

    suspend fun requestDeviceCode(clientId: String): DeviceCodePrompt = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) throw MicrosoftAuthException.MissingClientId()

        val form = FormBody.Builder()
            .add("client_id", clientId.trim())
            .add("scope", OAUTH_SCOPE)
            .build()

        val request = Request.Builder()
            .url(MS_DEVICE_CODE_URL)
            .post(form)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = JSONObject(raw.ifBlank { "{}" })
            if (!response.isSuccessful) {
                val desc = json.optString("error_description", "HTTP ${response.code}")
                throw IOException("Microsoft device code request failed: $desc")
            }
            DeviceCodePrompt(
                userCode = json.getString("user_code"),
                deviceCode = json.getString("device_code"),
                verificationUri = json.optString("verification_uri", "https://microsoft.com/link"),
                expiresInSeconds = json.optInt("expires_in", 900),
                intervalSeconds = json.optInt("interval", 5).coerceAtLeast(3),
                message = json.optString("message", "")
            )
        }
    }

    suspend fun pollForMicrosoftTokenAndCompleteChain(
        clientId: String,
        prompt: DeviceCodePrompt,
        onStatusUpdate: (String) -> Unit
    ): MicrosoftMinecraftSession = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + prompt.expiresInSeconds * 1000L
        var intervalMs = prompt.intervalSeconds * 1000L

        var msAccessToken = ""
        var msRefreshToken = ""

        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val form = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", clientId.trim())
                .add("device_code", prompt.deviceCode)
                .build()

            val request = Request.Builder().url(MS_TOKEN_URL).post(form).build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                val json = JSONObject(raw.ifBlank { "{}" })
                if (response.isSuccessful) {
                    msAccessToken = json.getString("access_token")
                    msRefreshToken = json.optString("refresh_token", "")
                } else {
                    when (val err = json.optString("error")) {
                        "authorization_pending" -> Unit
                        "slow_down" -> intervalMs += 2000L
                        "expired_token" -> throw MicrosoftAuthException.UserCancelledOrExpired(
                            "Device code expired before sign-in completed."
                        )
                        "authorization_declined" -> throw MicrosoftAuthException.UserCancelledOrExpired(
                            "Microsoft sign-in was declined."
                        )
                        else -> throw IOException(
                            json.optString("error_description", "Microsoft OAuth error: $err")
                        )
                    }
                }
            }
            if (msAccessToken.isNotBlank()) break
        }

        if (msAccessToken.isBlank()) {
            throw MicrosoftAuthException.UserCancelledOrExpired("Microsoft sign-in timed out.")
        }

        completeXboxAndMinecraftChain(msAccessToken, msRefreshToken, onStatusUpdate)
    }

    suspend fun refreshSession(
        clientId: String,
        refreshToken: String,
        onStatusUpdate: (String) -> Unit = {}
    ): MicrosoftMinecraftSession = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) throw MicrosoftAuthException.MissingClientId()
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId.trim())
            .add("refresh_token", refreshToken)
            .add("scope", OAUTH_SCOPE)
            .build()

        val request = Request.Builder().url(MS_TOKEN_URL).post(form).build()
        val (newMsAccess, newMsRefresh) = httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = JSONObject(raw.ifBlank { "{}" })
            if (!response.isSuccessful) {
                throw IOException(json.optString("error_description", "Token refresh failed (HTTP ${response.code})"))
            }
            json.getString("access_token") to json.optString("refresh_token", refreshToken)
        }
        completeXboxAndMinecraftChain(newMsAccess, newMsRefresh, onStatusUpdate)
    }

    private suspend fun completeXboxAndMinecraftChain(
        msAccessToken: String,
        msRefreshToken: String,
        onStatusUpdate: (String) -> Unit
    ): MicrosoftMinecraftSession = withContext(Dispatchers.IO) {
        onStatusUpdate("Authenticating with Xbox Live…")
        val (xblToken, userHash) = authenticateXboxLive(msAccessToken)

        onStatusUpdate("Obtaining XSTS authorization…")
        val xstsToken = authorizeXsts(xblToken)

        onStatusUpdate("Logging into Minecraft Services…")
        val (mcToken, expiresInSec) = loginMinecraftWithXbox(userHash, xstsToken)

        onStatusUpdate("Verifying Minecraft: Java Edition ownership…")
        val ownsMc = checkEntitlements(mcToken)
        if (!ownsMc) {
            throw MicrosoftAuthException.NoMinecraftOwnership()
        }

        onStatusUpdate("Loading Minecraft Java profile…")
        val (uuid, username) = fetchMinecraftProfile(mcToken)

        MicrosoftMinecraftSession(
            username = username,
            uuid = formatDashedUuid(uuid),
            msAccessToken = msAccessToken,
            msRefreshToken = msRefreshToken,
            mcAccessToken = mcToken,
            expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L),
            ownsMinecraft = true
        )
    }

    private fun authenticateXboxLive(msAccessToken: String): Pair<String, String> {
        val payload = JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("AuthMethod", "RPS")
                put("SiteName", "user.auth.xboxlive.com")
                put("RpsTicket", "d=$msAccessToken")
            })
            put("RelyingParty", "http://auth.xboxlive.com")
            put("TokenType", "JWT")
        }
        val req = Request.Builder()
            .url(XBL_AUTH_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Xbox Live auth failed (HTTP ${resp.code})")
            val json = JSONObject(raw)
            val token = json.getString("Token")
            val uhs = json.getJSONObject("DisplayClaims")
                .getJSONArray("xui")
                .getJSONObject(0)
                .getString("uhs")
            return token to uhs
        }
    }

    private fun authorizeXsts(xblToken: String): String {
        val payload = JSONObject().apply {
            put("Properties", JSONObject().apply {
                put("SandboxId", "RETAIL")
                put("UserTokens", JSONArray().put(xblToken))
            })
            put("RelyingParty", "rp://api.minecraftservices.com/")
            put("TokenType", "JWT")
        }
        val req = Request.Builder()
            .url(XSTS_AUTH_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val json = JSONObject(raw.ifBlank { "{}" })
            if (!resp.isSuccessful) {
                val xErr = json.optLong("XErr", 0L)
                when (xErr) {
                    2148916233L -> throw MicrosoftAuthException.NoXboxProfile()
                    2148916238L -> throw MicrosoftAuthException.ChildAccountRestricted()
                    else -> throw IOException("XSTS authorization failed (HTTP ${resp.code}, XErr=$xErr)")
                }
            }
            return json.getString("Token")
        }
    }

    private fun loginMinecraftWithXbox(userHash: String, xstsToken: String): Pair<String, Long> {
        val payload = JSONObject().apply {
            put("identityToken", "XBL3.0 x=$userHash;$xstsToken")
        }
        val req = Request.Builder()
            .url(MC_LOGIN_XBOX_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Minecraft login failed (HTTP ${resp.code})")
            val json = JSONObject(raw)
            return json.getString("access_token") to json.optLong("expires_in", 86400L)
        }
    }

    private fun checkEntitlements(mcAccessToken: String): Boolean {
        val req = Request.Builder()
            .url(MC_ENTITLEMENTS_URL)
            .get()
            .header("Authorization", "Bearer $mcAccessToken")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return false
            val raw = resp.body?.string().orEmpty()
            val items = JSONObject(raw).optJSONArray("items") ?: return false
            for (i in 0 until items.length()) {
                val name = items.optJSONObject(i)?.optString("name").orEmpty()
                if (name == "product_minecraft" || name == "game_minecraft") {
                    return true
                }
            }
            return false
        }
    }

    private fun fetchMinecraftProfile(mcAccessToken: String): Pair<String, String> {
        val req = Request.Builder()
            .url(MC_PROFILE_URL)
            .get()
            .header("Authorization", "Bearer $mcAccessToken")
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (resp.code == 404) throw MicrosoftAuthException.NoMinecraftOwnership()
            if (!resp.isSuccessful) throw IOException("Profile fetch failed (HTTP ${resp.code})")
            val json = JSONObject(resp.body?.string().orEmpty())
            return json.getString("id") to json.getString("name")
        }
    }

    private fun formatDashedUuid(raw: String): String {
        val clean = raw.replace("-", "")
        if (clean.length != 32) return raw
        return "${clean.substring(0, 8)}-${clean.substring(8, 12)}-${clean.substring(12, 16)}-${clean.substring(16, 20)}-${clean.substring(20)}"
    }
}
