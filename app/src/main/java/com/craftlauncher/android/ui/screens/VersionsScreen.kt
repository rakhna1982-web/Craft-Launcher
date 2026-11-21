package com.craftlauncher.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.data.model.ModLoaderType
import com.craftlauncher.android.data.model.MojangVersionSummary
import com.craftlauncher.android.data.model.UiAsyncState
import com.craftlauncher.android.data.remote.FabricLoaderOption
import com.craftlauncher.android.data.remote.MojangManifestResult
import com.craftlauncher.android.ui.components.StatusPill
import com.craftlauncher.android.ui.components.formatBytes
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import com.craftlauncher.android.ui.theme.RedstoneError
import com.example.R

@Composable
fun VersionsScreen(
    instances: List<GameInstanceEntity>,
    activeInstanceId: String?,
    manifestState: UiAsyncState<MojangManifestResult>,
    fabricLoaders: List<FabricLoaderOption>,
    onRefreshManifest: () -> Unit,
    onSelectInstance: (String) -> Unit,
    onInstallInstance: (GameInstanceEntity) -> Unit,
    onDeleteInstance: (String) -> Unit,
    onLoadFabricLoaders: (String) -> Unit,
    onCreateInstance: (name: String, version: MojangVersionSummary, loader: ModLoaderType, loaderVer: String?, installNow: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("release") }
    var versionPendingCreation by remember { mutableStateOf<MojangVersionSummary?>(null) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My Instances (${instances.size})") },
                    modifier = Modifier.testTag("tab_my_instances")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Mojang Catalog") },
                    modifier = Modifier.testTag("tab_mojang_catalog")
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
                            Text(
                                text = "Separate Game Instances",
                                style = MaterialTheme.typography.titleMedium
                            )
                            OutlinedButton(onClick = { selectedTab = 1 }) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Instance")
                            }
                        }
                    }

                    items(instances, key = { it.id }) { inst ->
                        val isActive = inst.id == activeInstanceId
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            border = BorderStroke(
                                if (isActive) 1.5.dp else 1.dp,
                                if (isActive) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectInstance(inst.id) }
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = inst.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isActive) {
                                                Icon(
                                                    Icons.Rounded.CheckCircle,
                                                    contentDescription = "Active instance",
                                                    tint = EmeraldAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "MC ${inst.minecraftVersion} (${inst.versionType}) • ${inst.modLoader.displayName}${inst.modLoaderVersion?.let { " $it" }.orEmpty()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (instances.size > 1) {
                                        IconButton(onClick = { onDeleteInstance(inst.id) }) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete instance",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusPill(
                                        text = "Java ${inst.requiredJavaMajor}",
                                        color = DiamondCyan
                                    )
                                    StatusPill(
                                        text = inst.installStatus.name,
                                        color = when (inst.installStatus) {
                                            InstallStatus.INSTALLED -> EmeraldAccent
                                            InstallStatus.DOWNLOADING -> DiamondCyan
                                            InstallStatus.ERROR -> RedstoneError
                                            InstallStatus.NOT_INSTALLED -> AmberSnapshot
                                        }
                                    )
                                    if (inst.totalSizeBytes > 0L) {
                                        Text(
                                            text = formatBytes(inst.totalSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (inst.installStatus == InstallStatus.DOWNLOADING) {
                                    LinearProgressIndicator(
                                        progress = { inst.installProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                    )
                                }
                                Text(
                                    text = inst.installStage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!isActive) {
                                        OutlinedButton(
                                            onClick = { onSelectInstance(inst.id) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Set Active")
                                        }
                                    }
                                    Button(
                                        onClick = { onInstallInstance(inst) },
                                        enabled = inst.installStatus != InstallStatus.DOWNLOADING,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            if (inst.installStatus == InstallStatus.INSTALLED) {
                                                "Verify / Reinstall"
                                            } else {
                                                stringResource(R.string.action_install)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search version (e.g. 1.21.4, 25w)") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("version_search_input")
                        )
                        IconButton(onClick = onRefreshManifest) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Mojang manifest")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "release" to "Releases",
                            "snapshot" to "Snapshots",
                            "old" to "Beta/Alpha",
                            "all" to "All"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = typeFilter == key,
                                onClick = { typeFilter = key },
                                label = { Text(label) }
                            )
                        }
                    }

                    when (manifestState) {
                        is UiAsyncState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(color = EmeraldAccent)
                                    Text("Loading Mojang piston-meta manifest…")
                                }
                            }
                        }
                        is UiAsyncState.Error -> {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = RedstoneError.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, RedstoneError.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Could not fetch Mojang version list",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = RedstoneError
                                    )
                                    Text(
                                        text = manifestState.message,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Button(onClick = onRefreshManifest) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                        is UiAsyncState.Success -> {
                            val manifest = manifestState.data
                            if (manifest.fromCache) {
                                Text(
                                    text = "Showing cached Mojang manifest (${manifest.versions.size} versions) • Tap refresh for latest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AmberSnapshot
                                )
                            }
                            val filtered = remember(manifest.versions, searchQuery, typeFilter) {
                                manifest.versions.filter { v ->
                                    val matchesType = when (typeFilter) {
                                        "release" -> v.type == "release"
                                        "snapshot" -> v.type == "snapshot"
                                        "old" -> v.type == "old_beta" || v.type == "old_alpha"
                                        else -> true
                                    }
                                    val matchesQuery = searchQuery.isBlank() ||
                                        v.id.contains(searchQuery.trim(), ignoreCase = true)
                                    matchesType && matchesQuery
                                }
                            }
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(filtered, key = { it.id }) { ver ->
                                    Surface(
                                        shape = MaterialTheme.shapes.medium,
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onLoadFabricLoaders(ver.id)
                                                versionPendingCreation = ver
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = ver.id,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                    StatusPill(
                                                        text = ver.type,
                                                        color = if (ver.type == "release") EmeraldAccent else AmberSnapshot
                                                    )
                                                }
                                                Text(
                                                    text = "Released ${ver.releaseTime.take(10)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    onLoadFabricLoaders(ver.id)
                                                    versionPendingCreation = ver
                                                }
                                            ) {
                                                Text("Create")
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
    }

    versionPendingCreation?.let { targetVer ->
        CreateInstanceDialog(
            version = targetVer,
            fabricLoaders = fabricLoaders,
            onDismiss = { versionPendingCreation = null },
            onConfirm = { name, loader, loaderVer, installNow ->
                onCreateInstance(name, targetVer, loader, loaderVer, installNow)
                versionPendingCreation = null
                selectedTab = 0
            }
        )
    }
}

@Composable
private fun CreateInstanceDialog(
    version: MojangVersionSummary,
    fabricLoaders: List<FabricLoaderOption>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, loader: ModLoaderType, loaderVer: String?, installNow: Boolean) -> Unit
) {
    var instanceName by remember { mutableStateOf("Minecraft ${version.id}") }
    var selectedLoader by remember { mutableStateOf(ModLoaderType.VANILLA) }
    var installImmediately by remember { mutableStateOf(true) }

    val selectedFabricVer = if (selectedLoader == ModLoaderType.FABRIC) {
        fabricLoaders.firstOrNull { it.stable }?.loaderVersion ?: fabricLoaders.firstOrNull()?.loaderVersion
    } else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Instance (${version.id})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = instanceName,
                    onValueChange = { instanceName = it },
                    label = { Text("Instance Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Mod Loader", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(ModLoaderType.VANILLA, ModLoaderType.FABRIC, ModLoaderType.FORGE).forEach { loader ->
                        FilterChip(
                            selected = selectedLoader == loader,
                            onClick = { selectedLoader = loader },
                            label = { Text(loader.displayName) }
                        )
                    }
                }
                if (selectedLoader == ModLoaderType.FABRIC) {
                    Text(
                        text = if (fabricLoaders.isNotEmpty()) {
                            "Fabric Loader ${selectedFabricVer ?: ""} detected via meta.fabricmc.net"
                        } else {
                            "Checking Fabric compatibility or no Fabric build for ${version.id}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DiamondCyan
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { installImmediately = !installImmediately }
                ) {
                    Checkbox(
                        checked = installImmediately,
                        onCheckedChange = { installImmediately = it }
                    )
                    Text("Download client.jar, libraries & assets now")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(instanceName, selectedLoader, selectedFabricVer, installImmediately)
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
