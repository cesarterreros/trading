package com.trading.monitor.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trading.monitor.App
import com.trading.monitor.db.Account
import com.trading.monitor.db.Signal
import com.trading.monitor.directionOf
import com.trading.monitor.matrix.Direction
import com.trading.monitor.normalizeHandle
import com.trading.monitor.ui.components.Avatar
import com.trading.monitor.ui.components.EmptyState
import com.trading.monitor.ui.components.SentimentBadge
import com.trading.monitor.ui.components.VerifiedMark
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import com.trading.monitor.ui.theme.tabular
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** Accounts screen: add, weight, enable, edit and remove the X accounts in the matrix. */
@Composable
fun AccountsScreen(app: App) {
    val accounts by remember { app.dao.accounts() }.collectAsState(initial = emptyList())
    val signals by remember { app.dao.signals() }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Account?>(null) }
    var removing by remember { mutableStateOf<Account?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpace.m),
    ) {
        AddAccountCard(onAdd = { handle -> scope.launch { app.dao.addAccount(Account(handle)) } })

        if (accounts.isEmpty()) {
            EmptyState(
                title = "No accounts yet",
                lines = listOf(
                    "Add the X handles you follow. Accounts are also added when you " +
                        "save a post for a new handle.",
                ),
            )
        }

        accounts.forEach { account ->
            AccountCard(
                account = account,
                signals = signals.filter { it.handle == account.handle },
                onUpdate = { scope.launch { app.dao.updateAccount(it) } },
                onEdit = { editing = account },
                onRemove = { removing = account },
            )
        }
    }

    editing?.let { account ->
        EditAccountDialog(
            account = account,
            scope = app.scope,
            onDismiss = { editing = null },
            onSave = { updated ->
                editing = null
                scope.launch { app.dao.updateAccount(updated) }
            },
        )
    }

    removing?.let { account ->
        RemoveAccountDialog(
            account = account,
            onDismiss = { removing = null },
            onConfirm = {
                removing = null
                app.scope.launch {
                    account.avatarPath?.let { path ->
                        withContext(Dispatchers.IO) { File(context.filesDir, path).delete() }
                    }
                    app.dao.deleteAccount(account.handle)
                }
            },
        )
    }
}

@Composable
private fun AddAccountCard(onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.widthIn(max = AppSize.ReadingWidth).fillMaxWidth().cardBorder().padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        Text("Add account", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; inputError = null },
                label = { Text("X handle or profile link", style = AppType.Caption) },
                singleLine = true,
                isError = inputError != null,
                supportingText = inputError?.let { message -> { Text(message, style = AppType.Caption) } },
                shape = AppShape.Small,
                textStyle = AppType.Body,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val handle = normalizeHandle(input)
                    if (handle == null) {
                        inputError = "Not a valid X handle"
                    } else {
                        onAdd(handle)
                        input = ""
                    }
                },
                shape = AppShape.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                ),
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpace.s))
                Text("Add account", style = AppType.Body)
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    signals: List<Signal>,
    onUpdate: (Account) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var weight by remember(account.handle) { mutableFloatStateOf(account.weight.toFloat()) }
    val wins = signals.count { it.outcome == "WIN" }
    val losses = signals.count { it.outcome == "LOSS" }
    val expired = signals.count { it.outcome == "EXPIRED" }
    val open = signals.count { it.outcome == null && it.type != "noise" }
    val resolved = wins + losses
    val rate = if (resolved > 0) {
        String.format(Locale.US, "%.0f%% hit rate", wins * 100.0 / resolved)
    } else {
        "no resolved signals"
    }

    Column(
        Modifier.widthIn(max = AppSize.ReadingWidth).fillMaxWidth().cardBorder().padding(AppSpace.m),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountHeader(
            account = account,
            stance = latestDirection(signals),
            onEdit = onEdit,
            onRemove = onRemove,
        )

        Text(rate, style = AppType.Body.tabular(), color = AppColors.TextPrimary)
        Text(
            "$wins won · $losses lost · $expired expired · $open open",
            style = AppType.Caption.tabular(),
            color = AppColors.TextSecondary,
        )

        Text(
            String.format(Locale.US, "Weight %.2f", weight),
            style = AppType.Caption.tabular(),
            color = AppColors.TextSecondary,
        )
        Slider(
            value = weight,
            onValueChange = { weight = it },
            onValueChangeFinished = { onUpdate(account.copy(weight = weight.toDouble())) },
            valueRange = 0f..2f,
            steps = 7,
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Primary,
                activeTrackColor = AppColors.Primary,
                inactiveTrackColor = AppColors.Border,
            ),
        )

        Row(
            Modifier.fillMaxWidth().heightIn(min = AppSize.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Include in matrix",
                style = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = account.enabled,
                onCheckedChange = { onUpdate(account.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AppColors.Primary,
                    checkedThumbColor = AppColors.OnPrimary,
                ),
            )
        }
    }
}

