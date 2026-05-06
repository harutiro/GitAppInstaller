package net.harutiro.gitappinstaller.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoginStatusCard(state = state, onLogout = viewModel::logout)

            if (!state.loggedIn) {
                Divider()
                DeviceFlowSection(
                    state = state,
                    onLogin = viewModel::startDeviceLogin,
                    onCancel = viewModel::cancelLogin,
                    onOpenBrowser = { uri ->
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                    onCopyCode = { code -> copyToClipboard(context, code) },
                )

                Divider()
                PatSection(
                    state = state,
                    onChange = viewModel::onPatChange,
                    onSave = viewModel::savePat,
                )
            }

            state.errorMessage?.let { msg ->
                Text(text = msg, color = Color.Red, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LoginStatusCard(state: SettingsState, onLogout: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (state.loggedIn) "GitHub にログイン中" else "未ログイン",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            val sub = when {
                state.loggedIn && state.login != null -> "@${state.login}"
                state.loggedIn -> "Token 保存済み"
                else -> "Private リポジトリを利用するにはログインが必要です"
            }
            Text(text = sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.loggedIn) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onLogout) { Text("ログアウト") }
            }
        }
    }
}

@Composable
private fun DeviceFlowSection(
    state: SettingsState,
    onLogin: () -> Unit,
    onCancel: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onCopyCode: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "GitHub でサインイン (Device Flow)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (state.oauthConfigured) {
                "ボタンを押すとコードが表示されます。表示された URL をブラウザで開き、コードを入力してください。"
            } else {
                "OAuth Client ID が未設定です。BuildConfigOverrides.kt の CLIENT_ID を設定するか、下の Personal Access Token 入力をお使いください。"
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (state.deviceCode != null) {
            Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ユーザーコード", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = state.deviceCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Button(onClick = { state.verificationUri?.let(onOpenBrowser) }) {
                            Text("ブラウザで開く")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { onCopyCode(state.deviceCode) }) {
                            Text("コピー")
                        }
                    }
                    state.pollingMessage?.let { msg ->
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(msg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCancel) { Text("キャンセル") }
                }
            }
        } else {
            Button(onClick = onLogin, enabled = state.oauthConfigured && !state.isLoggingIn) {
                if (state.isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.width(16.dp).height(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("準備中…")
                } else {
                    Text("Login with GitHub")
                }
            }
        }
    }
}

@Composable
private fun PatSection(
    state: SettingsState,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "または Personal Access Token を直接入力",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Settings → Developer settings → Personal access tokens で `repo` スコープのトークンを作成して貼り付けてください。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.patInput,
            onValueChange = onChange,
            label = { Text("ghp_... または github_pat_...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onSave, enabled = state.patInput.isNotBlank()) { Text("保存") }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("device_code", text))
}
