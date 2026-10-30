package com.khanz1.serlok.ui.components

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.khanz1.serlok.R
import com.khanz1.serlok.ui.theme.SerlokPrimary
import com.khanz1.serlok.ui.theme.SerlokWarning

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

fun hasLocationPermission(context: Context): Boolean =
    LOCATION_PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PermissionChecker.PERMISSION_GRANTED
    }

/**
 * Prompts for foreground location access before the map and [LocationRepository]
 * can publish GPS fixes. Hidden once at least one location permission is granted.
 */
@Composable
fun LocationPermissionCard(
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {},
) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(ctx)) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.any { it }
        if (granted) onGranted()
    }

    if (granted) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = SerlokWarning.copy(alpha = 0.10f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MyLocation,
                contentDescription = null,
                tint = SerlokWarning,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(R.string.permission_room_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.permission_room_rationale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { launcher.launch(LOCATION_PERMISSIONS) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SerlokPrimary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.permission_action_allow))
            }
        }
    }
}