@Composable
private fun AccountHeader(
    account: Account,
    stance: Direction?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val displayName = account.displayName?.takeIf { it.isNotBlank() }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            handle = account.handle,
            displayName = account.displayName,
            avatarPath = account.avatarPath,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
            ) {
                Text(
                    displayName ?: "@${account.handle}",
                    style = AppType.Label,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (account.verified) VerifiedMark()
            }
            if (displayName != null) {
                Text(
                    "@${account.handle}",
                    style = AppType.Caption,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        SentimentBadge(direction = stance)
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(AppSize.TouchTarget),
            ) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More actions for @${account.handle}",
                    tint = AppColors.TextSecondary,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit", style = AppType.Body, color = AppColors.TextPrimary) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Remove account", style = AppType.Body, color = AppColors.Danger) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun RemoveAccountDialog(account: Account, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Remove @${account.handle}?", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        },
        text = {
            Text(
                "The account leaves the matrix. Its posts stay in the feed, marked as removed.",
                style = AppType.Body,
                color = AppColors.TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", style = AppType.Label, color = AppColors.Danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = AppType.Label, color = AppColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun EditAccountDialog(
    account: Account,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(account.handle) { mutableStateOf(account.displayName.orEmpty()) }
    var verified by remember(account.handle) { mutableStateOf(account.verified) }
    var avatarPath by remember(account.handle) { mutableStateOf(account.avatarPath) }
    val created = remember(account.handle) { mutableListOf<File>() }

    fun cleanup(files: List<File>) {
        if (files.isEmpty()) return
        created.clear()
        scope.launch { withContext(Dispatchers.IO) { files.forEach { it.delete() } } }
    }

    fun cancel() {
        onDismiss()
        cleanup(created.toList())
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) { storeAvatar(context, account.handle, uri) }
                if (saved != null) {
                    created.add(saved)
                    avatarPath = avatarRelativePath(saved)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { cancel() },
        title = {
            Text("Edit @${account.handle}", style = AppType.SectionTitle, color = AppColors.TextPrimary)
        },
        text = {
            EditAccountBody(
                name = name,
                onNameChange = { if (it.length <= 50) name = it },
                verified = verified,
                onVerifiedChange = { verified = it },
                handle = account.handle,
                avatarPath = avatarPath,
                onChoosePicture = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePicture = { avatarPath = null },
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val finalPath = avatarPath
                onSave(
                    account.copy(
                        displayName = name.trim().ifBlank { null },
                        verified = verified,
                        avatarPath = finalPath,
                    ),
                )
                val previous = account.avatarPath
                val stale = created.filter { avatarRelativePath(it) != finalPath }
                created.clear()
                if (previous != null && previous != finalPath) {
                    scope.launch { withContext(Dispatchers.IO) { File(context.filesDir, previous).delete() } }
                }
                cleanup(stale)
            }) {
                Text("Save", style = AppType.Label, color = AppColors.Primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { cancel() }) {
                Text("Cancel", style = AppType.Label, color = AppColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun EditAccountBody(
    name: String,
    onNameChange: (String) -> Unit,
    verified: Boolean,
    onVerifiedChange: (Boolean) -> Unit,
    handle: String,
    avatarPath: String?,
    onChoosePicture: () -> Unit,
    onRemovePicture: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.s)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Display name (optional)", style = AppType.Caption) },
            singleLine = true,
            shape = AppShape.Small,
            textStyle = AppType.Body,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().heightIn(min = AppSize.TouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Verified",
                style = AppType.Body,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = verified,
                onCheckedChange = onVerifiedChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = AppColors.Primary,
                    checkedThumbColor = AppColors.OnPrimary,
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(
                handle = handle,
                displayName = name.trim().ifBlank { null },
                avatarPath = avatarPath,
            )
            TextButton(onClick = onChoosePicture) {
                Text("Choose picture", style = AppType.Label, color = AppColors.Primary)
            }
            if (avatarPath != null) {
                TextButton(onClick = onRemovePicture) {
                    Text("Remove picture", style = AppType.Label, color = AppColors.TextSecondary)
                }
            }
        }
        Text(
            "Set by you. The app does not read profiles from X.",
            style = AppType.Caption,
            color = AppColors.TextSecondary,
        )
    }
}

private fun storeAvatar(context: Context, handle: String, uri: Uri): File? {
    return try {
        val dir = File(context.filesDir, "avatars")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$handle-${System.currentTimeMillis()}.jpg")
        val source = context.contentResolver.openInputStream(uri)
        if (source == null) {
            null
        } else {
            source.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            file
        }
    } catch (e: Exception) {
        null
    }
}

private fun avatarRelativePath(file: File): String = "avatars/${file.name}"

/** The direction of the newest non-noise signal, or null when the account has none. */
private fun latestDirection(signals: List<Signal>): Direction? {
    val latest = signals.filter { it.type != "noise" }.maxByOrNull { it.timeMs } ?: return null
    return runCatching { directionOf(latest.direction) }.getOrNull()
}
