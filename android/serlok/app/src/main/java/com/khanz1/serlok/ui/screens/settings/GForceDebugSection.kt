package com.khanz1.serlok.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.khanz1.serlok.R
import com.khanz1.serlok.debug.DeveloperOptions
import com.khanz1.serlok.service.SosEventBus
import com.khanz1.serlok.safety.DebugSessionDownload
import com.khanz1.serlok.safety.DebugNotificationText
import com.khanz1.serlok.safety.DebugSnapshot
import com.khanz1.serlok.safety.GForceChartPoint
import com.khanz1.serlok.safety.GForceChartWindow
import com.khanz1.serlok.safety.GForceTimeBucket
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class DebugChartMode { SECONDS, MINUTES }

@Composable
internal fun rememberDeveloperOptionsEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(DeveloperOptions.isEnabled(context)) }
    DisposableEffect(context) {
        val observer = DeveloperOptions.observe(context) {
            enabled = DeveloperOptions.isEnabled(context)
        }
        onDispose { DeveloperOptions.unregister(context, observer) }
    }
    return enabled
}

@Composable
internal fun GForceDebugSection(
    overlayEnabled: Boolean,
    snapshot: DebugSnapshot,
    onOverlayChange: (Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(DebugChartMode.SECONDS) }
    var windowSize by remember { mutableIntStateOf(30) }
    var yScale by remember { mutableStateOf(GForceChartWindow.YScale.AUTO) }
    var inspectedElapsed by remember { mutableStateOf<Int?>(null) }
    var copied by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val buckets = if (mode == DebugChartMode.SECONDS) snapshot.seconds else snapshot.minutes
    val windows = if (mode == DebugChartMode.SECONDS) {
        GForceChartWindow.SECOND_WINDOWS
    } else {
        GForceChartWindow.MINUTE_WINDOWS
    }
    val windowed = GForceChartWindow.visible(buckets, windowSize)
    val localIndex = resolveInspectedIndex(windowed, inspectedElapsed)
    val selected = windowed.getOrNull(localIndex)

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(2_000)
        copied = false
    }
    LaunchedEffect(downloaded) {
        if (!downloaded) return@LaunchedEffect
        delay(2_000)
        downloaded = false
    }

    SettingsCard {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(R.string.settings_gforce_debug),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.settings_gforce_debug_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = overlayEnabled,
                onCheckedChange = onOverlayChange,
            )
        }
    }

    if (!overlayEnabled) return

    Spacer(Modifier.height(12.dp))
    SettingsCard {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            DebugStatGrid(snapshot)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(
                                ClipData.newPlainText(
                                    "serlok-gforce",
                                    DebugNotificationText.summary(snapshot),
                                ),
                            )
                        copied = true
                    },
                ) {
                    Text(
                        stringResource(
                            if (copied) R.string.settings_gforce_copied else R.string.settings_gforce_copy,
                        ),
                    )
                }
                TextButton(
                    onClick = {
                        val export = SosEventBus.debugCsvExport()
                        if (export == null) return@TextButton
                        val uri = DebugSessionDownload.save(context, export.fileName, export.csv)
                            ?: return@TextButton
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, export.fileName)
                            clipData = ClipData.newRawUri(export.fileName, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    share,
                                    context.getString(R.string.settings_gforce_download),
                                ),
                            )
                        }
                        downloaded = true
                    },
                    enabled = snapshot.seconds.isNotEmpty() || snapshot.elapsedMs > 0L,
                ) {
                    Text(
                        stringResource(
                            if (downloaded) {
                                R.string.settings_gforce_downloaded
                            } else {
                                R.string.settings_gforce_download
                            },
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == DebugChartMode.SECONDS,
                    onClick = {
                        mode = DebugChartMode.SECONDS
                        windowSize = 30
                        inspectedElapsed = null
                    },
                    label = { Text(stringResource(R.string.settings_gforce_seconds)) },
                )
                FilterChip(
                    selected = mode == DebugChartMode.MINUTES,
                    onClick = {
                        mode = DebugChartMode.MINUTES
                        windowSize = 5
                        inspectedElapsed = null
                    },
                    label = { Text(stringResource(R.string.settings_gforce_minutes)) },
                )
                FilterChip(
                    selected = inspectedElapsed == null,
                    onClick = { inspectedElapsed = null },
                    label = { Text(stringResource(R.string.settings_gforce_live)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_gforce_window),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                windows.forEach { size ->
                    FilterChip(
                        selected = windowSize == size,
                        onClick = { windowSize = size },
                        label = { Text(windowChipLabel(size, mode)) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_gforce_scale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GForceChartWindow.YScale.entries.forEach { scale ->
                    FilterChip(
                        selected = yScale == scale,
                        onClick = { yScale = scale },
                        label = { Text(scaleChipLabel(scale)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (windowed.isEmpty()) {
                Text(
                    stringResource(
                        if (snapshot.sessionActive) {
                            R.string.settings_gforce_waiting_samples
                        } else {
                            R.string.settings_gforce_idle
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GForceDebugChart(
                    buckets = windowed,
                    thresholdG = snapshot.thresholdG,
                    selectedIndex = localIndex,
                    yScale = yScale,
                    mode = mode,
                    onSelectIndex = { index ->
                        inspectedElapsed = windowed.getOrNull(index)?.elapsedIndex
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(
                        R.string.settings_gforce_readout,
                        formatBucketTime(selected, mode),
                        selected?.let { DebugNotificationText.format2(it.peakG) } ?: "-",
                        selected?.let { DebugNotificationText.format2(it.meanG) } ?: "-",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (windowed.size > 1) {
                    Slider(
                        value = localIndex.coerceAtLeast(0).toFloat(),
                        onValueChange = { value ->
                            inspectedElapsed = windowed.getOrNull(value.toInt())?.elapsedIndex
                        },
                        valueRange = 0f..(windowed.lastIndex.toFloat()),
                        steps = (windowed.size - 2).coerceAtLeast(0),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun windowChipLabel(size: Int, mode: DebugChartMode): String {
    if (mode == DebugChartMode.MINUTES) {
        return stringResource(R.string.settings_gforce_window_min, size)
    }
    if (size >= 60 && size % 60 == 0) {
        return stringResource(R.string.settings_gforce_window_min, size / 60)
    }
    return stringResource(R.string.settings_gforce_window_sec, size)
}

@Composable
private fun scaleChipLabel(scale: GForceChartWindow.YScale): String {
    return when (scale) {
        GForceChartWindow.YScale.AUTO -> stringResource(R.string.settings_gforce_scale_auto)
        GForceChartWindow.YScale.G2 -> stringResource(R.string.settings_gforce_scale_fixed, "2")
        GForceChartWindow.YScale.G5 -> stringResource(R.string.settings_gforce_scale_fixed, "5")
        GForceChartWindow.YScale.G10 -> stringResource(R.string.settings_gforce_scale_fixed, "10")
    }
}

@Composable
private fun DebugStatGrid(snapshot: DebugSnapshot) {
    val status = when {
        snapshot.seconds.isEmpty() && !snapshot.sessionActive ->
            stringResource(R.string.settings_gforce_none)
        snapshot.coolingDown -> stringResource(
            R.string.settings_gforce_cooling,
            DebugNotificationText.format1(snapshot.cooldownRemainingMs / 1_000f),
        )
        snapshot.armed -> stringResource(R.string.settings_gforce_armed)
        else -> stringResource(R.string.settings_gforce_waiting)
    }
    val lastTrigger = snapshot.lastTriggerG?.let { value ->
        stringResource(R.string.settings_gforce_g_value, DebugNotificationText.format2(value))
    } ?: stringResource(R.string.settings_gforce_none)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DebugStat(
                label = stringResource(R.string.settings_gforce_now),
                value = stringResource(
                    R.string.settings_gforce_g_value,
                    DebugNotificationText.format2(snapshot.nowG),
                ),
                modifier = Modifier.weight(1f),
            )
            DebugStat(
                label = stringResource(R.string.settings_gforce_peak),
                value = stringResource(
                    R.string.settings_gforce_g_value,
                    DebugNotificationText.format2(snapshot.peakG),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DebugStat(
                label = stringResource(R.string.settings_gforce_avg),
                value = stringResource(
                    R.string.settings_gforce_g_value,
                    DebugNotificationText.format2(snapshot.meanG),
                ),
                modifier = Modifier.weight(1f),
            )
            DebugStat(
                label = stringResource(R.string.settings_gforce_threshold),
                value = stringResource(
                    R.string.settings_gforce_g_value,
                    DebugNotificationText.format1(snapshot.thresholdG),
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DebugStat(
                label = stringResource(R.string.settings_gforce_triggers),
                value = snapshot.triggerCount.toString(),
                modifier = Modifier.weight(1f),
            )
            DebugStat(
                label = stringResource(R.string.settings_gforce_last),
                value = lastTrigger,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DebugStat(
                label = stringResource(R.string.settings_gforce_elapsed),
                value = DebugNotificationText.formatElapsed(snapshot.elapsedMs),
                modifier = Modifier.weight(1f),
            )
            DebugStat(
                label = stringResource(R.string.settings_gforce_status),
                value = status,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DebugStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun GForceDebugChart(
    buckets: List<GForceTimeBucket>,
    thresholdG: Float,
    selectedIndex: Int,
    yScale: GForceChartWindow.YScale,
    mode: DebugChartMode,
    onSelectIndex: (Int) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val thresholdColor = MaterialTheme.colorScheme.error
    val markerColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val peaks = buckets.map { bucket -> bucket.peakG }
    val maxY = GForceChartWindow.yMax(peaks, thresholdG, yScale)
    val ticks = GForceChartWindow.yTicks(maxY)
    val point = GForceChartWindow.inspect(buckets, selectedIndex, maxY)
    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val popupWidthPx = with(density) { 128.dp.roundToPx() }
    val popupHeightPx = with(density) { 56.dp.roundToPx() }

    Row(Modifier.fillMaxWidth().height(188.dp)) {
        Column(
            Modifier.width(40.dp).fillMaxHeight().padding(bottom = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            ticks.reversed().forEach { tick ->
                Text(
                    DebugNotificationText.format1(tick),
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor,
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coordinates -> plotSize = coordinates.size }
                    .pointerInput(buckets) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val tapped = GForceChartWindow.indexFromX(
                                x = down.position.x,
                                width = size.width.toFloat(),
                                bucketCount = buckets.size,
                            )
                            if (tapped >= 0) onSelectIndex(tapped)
                            drag(down.id) { change ->
                                val dragged = GForceChartWindow.indexFromX(
                                    x = change.position.x,
                                    width = size.width.toFloat(),
                                    bucketCount = buckets.size,
                                )
                                if (dragged >= 0) onSelectIndex(dragged)
                                change.consume()
                            }
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (buckets.isEmpty()) return@Canvas
                    fun xFor(index: Int): Float {
                        if (buckets.size == 1) return size.width / 2f
                        return size.width * index / (buckets.size - 1)
                    }
                    fun yFor(gForce: Float): Float =
                        (size.height * (1f - (gForce / maxY))).coerceIn(0f, size.height)

                    ticks.forEach { tick ->
                        val y = yFor(tick)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    drawLine(
                        color = thresholdColor,
                        start = Offset(0f, yFor(thresholdG)),
                        end = Offset(size.width, yFor(thresholdG)),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                    )
                    val path = Path()
                    peaks.forEachIndexed { index, gForce ->
                        val next = Offset(xFor(index), yFor(gForce))
                        if (index == 0) path.moveTo(next.x, next.y) else path.lineTo(next.x, next.y)
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                    if (point != null) {
                        val mark = Offset(
                            x = point.xFraction * size.width,
                            y = point.yFraction * size.height,
                        )
                        drawLine(
                            color = markerColor.copy(alpha = 0.45f),
                            start = Offset(mark.x, 0f),
                            end = Offset(mark.x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        drawCircle(color = markerColor, radius = 6.dp.toPx(), center = mark)
                        drawCircle(color = lineColor, radius = 3.dp.toPx(), center = mark)
                    }
                }
                if (point != null && plotSize.width > 0) {
                    GForceInspectPopup(
                        point = point,
                        mode = mode,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                val x = (point.xFraction * plotSize.width - popupWidthPx / 2f)
                                    .roundToInt()
                                    .coerceIn(0, (plotSize.width - popupWidthPx).coerceAtLeast(0))
                                val above = (point.yFraction * plotSize.height - popupHeightPx - 8)
                                    .roundToInt()
                                val y = if (above >= 0) {
                                    above
                                } else {
                                    (point.yFraction * plotSize.height + 8).roundToInt()
                                        .coerceAtMost((plotSize.height - popupHeightPx).coerceAtLeast(0))
                                }
                                IntOffset(x, y)
                            },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                windowXLabels(buckets, mode).forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = axisColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun GForceInspectPopup(
    point: GForceChartPoint,
    mode: DebugChartMode,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                stringResource(
                    R.string.settings_gforce_g_value,
                    DebugNotificationText.format2(point.peakG),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "t=${formatElapsedIndex(point.elapsedIndex, mode)}  " +
                    stringResource(
                        R.string.settings_gforce_popup_mean,
                        DebugNotificationText.format2(point.meanG),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun resolveInspectedIndex(
    windowed: List<GForceTimeBucket>,
    inspectedElapsed: Int?,
): Int {
    if (windowed.isEmpty()) return -1
    if (inspectedElapsed == null) return windowed.lastIndex
    val found = windowed.indexOfFirst { bucket -> bucket.elapsedIndex == inspectedElapsed }
    return if (found >= 0) found else windowed.lastIndex
}

private fun windowXLabels(
    buckets: List<GForceTimeBucket>,
    mode: DebugChartMode,
): List<String> {
    if (buckets.isEmpty()) return emptyList()
    val first = buckets.first()
    val last = buckets.last()
    if (buckets.size == 1) {
        return listOf(formatElapsedIndex(first.elapsedIndex, mode))
    }
    val mid = buckets[buckets.size / 2]
    return listOf(
        formatElapsedIndex(first.elapsedIndex, mode),
        formatElapsedIndex(mid.elapsedIndex, mode),
        formatElapsedIndex(last.elapsedIndex, mode),
    )
}

private fun formatBucketTime(bucket: GForceTimeBucket?, mode: DebugChartMode): String {
    if (bucket == null) return "--:--"
    return formatElapsedIndex(bucket.elapsedIndex, mode)
}

private fun formatElapsedIndex(elapsedIndex: Int, mode: DebugChartMode): String {
    val elapsedMs = if (mode == DebugChartMode.SECONDS) {
        elapsedIndex * 1_000L
    } else {
        elapsedIndex * 60_000L
    }
    return DebugNotificationText.formatElapsed(elapsedMs)
}
