package com.khanz1.serlok.ui.screens.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.R
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.ui.components.SerlokMark
import com.khanz1.serlok.ui.theme.SerlokSpacing

private data class OnboardingPage(
    val title: Int,
    val body: Int,
    val icon: ImageVector,
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = listOf(
        OnboardingPage(R.string.onboarding_location_title, R.string.onboarding_location_body, Icons.Rounded.Groups),
        OnboardingPage(R.string.onboarding_sos_title, R.string.onboarding_sos_body, Icons.Rounded.Shield),
    )
    var page by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SerlokMark(Modifier.size(34.dp), contentDescription = stringResource(R.string.app_name))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp),
            )
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "onboarding_transition",
            modifier = Modifier.weight(1f),
        ) { index ->
            val item = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(132.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp),
                    )
                }
                Spacer(Modifier.height(SerlokSpacing.xxl))
                Text(
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(SerlokSpacing.sm))
                Text(
                    text = stringResource(item.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.indices.forEach { index ->
                Box(
                    Modifier.padding(horizontal = 4.dp).size(if (page == index) 20.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (page == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
        Spacer(Modifier.height(SerlokSpacing.lg))
        Button(
            onClick = {
                if (page < pages.lastIndex) page++ else {
                    AppSettings.setOnboardingDone()
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.small,
        ) {
            Text(stringResource(if (page == pages.lastIndex) R.string.onboarding_start else R.string.onboarding_next))
        }
    }
}
