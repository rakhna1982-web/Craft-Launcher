package com.craftlauncher.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.craftlauncher.android.data.local.JavaRuntimeEntity
import com.craftlauncher.android.data.local.LauncherSettings
import com.craftlauncher.android.data.model.RendererBackend
import com.craftlauncher.android.data.model.StorageBreakdown
import com.craftlauncher.android.data.model.ThemeMode
import com.craftlauncher.android.ui.components.SectionHeader
import com.craftlauncher.android.ui.components.StatusPill
import com.craftlauncher.android.ui.components.formatBytes
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import com.craftlauncher.android.ui.viewmodel.RuntimeDownloadProgress
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    deviceAbi: String,
    maxRamMb: Int,
    settings: LauncherSettings,
    runtimes: List<JavaRuntimeEntity>,
    runtimeDownload: RuntimeDownloadProgress?,
    storageBreakdown: StorageBreakdown?,
    onUpdateRam: (Int) -> Unit,
    onUpdateResolution: (scale: Int, width: Int, height: Int) -> Unit,
    onUpdateJavaMode: (String) -> Unit,
    onUpdateTheme: (ThemeMode) -> Unit,
    onUpdateLanguage: (String) -> Unit,
    onUpdateRenderer: (RendererBackend) -> Unit,
    onUpdateTouchControls: (opacity: Float, scale: Float) -> Unit,
    onUpdateAudio: (Boolean) -> Unit,
    onUpdateShowFps: (Boolean) -> Unit,
    onDownloadRuntime: (String) -> Unit,
    onImportRuntimeArchive: (String, Uri) -> Unit,
    onVerifyRuntime: (String) -> Unit,
    onDeleteRuntime: (String) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingImportRuntimeId by remember { mutableStateOf<String?>(null) }
    val runtimeArchivePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val id = pendingImportRuntimeId
        if (uri != null && id != null) {
            onImportRuntimeArchive(id, uri)
        }
        pendingImportRuntimeId = null
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Java Runtime Manager
            item {
                SectionHeader(
                    title = "Java Runtime Manager",
                    subtitle = "Detected CPU ABI: $deviceAbi • Auto-matches Java 8, 17, 21, or 25 per MC version",
                    trailing = {
                        StatusPill(text = deviceAbi, color = DiamondCyan)
                    }
                )
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Java Version Selection", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Automatically picks Java 8, 17, or 21 from version JSON",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.defaultJavaRuntimeMode == "AUTO",
                                onCheckedChange = { auto ->
                                    if (auto) onUpdateJavaMode("AUTO")
                                    else runtimes.firstOrNull { it.isInstalled }?.let { onUpdateJavaMode(it.id) }
                                }
                            )
                        }

                        runtimes.forEach { rt ->
                            val isDownloadingThis = runtimeDownload?.runtimeId == rt.id
                            val isSelectedManual = settings.defaultJavaRuntimeMode == rt.id
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelectedManual) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(rt.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                text = "Java ${rt.majorVersion} (${rt.versionString}) • ${rt.architecture}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPill(
                                            text = when {
                                                rt.isVerified -> "Verified"
                                                rt.isInstalled -> "Installed"
                                                else -> "Not Installed"
                                            },
                                            color = when {
                                                rt.isVerified -> EmeraldAccent
                                                rt.isInstalled -> AmberSnapshot
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }

                                    if (isDownloadingThis && runtimeDownload != null) {
                                        LinearProgressIndicator(
                                            progress = { runtimeDownload.progress },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                        )
                                        Text(
                                            text = runtimeDownload.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DiamondCyan
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { onDownloadRuntime(rt.id) },
                                            enabled = runtimeDownload == null,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (rt.isInstalled) "Re-download" else "Download")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                pendingImportRuntimeId = rt.id
                                                runtimeArchivePicker.launch(arrayOf("*/*"))
                                            },
                                            enabled = runtimeDownload == null
                                        ) {
                                            Icon(Icons.Rounded.FolderOpen, contentDescription = "Import JRE archive", modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Import")
                                        }
                                        if (rt.isInstalled) {
                                            IconButton(onClick = { onVerifyRuntime(rt.id) }) {
                                                Icon(Icons.Rounded.Verified, contentDescription = "Verify runtime")
                                            }
                                            IconButton(onClick = { onDeleteRuntime(rt.id) }) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete runtime")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Memory & Graphics Settings
            item {
                SectionHeader(
                    title = "Memory, Resolution & Controls",
                    subtitle = "JVM heap size, OpenGL renderer backend, and HUD layout"
                )
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("RAM Allocation (-Xmx)", style = MaterialTheme.typography.titleMedium)
                            StatusPill(text = "${settings.ramAllocationMb} MB", color = EmeraldAccent)
                        }
                        Slider(
                            value = settings.ramAllocationMb.toFloat(),
                            onValueChange = { raw ->
                                val snapped = ((raw / 256f).roundToInt() * 256).coerceIn(512, maxRamMb)
                                onUpdateRam(snapped)
                            },
                            valueRange = 512f..maxRamMb.toFloat().coerceAtLeast(1024f),
                            modifier = Modifier.testTag("ram_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Resolution Scale", style = MaterialTheme.typography.titleMedium)
                            StatusPill(
                                text = "${settings.resolutionScalePercent}% (${settings.resolutionWidth}×${settings.resolutionHeight})",
                                color = DiamondCyan
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Triple(60, 960, 540) to "540p (60%)",
                                Triple(75, 1280, 720) to "720p (75%)",
                                Triple(90, 1600, 900) to "900p (90%)",
                                Triple(100, 1920, 1080) to "1080p (100%)"
                            ).forEach { (spec, label) ->
                                FilterChip(
                                    selected = settings.resolutionScalePercent == spec.first,
                                    onClick = { onUpdateResolution(spec.first, spec.second, spec.third) },
                                    label = { Text(label) }
                                )
                            }
                        }

                        Text("Renderer Backend", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RendererBackend.entries.forEach { backend ->
                                FilterChip(
                                    selected = settings.rendererBackend == backend,
                                    onClick = { onUpdateRenderer(backend) },
                                    label = { Text(backend.id) }
                                )
                            }
                        }
                        Text(
                            text = settings.rendererBackend.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Game Audio (OpenAL / AudioTrack)", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.enableAudio, onCheckedChange = onUpdateAudio)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Show FPS & Memory HUD in Viewport", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.showFpsOverlay, onCheckedChange = onUpdateShowFps)
                        }
                    }
                }
            }

            // 3. Appearance & Language
            item {
                SectionHeader(
                    title = "Theme & Language",
                    subtitle = "Customize launcher appearance and UI localization"
                )
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Launcher Theme", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { onUpdateTheme(mode) },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        Text("Language", style = MaterialTheme.typography.titleMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "en" to "English",
                                "es" to "Español",
                                "pt" to "Português",
                                "fr" to "Français",
                                "de" to "Deutsch",
                                "ja" to "日本語"
                            ).forEach { (code, name) ->
                                FilterChip(
                                    selected = settings.languageCode == code,
                                    onClick = { onUpdateLanguage(code) },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
            }

            // 4. Storage Management
            item {
                SectionHeader(
                    title = "Storage Management",
                    subtitle = "App-scoped game instances, assets, runtimes & cache"
                )
            }

            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sb = storageBreakdown
                        StorageRow("Instances & Versions", formatBytes(sb?.instancesBytes ?: 0L))
                        StorageRow("Libraries & Assets", formatBytes(sb?.assetsAndLibrariesBytes ?: 0L))
                        StorageRow("Java Runtimes", formatBytes(sb?.runtimesBytes ?: 0L))
                        StorageRow("Profile Avatars", formatBytes(sb?.avatarsBytes ?: 0L))
                        StorageRow("Manifest & Temp Cache", formatBytes(sb?.cacheBytes ?: 0L))

                        OutlinedButton(
                            onClick = onClearCache,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .testTag("clear_cache_button")
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Clear Temp Cache (${formatBytes(sb?.cacheBytes ?: 0L)})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
