package com.craftlauncher.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftlauncher.android.data.local.AccountEntity
import com.craftlauncher.android.data.local.GameInstanceEntity
import com.craftlauncher.android.data.local.JavaRuntimeEntity
import com.craftlauncher.android.data.local.LauncherSettings
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.InstallStatus
import com.craftlauncher.android.ui.components.AccountAvatar
import com.craftlauncher.android.ui.components.CraftBrandLogo
import com.craftlauncher.android.ui.components.SectionHeader
import com.craftlauncher.android.ui.components.StatusPill
import com.craftlauncher.android.ui.components.formatBytes
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import com.craftlauncher.android.ui.theme.EmeraldAction
import com.craftlauncher.android.ui.theme.RedstoneError
import com.example.R

@Composable
fun HomeScreen(
    activeAccount: AccountEntity?,
    activeInstance: GameInstanceEntity?,
    recentlyPlayed: List<GameInstanceEntity>,
    runtimes: List<JavaRuntimeEntity>,
    settings: LauncherSettings,
    modsCount: Int,
    packsCount: Int,
    onPlayClick: () -> Unit,
    onInstallInstance: (GameInstanceEntity) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenVersions: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenRuntimes: () -> Unit,
    onSelectRecentInstance: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CraftBrandLogo()
                    StatusPill(
                        text = "${settings.ramAllocationMb} MB • ${settings.rendererBackend.id}",
                        color = DiamondCyan
                    )
                }
            }

            // Hero Launcher Banner Card
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(172.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_launcher),
                            contentDescription = "CraftLauncher landscape banner",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.15f),
                                            Color(0xFF0B0E11).copy(alpha = 0.92f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPill(
                                    text = activeInstance?.modLoader?.displayName ?: "Vanilla",
                                    color = EmeraldAccent
                                )
                                StatusPill(
                                    text = "Java ${activeInstance?.requiredJavaMajor ?: 21}",
                                    color = DiamondCyan
                                )
                            }
                            Text(
                                text = activeInstance?.name ?: "Select a Minecraft Version",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeInstance?.let {
                                    "Minecraft ${it.minecraftVersion} • ${it.installStage}"
                                } ?: "Open Versions to create or install an instance",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Selected Account Card
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_switcher_card")
                        .clickable { onOpenAccounts() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeAccount != null) {
                            AccountAvatar(
                                avatarUri = activeAccount.avatarUri,
                                username = activeAccount.username,
                                size = 48.dp
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = activeAccount.username,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val isLocal = activeAccount.type == AccountType.LOCAL
                                    StatusPill(
                                        text = if (isLocal) {
                                            stringResource(R.string.label_local_offline)
                                        } else {
                                            stringResource(R.string.label_microsoft_licensed)
                                        },
                                        color = if (isLocal) AmberSnapshot else EmeraldAccent
                                    )
                                }
                                Text(
                                    text = if (activeAccount.type == AccountType.LOCAL) {
                                        "Offline single-player ready • UUID ${activeAccount.uuid.take(8)}…"
                                    } else {
                                        "Licensed Microsoft session • Online multiplayer enabled"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No Active Account",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Tap to create a Local Offline Profile or sign in with Microsoft",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            contentDescription = "Switch account",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Selected Version & Download Status + Primary PLAY Action
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .testTag("version_selector_card")
                                .clickable { onOpenVersions() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ACTIVE INSTANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = activeInstance?.let { "${it.name} (${it.minecraftVersion})" }
                                        ?: "No version selected",
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.action_switch),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = "Switch version",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Download / Installation Status Progress
                        if (activeInstance != null) {
                            val statusColor = when (activeInstance.installStatus) {
                                InstallStatus.INSTALLED -> EmeraldAccent
                                InstallStatus.DOWNLOADING -> DiamondCyan
                                InstallStatus.ERROR -> RedstoneError
                                InstallStatus.NOT_INSTALLED -> AmberSnapshot
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = activeInstance.installStage.ifBlank {
                                            activeInstance.installStatus.name
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (activeInstance.totalSizeBytes > 0L) {
                                        Text(
                                            text = formatBytes(activeInstance.totalSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (activeInstance.installStatus == InstallStatus.DOWNLOADING) {
                                    LinearProgressIndicator(
                                        progress = { activeInstance.installProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = DiamondCyan,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        // Large PLAY / INSTALL button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (activeInstance != null && activeInstance.installStatus != InstallStatus.INSTALLED) {
                                OutlinedButton(
                                    onClick = { onInstallInstance(activeInstance) },
                                    enabled = activeInstance.installStatus != InstallStatus.DOWNLOADING,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .testTag("home_install_button"),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (activeInstance.installStatus == InstallStatus.DOWNLOADING) {
                                            stringResource(R.string.status_downloading)
                                        } else {
                                            stringResource(R.string.action_install)
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            Button(
                                onClick = onPlayClick,
                                enabled = activeInstance != null && activeAccount != null,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(54.dp)
                                    .testTag("play_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldAction,
                                    contentColor = Color(0xFF041D11)
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_play),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (activeAccount?.type == AccountType.LOCAL) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = AmberSnapshot,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Local account active: launches offline single-player & LAN worlds. Online servers require Microsoft login.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (activeAccount?.ownsMinecraft == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Official Microsoft license verified for online & offline play.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldAccent
                                )
                            }
                        }
                    }
                }
            }

            // Quick Instance Telemetry Row
            item {
                val installedRuntimes = runtimes.count { it.isInstalled }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStatTile(
                        icon = Icons.Rounded.Extension,
                        label = "Mods",
                        value = "$modsCount active",
                        accent = DiamondCyan,
                        onClick = onOpenContent,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatTile(
                        icon = Icons.Rounded.Layers,
                        label = "Packs",
                        value = "$packsCount zips",
                        accent = EmeraldAccent,
                        onClick = onOpenContent,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatTile(
                        icon = Icons.Rounded.Memory,
                        label = "Runtimes",
                        value = "$installedRuntimes / ${runtimes.size} JRE",
                        accent = AmberSnapshot,
                        onClick = onOpenRuntimes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recently Played Versions
            item {
                SectionHeader(
                    title = stringResource(R.string.section_recently_played),
                    subtitle = "Jump back into your recent instances"
                )
            }

            if (recentlyPlayed.isEmpty()) {
                item {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No recent launches yet. Install an instance and press PLAY to record launch history.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recentlyPlayed, key = { it.id }) { inst ->
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (inst.id == activeInstance?.id) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .width(196.dp)
                                    .clickable { onSelectRecentInstance(inst.id) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        StatusPill(text = inst.minecraftVersion, color = EmeraldAccent)
                                        Text(
                                            text = "${inst.playCount}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = inst.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${inst.modLoader.displayName} • Java ${inst.requiredJavaMajor}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun QuickStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
