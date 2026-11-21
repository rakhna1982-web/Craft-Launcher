package com.craftlauncher.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.AvatarPreset
import com.craftlauncher.android.data.model.LaunchPreflightReport
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import com.craftlauncher.android.ui.theme.EmeraldDeep
import com.craftlauncher.android.ui.theme.RedstoneError
import com.example.R
import java.util.Locale

@Composable
fun CraftBrandLogo(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(EmeraldAccent, Color(0xFF059669))
                    )
                )
                .padding(2.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_launcher_icon_fg),
                contentDescription = "CraftLauncher logo",
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Column {
            Text(
                text = "CraftLauncher",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "JAVA EDITION • ANDROID",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AccountAvatar(
    avatarUri: String?,
    username: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val baseModifier = modifier
        .size(size)
        .clip(shape)
        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), shape)

    when (avatarUri) {
        AvatarPreset.STEVE.key, null, "" -> Image(
            painter = painterResource(id = R.drawable.img_avatar_steve),
            contentDescription = "$username avatar",
            modifier = baseModifier,
            contentScale = ContentScale.Crop
        )
        AvatarPreset.ALEX.key -> Image(
            painter = painterResource(id = R.drawable.img_avatar_alex),
            contentDescription = "$username avatar",
            modifier = baseModifier,
            contentScale = ContentScale.Crop
        )
        AvatarPreset.ENDERMAN.key -> Image(
            painter = painterResource(id = R.drawable.img_avatar_enderman),
            contentDescription = "$username avatar",
            modifier = baseModifier,
            contentScale = ContentScale.Crop
        )
        else -> AsyncImage(
            model = avatarUri,
            contentDescription = "$username avatar",
            modifier = baseModifier,
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.img_avatar_steve),
            placeholder = painterResource(id = R.drawable.img_avatar_steve)
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaunchPreflightSheet(
    report: LaunchPreflightReport,
    onDismiss: () -> Unit,
    onOpenGameViewport: (Boolean) -> Unit,
    onGoToVersions: () -> Unit,
    onGoToRuntimes: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (report.canExecuteJvmNatively) {
                            "Ready to Launch ${report.minecraftVersion}"
                        } else {
                            "Launch Preflight & Engine Check"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${report.instanceName} • ${report.accountName} (${if (report.accountType == AccountType.LOCAL) "Offline" else "Microsoft"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close preflight")
                }
            }

            report.checks.forEach { check ->
                val tint = when {
                    check.passed -> EmeraldAccent
                    check.isWarningOnly -> AmberSnapshot
                    else -> RedstoneError
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = when {
                                check.passed -> Icons.Rounded.CheckCircle
                                check.isWarningOnly -> Icons.Rounded.Warning
                                else -> Icons.Rounded.Error
                            },
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = check.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!check.passed && check.id == "game_files") {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onGoToVersions()
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Install Game Files")
                                }
                            } else if (!check.passed && check.id == "java_runtime") {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onGoToRuntimes()
                                    },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text("Install Java ${report.javaMajorRequired}")
                                }
                            }
                        }
                    }
                }
            }

            if (report.missingNativeComponents.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmberSnapshot.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AmberSnapshot.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Why Full Desktop JVM Execution Needs NDK Binaries",
                            style = MaterialTheme.typography.labelLarge,
                            color = AmberSnapshot
                        )
                        Text(
                            text = "Running Minecraft: Java Edition desktop bytecode on Android requires compiled ARM64/ARM32 JNI libraries from an upstream GPLv3 engine (PojavLauncher / Amethyst): OpenJDK Mobile (libjvm.so), GL4ES/Zink, LWJGL3-GLFW stubs, and OpenAL-Soft. Because those C/C++ shared objects are not bundled in this standalone APK, CraftLauncher will NOT fake a launch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "You can still inspect the assembled JVM launch command below or open the real OpenGL ES Game Viewport to test touch controls, hardware keyboard, and pointer capture.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = DiamondCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Assembled JVM Command & Environment",
                    style = MaterialTheme.typography.labelLarge,
                    color = DiamondCyan
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = report.environmentVars.entries.joinToString(" ") { "${it.key}=${it.value}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAccent
                    )
                    Text(
                        text = report.jvmCommandLinePreview.joinToString(" "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (report.canExecuteJvmNatively) {
                Button(
                    onClick = { onOpenGameViewport(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("launch_native_game_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Launch Game Viewport", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onOpenGameViewport(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("launch_diagnostic_viewport_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldDeep,
                        contentColor = EmeraldAccent
                    )
                ) {
                    Icon(Icons.Rounded.Gamepad, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open OpenGL ES Viewport & Controls Test")
                }
            }
        }
    }
}
