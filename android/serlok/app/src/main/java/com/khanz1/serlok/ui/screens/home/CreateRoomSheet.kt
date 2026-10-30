package com.khanz1.serlok.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomSheet(
    onConfirm: (shakeThreshold: Float, locationIntervalMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var shake by remember { mutableFloatStateOf(3.5f) }
    var intervalIndex by remember {
        mutableIntStateOf(
            AppSettings.LOCATION_INTERVALS.indexOf(10_000L).coerceAtLeast(0),
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.create_room_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.create_room_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            )

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_shake_level),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${"%.1f".format(shake)} g",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(
                value = shake,
                onValueChange = { raw ->
                    shake = listOf(2.5f, 3.5f, 4.5f).minBy { kotlin.math.abs(it - raw) }
                },
                valueRange = 2.5f..4.5f,
                steps = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.settings_location_interval),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(
                        R.string.settings_location_interval_value,
                        formatInterval(AppSettings.LOCATION_INTERVALS[intervalIndex]),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(
                value = intervalIndex.toFloat(),
                onValueChange = { intervalIndex = it.roundToInt() },
                valueRange = 0f..AppSettings.LOCATION_INTERVALS.lastIndex.toFloat(),
                steps = AppSettings.LOCATION_INTERVALS.size - 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onConfirm(shake, AppSettings.LOCATION_INTERVALS[intervalIndex])
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.create_room_action))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

private fun formatInterval(ms: Long): String = when {
    ms < 1_000 -> "${ms} ms"
    ms < 60_000 -> "${ms / 1_000} s"
    else -> "${ms / 60_000} min"
}
