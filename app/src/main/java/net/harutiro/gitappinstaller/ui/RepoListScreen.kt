package net.harutiro.gitappinstaller.ui

import android.app.Application
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.harutiro.gitappinstaller.domain.UpdateState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoListScreen(
    onAdd: () -> Unit,
    viewModel: RepoListViewModel = viewModel(
        factory = RepoListViewModel.factory(
            LocalContext.current.applicationContext as Application
        )
    ),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RepoListViewModel.UiEvent.RequestInstallPermission -> {
                    viewModel.openInstallPermissionSettings()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tracked Repos") },
                actions = {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh all")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            EmptyListContent(padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.repo.id }) { uiState ->
                    RepoCard(
                        ui = uiState,
                        onInstall = { viewModel.install(uiState.repo.id) },
                        onOpen = { viewModel.openApp(uiState.repo.id) },
                        onDelete = { viewModel.remove(uiState.repo.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyListContent(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No repos tracked yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to add a public GitHub repo URL.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RepoCard(
    ui: RepoUiState,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ui.repo.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = ui.repo.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(ui.state)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Installed: ${ui.installedVersion ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val latestText = ui.release?.let { rel ->
                        "Latest: ${rel.versionName} (${rel.tagName})"
                    } ?: "Latest: —"
                    Text(
                        text = latestText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ui.release?.apkAsset?.let { asset ->
                        Text(
                            text = "APK: ${asset.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ActionButton(
                    ui = ui,
                    onInstall = onInstall,
                    onOpen = onOpen,
                )
            }

            if (ui.isChecking) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            ui.errorMessage?.let { message ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusChip(state: UpdateState) {
    val (label, container) = when (state) {
        UpdateState.UPDATE_AVAILABLE -> "Update" to MaterialTheme.colorScheme.primaryContainer
        UpdateState.UP_TO_DATE -> "Up to date" to MaterialTheme.colorScheme.secondaryContainer
        UpdateState.NOT_INSTALLED -> "Not installed" to MaterialTheme.colorScheme.tertiaryContainer
        UpdateState.NO_APK -> "No APK" to MaterialTheme.colorScheme.errorContainer
        UpdateState.UNKNOWN -> "Unknown" to MaterialTheme.colorScheme.surfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container),
    )
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun ActionButton(
    ui: RepoUiState,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    if (ui.isInstalling) {
        Button(onClick = {}, enabled = false) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text("Installing")
        }
        return
    }
    if (ui.isChecking) {
        Button(onClick = {}, enabled = false) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text("Checking")
        }
        return
    }
    when (ui.state) {
        UpdateState.UPDATE_AVAILABLE -> Button(
            onClick = onInstall,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text("Install") }
        UpdateState.NOT_INSTALLED -> Button(onClick = onInstall) { Text("Install") }
        UpdateState.UP_TO_DATE -> Button(onClick = onOpen) { Text("開く") }
        UpdateState.NO_APK -> Button(onClick = {}, enabled = false) { Text("No APK in release") }
        UpdateState.UNKNOWN -> Button(onClick = {}, enabled = false) { Text("—") }
    }
}
