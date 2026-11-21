package com.craftlauncher.android

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.craftlauncher.android.data.remote.MojangJsonParser
import com.example.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CraftLauncherUnitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `opening local profile dialog and content and settings screens works with ActivityResultRegistryOwner`() {
        composeRule.onNodeWithTag("nav_accounts").performClick()
        composeRule.onNodeWithTag("add_local_profile_button").performClick()
        composeRule.onNodeWithTag("local_username_input").assertIsDisplayed()
        composeRule.onNodeWithTag("local_username_input").performTextInput("AlexTest")
        composeRule.onNodeWithTag("save_local_profile_button").performClick()

        // Also verify ContentScreen and SettingsScreen (which use rememberLauncherForActivityResult)
        composeRule.onNodeWithTag("nav_content").performClick()
        composeRule.onNodeWithTag("import_mod_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithText("Java Runtime Manager").assertIsDisplayed()
    }

    @Test
    fun `offline uuid matches standard Mojang OfflinePlayer v3 format`() {
        val username = "Steve"
        val expected = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()
        val app = ApplicationProvider.getApplicationContext<CraftLauncherApp>()
        val actual = app.container.accountRepository.generateOfflineUuid(username)
        assertEquals(expected, actual)
    }

    @Test
    fun `mojang manifest parser extracts releases and snapshots`() {
        val sampleJson = """
            {
              "latest": { "release": "1.21.4", "snapshot": "25w09b" },
              "versions": [
                {
                  "id": "1.21.4",
                  "type": "release",
                  "url": "https://piston-meta.mojang.com/v1/packages/abc/1.21.4.json",
                  "releaseTime": "2024-12-03T10:12:57+00:00",
                  "sha1": "deadbeef"
                },
                {
                  "id": "25w09b",
                  "type": "snapshot",
                  "url": "https://piston-meta.mojang.com/v1/packages/def/25w09b.json",
                  "releaseTime": "2025-02-26T12:00:00+00:00",
                  "sha1": "cafebabe"
                }
              ]
            }
        """.trimIndent()

        val result = MojangJsonParser.parseManifest(sampleJson, fromCache = false)
        assertEquals("1.21.4", result.latestRelease)
        assertEquals("25w09b", result.latestSnapshot)
        assertEquals(2, result.versions.size)
        assertFalse(result.fromCache)
        assertTrue(result.versions.any { it.id == "25w09b" && it.type == "snapshot" })
    }

    @Test
    fun `mojang version details parser extracts javaVersion and client jar`() {
        val sampleVersionJson = """
            {
              "id": "1.21.4",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": { "majorVersion": 21 },
              "assetIndex": {
                "id": "19",
                "sha1": "1111",
                "size": 1234,
                "url": "https://piston-meta.mojang.com/v1/packages/19/19.json"
              },
              "downloads": {
                "client": {
                  "sha1": "2222",
                  "size": 25000000,
                  "url": "https://piston-meta.mojang.com/v1/objects/2222/client.jar"
                }
              },
              "libraries": []
            }
        """.trimIndent()

        val details = MojangJsonParser.parseVersionDetails(sampleVersionJson)
        assertEquals("1.21.4", details.id)
        assertEquals(21, details.requiredJavaMajor)
        assertEquals("net.minecraft.client.main.Main", details.mainClass)
        assertEquals("19", details.assetIndexId)
        assertEquals("2222", details.clientJar.sha1)
    }

    @Test
    fun `default runtime catalog includes Java 25 alongside 21 17 and 8`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtimes = com.craftlauncher.android.data.local.CraftDatabase.buildDefaultRuntimeCatalog(context)
        val majors = runtimes.map { it.majorVersion }
        assertTrue(majors.containsAll(listOf(25, 21, 17, 8)))
        val java25 = runtimes.first { it.majorVersion == 25 }
        assertTrue(java25.downloadUrl.contains("/25/ga/linux/"))
    }
}
