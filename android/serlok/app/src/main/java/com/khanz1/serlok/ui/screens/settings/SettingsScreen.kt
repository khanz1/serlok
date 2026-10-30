package com.khanz1.serlok.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.ui.components.DeterministicAvatar
import com.khanz1.serlok.ui.screens.room.RoomViewModel

@Composable
fun SettingsScreen(
    roomViewModel: RoomViewModel,
    viewModel: SettingsViewModel,
    onLogout: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshProfile() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        SectionLabel(R.string.settings_profile)
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                DeterministicAvatar(AppSettings.userId, uiState.name, size = 52.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        uiState.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        uiState.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionLabel(R.string.settings_safety)
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        val developerOptionsEnabled = rememberDeveloperOptionsEnabled()
        if (developerOptionsEnabled) {
            Spacer(Modifier.height(24.dp))
            SectionLabel(R.string.settings_developer)
            GForceDebugSection(
                overlayEnabled = uiState.gforceDebugEnabled,
                snapshot = uiState.debugSnapshot,
                onOverlayChange = viewModel::setGforceDebugEnabled,
            )
        }

        Spacer(Modifier.height(24.dp))
        SettingsCard {
            DestructiveRow(Icons.AutoMirrored.Rounded.Logout, R.string.settings_logout) {
                viewModel.signOut(roomViewModel, onLogout)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
internal fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) { Column(Modifier.fillMaxWidth(), content = { content() }) }
}

@Composable
private fun DestructiveRow(icon: ImageVector, title: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.error)
        Text(
            stringResource(title),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
