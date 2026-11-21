package com.craftlauncher.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.model.ModItem
import com.craftlauncher.android.data.model.ModLoaderType
import com.craftlauncher.android.data.model.ResourcePackItem
import com.craftlauncher.android.ui.components.StatusPill
import com.craftlauncher.android.ui.components.formatBytes
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent

@Composable
fun ContentScreen(
    instances: List<GameInstanceEntity>,
    activeInstance: GameInstanceEntity?,
    mods: List<ModItem>,
    resourcePacks: List<ResourcePackItem>,
    onSelectInstance: (String) -> Unit,
    onImportMod: (Uri) -> Unit,
    onToggleMod: (ModItem) -> Unit,
    onDeleteMod: (ModItem) -> Unit,
    onImportResourcePack: (Uri) -> Unit,
    onToggleResourcePack: (ResourcePackItem) -> Unit,
    onDeleteResourcePack: (ResourcePackItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val modPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportMod) }

    val packPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportResourcePack) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Per-instance isolation selector
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(instances, key = { it.id }) { inst ->
                    FilterChip(
                        selected = inst.id == activeInstance?.id,
                        onClick = { onSelectInstance(inst.id) },
                        label = { Text("${inst.name} (${inst.modLoader.displayName})") }
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Mods (${mods.size})") },
                    modifier = Modifier.testTag("tab_mods")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Resource Packs (${resourcePacks.size})") },
                    modifier = Modifier.testTag("tab_resource_packs")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Instance Mods",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Isolated to ${activeInstance?.name ?: "instance"}/mods",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    modPicker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*"))
                                },
                                enabled = activeInstance != null,
                                modifier = Modifier.testTag("import_mod_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import .jar")
                            }
                        }
                    }

                    if (activeInstance?.modLoader == ModLoaderType.VANILLA) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = AmberSnapshot.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, AmberSnapshot.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Tip: This instance uses Vanilla. Create a Fabric or Forge instance in Versions to load .jar mods at runtime.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AmberSnapshot,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    if (mods.isEmpty()) {
                        item {
                            EmptyContentCard(
                                icon = Icons.Rounded.Extension,
                                title = "No mods in this instance",
                                message = "Tap 'Import .jar' to add Fabric or Forge mods. CraftLauncher reads fabric.mod.json and mods.toml automatically."
                            )
                        }
                    } else {
                        items(mods, key = { it.filePath }) { mod ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = mod.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            StatusPill(
                                                text = mod.loader.displayName,
                                                color = when (mod.loader) {
                                                    ModLoaderType.FABRIC -> DiamondCyan
                                                    ModLoaderType.FORGE, ModLoaderType.NEOFORGE -> AmberSnapshot
                                                    ModLoaderType.VANILLA -> EmeraldAccent
                                                }
                                            )
                                        }
                                        Text(
                                            text = "v${mod.version} • id: ${mod.modId} • ${formatBytes(mod.sizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (mod.description.isNotBlank()) {
                                            Text(
                                                text = mod.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = mod.isEnabled,
                                        onCheckedChange = { onToggleMod(mod) }
                                    )
                                    IconButton(onClick = { onDeleteMod(mod) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete mod ${mod.name}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Resource Packs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Synced to ${activeInstance?.name ?: "instance"}/options.txt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    packPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                },
                                enabled = activeInstance != null,
                                modifier = Modifier.testTag("import_pack_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Import .zip")
                            }
                        }
                    }

                    if (resourcePacks.isEmpty()) {
                        item {
                            EmptyContentCard(
                                icon = Icons.Rounded.Layers,
                                title = "No resource packs imported",
                                message = "Import a .zip resource pack to read its pack.mcmeta metadata and enable/disable it cleanly."
                            )
                        }
                    } else {
                        items(resourcePacks, key = { it.filePath }) { pack ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = pack.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            pack.packFormat?.let { fmt ->
                                                StatusPill(text = "Format $fmt", color = EmeraldAccent)
                                            }
                                        }
                                        Text(
                                            text = pack.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatBytes(pack.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = pack.isEnabled,
                                        onCheckedChange = { onToggleResourcePack(pack) }
                                    )
                                    IconButton(onClick = { onDeleteResourcePack(pack) }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Remove pack ${pack.displayName}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyContentCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
