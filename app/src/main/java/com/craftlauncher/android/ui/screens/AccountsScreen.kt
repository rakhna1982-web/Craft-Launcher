package com.craftlauncher.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.craftlauncher.android.data.local.AccountEntity
import com.craftlauncher.android.data.model.AccountType
import com.craftlauncher.android.data.model.AvatarPreset
import com.craftlauncher.android.ui.components.AccountAvatar
import com.craftlauncher.android.ui.components.StatusPill
import com.craftlauncher.android.ui.theme.AmberSnapshot
import com.craftlauncher.android.ui.theme.DiamondCyan
import com.craftlauncher.android.ui.theme.EmeraldAccent
import com.craftlauncher.android.ui.theme.RedstoneError
import com.craftlauncher.android.ui.viewmodel.MicrosoftLoginState

@Composable
fun AccountsScreen(
    accounts: List<AccountEntity>,
    activeAccountId: String?,
    msLoginState: MicrosoftLoginState,
    savedClientId: String,
    onCreateLocalProfile: (username: String, avatarUri: String?) -> Unit,
    onUpdateLocalProfile: (accountId: String, username: String, avatarUri: String?, ramMb: Int?) -> Unit,
    onSwitchAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onStartMicrosoftLogin: (clientId: String) -> Unit,
    onCancelMicrosoftLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateLocalDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<AccountEntity?>(null) }
    var showMicrosoftDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Account Manager",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Switch seamlessly between licensed Microsoft accounts and Local Offline profiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dual Auth Creation Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showCreateLocalDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_local_profile_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Local Profile")
                    }

                    OutlinedButton(
                        onClick = { showMicrosoftDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_microsoft_account_button")
                    ) {
                        Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Microsoft Login")
                    }
                }
            }

            // Clear Offline vs Licensed Feature Boundaries Card
            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = DiamondCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Official Auth vs Local Offline Policy",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            text = "• Local Profiles: Supports custom usernames, custom avatars, per-profile game instances, offline single-player, and local LAN play once game files & JRE are installed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• Microsoft Accounts: Uses official Microsoft OAuth + Xbox Live + XSTS + Minecraft Services ownership checks. Required for online authenticated multiplayer servers and Realms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (accounts.isEmpty()) {
                item {
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
                                Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Text("No Saved Profiles Yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Create a Local Offline Profile with any username and avatar, or sign in with your official Microsoft account.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(accounts, key = { it.id }) { acc ->
                    val isActive = acc.id == activeAccountId
                    val isLocal = acc.type == AccountType.LOCAL
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
                            .clickable { onSwitchAccount(acc.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AccountAvatar(
                                avatarUri = acc.avatarUri,
                                username = acc.username,
                                size = 52.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = acc.username,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isActive) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = "Active account",
                                            tint = EmeraldAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        text = if (isLocal) "Local Offline" else "Microsoft",
                                        color = if (isLocal) AmberSnapshot else EmeraldAccent
                                    )
                                    if (!isLocal && acc.ownsMinecraft) {
                                        StatusPill(text = "Licensed", color = DiamondCyan)
                                    }
                                }
                                Text(
                                    text = "UUID: ${acc.uuid}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isLocal) {
                                IconButton(onClick = { editingAccount = acc }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit profile")
                                }
                            }
                            IconButton(onClick = { onDeleteAccount(acc.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete profile")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateLocalDialog) {
        LocalProfileDialog(
            title = "New Local Offline Profile",
            initialUsername = "",
            initialAvatarUri = AvatarPreset.STEVE.key,
            initialRamMb = null,
            showRamOverride = false,
            onDismiss = { showCreateLocalDialog = false },
            onSave = { name, avatar, _ ->
                onCreateLocalProfile(name, avatar)
                showCreateLocalDialog = false
            }
        )
    }

    editingAccount?.let { acc ->
        LocalProfileDialog(
            title = "Edit Local Profile",
            initialUsername = acc.username,
            initialAvatarUri = acc.avatarUri,
            initialRamMb = acc.preferredRamMb,
            showRamOverride = true,
            onDismiss = { editingAccount = null },
            onSave = { name, avatar, ram ->
                onUpdateLocalProfile(acc.id, name, avatar, ram)
                editingAccount = null
            }
        )
    }

    if (showMicrosoftDialog) {
        MicrosoftLoginDialog(
            state = msLoginState,
            savedClientId = savedClientId,
            onStartLogin = onStartMicrosoftLogin,
            onDismiss = {
                onCancelMicrosoftLogin()
                showMicrosoftDialog = false
            }
        )
    }
}

@Composable
private fun LocalProfileDialog(
    title: String,
    initialUsername: String,
    initialAvatarUri: String?,
    initialRamMb: Int?,
    showRamOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (username: String, avatarUri: String?, ramMb: Int?) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var selectedAvatar by remember { mutableStateOf(initialAvatarUri ?: AvatarPreset.STEVE.key) }
    var ramOverrideText by remember { mutableStateOf(initialRamMb?.toString().orEmpty()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) selectedAvatar = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AccountAvatar(
                        avatarUri = selectedAvatar,
                        username = username.ifBlank { "Player" },
                        size = 56.dp
                    )
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose Photo")
                    }
                }

                Text("Or choose a built-in avatar:", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarPreset.entries.forEach { preset ->
                        val active = selectedAvatar == preset.key
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(
                                if (active) 2.dp else 1.dp,
                                if (active) EmeraldAccent else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.clickable { selectedAvatar = preset.key }
                        ) {
                            AccountAvatar(
                                avatarUri = preset.key,
                                username = preset.label,
                                size = 44.dp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it.take(16)
                        errorText = null
                    },
                    label = { Text("Offline Username (2-16 chars)") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { msg -> { Text(msg) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("local_username_input")
                )

                if (showRamOverride) {
                    OutlinedTextField(
                        value = ramOverrideText,
                        onValueChange = { ramOverrideText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Profile RAM Override (MB, optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = username.trim()
                    if (trimmed.length < 2) {
                        errorText = "Enter at least 2 characters."
                    } else {
                        val ram = ramOverrideText.toIntOrNull()?.takeIf { it in 512..8192 }
                        onSave(trimmed, selectedAvatar, ram)
                    }
                },
                modifier = Modifier.testTag("save_local_profile_button")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MicrosoftLoginDialog(
    state: MicrosoftLoginState,
    savedClientId: String,
    onStartLogin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var clientId by remember { mutableStateOf(savedClientId) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Official Microsoft Sign-In") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Uses official Microsoft OAuth 2.0 Device Code -> Xbox Live -> XSTS -> Minecraft Services entitlement verification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when (state) {
                    is MicrosoftLoginState.Idle -> {
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Azure App Client ID (Public Client)") },
                            placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Note: Register a free personal Azure AD App Registration (Mobile/Desktop public client, no secret needed) to authenticate your Microsoft account securely.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is MicrosoftLoginState.RequestingCode -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Text("Requesting Microsoft device code…")
                        }
                    }
                    is MicrosoftLoginState.WaitingForUser -> {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Verification Code:", style = MaterialTheme.typography.labelSmall)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.prompt.userCode,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldAccent
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(state.prompt.userCode))
                                        }
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy code")
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(state.prompt.verificationUri)).apply {
                                            if (context !is android.app.Activity) {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Open ${state.prompt.verificationUri}")
                                }
                                Text(
                                    text = state.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DiamondCyan
                                )
                            }
                        }
                    }
                    is MicrosoftLoginState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = RedstoneError
                        )
                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            label = { Text("Azure App Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (state is MicrosoftLoginState.Idle || state is MicrosoftLoginState.Error) {
                Button(onClick = { onStartLogin(clientId) }) {
                    Text("Request Code")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
