package com.trading.monitor.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.trading.monitor.App
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppShape
import com.trading.monitor.ui.theme.AppSize
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.cardBorder
import com.trading.monitor.ui.theme.tabular
import java.time.LocalDate

@Composable
fun SettingsScreen(app: App) {
    val settings = app.settings
    val context = LocalContext.current
    val usedTokens by remember { app.dao.tokensFlow(LocalDate.now().toString()) }.collectAsState(initial = null)

    var apiKey by remember { mutableStateOf("") }
    val hasKey = remember { mutableStateOf(settings.apiKey.isNotBlank()) }
    var model by remember { mutableStateOf(settings.model) }
    var tokenCap by remember { mutableStateOf(settings.dailyTokenCap.toString()) }
    var minAgree by remember { mutableStateOf(settings.minAgreeingAccounts.toString()) }
    var buyThreshold by remember { mutableStateOf(settings.buyThreshold.toString()) }
    var strongThreshold by remember { mutableStateOf(settings.strongThreshold.toString()) }
    var minRr by remember { mutableStateOf(settings.minRewardRisk.toString()) }
    var monitoring by remember { mutableStateOf(settings.monitoringEnabled) }
    var keepOn by remember { mutableStateOf(settings.keepScreenOn) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpace.m),
        ) {
            Column(
                Modifier.widthIn(max = AppSize.ReadingWidth).fillMaxWidth().cardBorder().padding(AppSpace.m),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("DeepSeek", style = AppType.SectionTitle, color = AppColors.TextPrimary)
                Text(
                    if (hasKey.value) "An API key is stored (encrypted on this tablet). Type a new one to replace it."
                    else "No API key stored. Create one at platform.deepseek.com and paste it here.",
                    style = AppType.Caption,
                    color = if (hasKey.value) AppColors.TextSecondary else AppColors.WarningText,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it; saved = false },
                    label = { Text("API key", style = AppType.Caption) },
                    singleLine = true,
                    textStyle = AppType.Body,
                    shape = AppShape.Small,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it; saved = false },
                    label = { Text("Model", style = AppType.Caption) },
                    singleLine = true,
                    textStyle = AppType.Body,
                    shape = AppShape.Small,
                    modifier = Modifier.fillMaxWidth(),
                )
                NumberField("Daily token cap", tokenCap) { tokenCap = it; saved = false }
                Text("Used today: ${usedTokens ?: 0} tokens", style = AppType.Caption, color = AppColors.TextSecondary)
            }

            Column(
                Modifier.widthIn(max = AppSize.ReadingWidth).fillMaxWidth().cardBorder().padding(AppSpace.m),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Alerts and display", style = AppType.SectionTitle, color = AppColors.TextPrimary)
                SwitchRow(
                    label = "Price and verdict alerts in the background",
                    checked = monitoring,
                    onCheckedChange = {
                        monitoring = it
                        settings.monitoringEnabled = it
                        app.applyMonitoring()
                    },
                )
                SwitchRow(
                    label = "Keep the screen on while the app is open",
                    checked = keepOn,
                    onCheckedChange = {
                        keepOn = it
                        settings.keepScreenOn = it
                        val window = (context as? Activity)?.window
                        if (it) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                )
            }

            Column(
                Modifier.widthIn(max = AppSize.ReadingWidth).fillMaxWidth().cardBorder().padding(AppSpace.m),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Verdict rules", style = AppType.SectionTitle, color = AppColors.TextPrimary)
                NumberField("Accounts that must agree", minAgree) { minAgree = it; saved = false }
                NumberField("Minimum reward:risk", minRr, decimal = true) { minRr = it; saved = false }
                NumberField("Buy/sell score threshold", buyThreshold) { buyThreshold = it; saved = false }
                NumberField("Strong score threshold", strongThreshold) { strongThreshold = it; saved = false }
            }
        }

        HorizontalDivider(color = AppColors.Border)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppSpace.s),
            horizontalArrangement = Arrangement.spacedBy(AppSpace.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        settings.apiKey = apiKey
                        apiKey = ""
                        hasKey.value = true
                    }
                    if (model.isNotBlank()) settings.model = model
                    tokenCap.toIntOrNull()?.let { settings.dailyTokenCap = it }
                    minAgree.toIntOrNull()?.let { settings.minAgreeingAccounts = it }
                    buyThreshold.toIntOrNull()?.let { settings.buyThreshold = it }
                    strongThreshold.toIntOrNull()?.let { settings.strongThreshold = it }
                    minRr.toFloatOrNull()?.let { settings.minRewardRisk = it }
                    // Show what was actually stored (values are clamped to their allowed ranges).
                    tokenCap = settings.dailyTokenCap.toString()
                    minAgree = settings.minAgreeingAccounts.toString()
                    buyThreshold = settings.buyThreshold.toString()
                    strongThreshold = settings.strongThreshold.toString()
                    minRr = settings.minRewardRisk.toString()
                    saved = true
                    app.pollNow.tryEmit(Unit)
                },
                shape = AppShape.Small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                ),
                modifier = Modifier.heightIn(min = AppSize.TouchTarget),
            ) { Text("Save", style = AppType.Label) }
            if (saved) Text("Saved", style = AppType.Label, color = AppColors.BullishText)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = AppSize.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.s),
    ) {
        Text(label, style = AppType.Body, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AppColors.Primary,
                checkedThumbColor = AppColors.OnPrimary,
            ),
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, decimal: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = AppType.Caption) },
        singleLine = true,
        textStyle = AppType.Body.tabular(),
        shape = AppShape.Small,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
