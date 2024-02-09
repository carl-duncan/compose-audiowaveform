package com.linc.audiowaveform

import android.view.MotionEvent
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import com.linc.audiowaveform.model.AmplitudeType
import com.linc.audiowaveform.model.WaveformAlignment

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

private val MinSpikeWidthDp: Dp = 1.dp
private val MaxSpikeWidthDp: Dp = 24.dp
private val MinSpikePaddingDp: Dp = 0.dp
private val MaxSpikePaddingDp: Dp = 12.dp
private val MinSpikeRadiusDp: Dp = 0.dp
private val MaxSpikeRadiusDp: Dp = 12.dp

private const val MinProgress: Float = 0F
private const val MaxProgress: Float = 1F

private const val MinSpikeHeight: Float = 1F
private const val DefaultGraphicsLayerAlpha: Float = 0.99F

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AudioWaveform(
    modifier: Modifier = Modifier,
    style: DrawStyle = Fill,
    waveformBrush: Brush = SolidColor(Color.White),
    progressBrush: Brush = SolidColor(Color.Blue),
    waveformAlignment: WaveformAlignment = WaveformAlignment.Center,
    amplitudeType: AmplitudeType = AmplitudeType.Avg,
    onProgressChangeFinished: (() -> Unit)? = null,
    spikeAnimationSpec: AnimationSpec<Float> = tween(500),
    spikeWidth: Dp = 4.dp,
    spikeRadius: Dp = 2.dp,
    spikePadding: Dp = 1.dp,
    progress: Float = 0F,
    amplitudes: List<Int>,
    onProgressChange: (Float) -> Unit
) {
    val _progress = remember(progress) { progress.coerceIn(MinProgress, MaxProgress) }
    val _spikeWidth = remember(spikeWidth) { spikeWidth.coerceIn(MinSpikeWidthDp, MaxSpikeWidthDp) }
    val _spikePadding = remember(spikePadding) { spikePadding.coerceIn(MinSpikePaddingDp, MaxSpikePaddingDp) }
    val _spikeRadius = remember(spikeRadius) { spikeRadius.coerceIn(MinSpikeRadiusDp, MaxSpikeRadiusDp) }
    val _spikeTotalWidth = remember(spikeWidth, spikePadding) { _spikeWidth + _spikePadding }
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var spikes by remember { mutableStateOf(0F) }
    val spikesAmplitudes = remember(amplitudes, spikes, amplitudeType) {
        amplitudes.toDrawableAmplitudes(
            amplitudeType = amplitudeType,
            spikes = spikes.toInt(),
            minHeight = MinSpikeHeight,
            maxHeight = canvasSize.height.coerceAtLeast(MinSpikeHeight)
        )
    }.map { animateFloatAsState(it, spikeAnimationSpec).value }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(48.dp)
            .graphicsLayer(alpha = DefaultGraphicsLayerAlpha)
            .pointerInteropFilter {
                return@pointerInteropFilter when (it.action) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE -> {
                        if (it.x in 0F..canvasSize.width) {
                            onProgressChange(it.x / canvasSize.width)
                            true
                        } else false
                    }
                    MotionEvent.ACTION_UP -> {
                        onProgressChangeFinished?.invoke()
                        true
                    }
                    else -> false
                }
            }
            .then(modifier)
    ) {
        canvasSize = size
        spikes = size.width / _spikeTotalWidth.toPx()
        val androidPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            // Convert the brush to Android Color
            val brushColor = when (waveformBrush) {
                is SolidColor -> waveformBrush.value
                else -> Color.White // Default or fallback color
            }
            color = brushColor.toArgb()
        }

        val progressAndroidPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            // Convert the brush to Android Color
            val brushColor = when (progressBrush) {
                is SolidColor -> progressBrush.value
                else -> Color.Blue // Default or fallback color
            }
            color = brushColor.toArgb()
        }

        spikesAmplitudes.forEachIndexed { index, amplitude ->
            val topLeft = Offset(
                x = index * _spikeTotalWidth.toPx(),
                y = when (waveformAlignment) {
                    WaveformAlignment.Top -> 0f
                    WaveformAlignment.Bottom -> size.height - amplitude
                    WaveformAlignment.Center -> size.height / 2f - amplitude / 2f
                }
            )
            val rectSize = Size(
                width = _spikeWidth.toPx(),
                height = amplitude
            )

            drawCustomRoundedRect(
                paint = androidPaint,
                topLeft = topLeft,
                size = rectSize,
                spikeRadius = _spikeRadius.toPx(),
                amplitude = amplitude,
                waveformAlignment = waveformAlignment
            )

//            drawCustomRect(
//                paint = progressAndroidPaint,
//                topLeft = Offset(
//                    x = 0f,
//                    y = 0f
//                ),
//                size = Size(
//                    width = _progress * size.width,
//                    height = amplitude
//                ),
//                amplitude = amplitude,
//                waveformAlignment = waveformAlignment
//            )
        }
    }
}

private fun List<Int>.toDrawableAmplitudes(
    amplitudeType: AmplitudeType,
    spikes: Int,
    minHeight: Float,
    maxHeight: Float
): List<Float> {
    val amplitudes = map(Int::toFloat)
    if(amplitudes.isEmpty() || spikes == 0) {
        return List(spikes) { minHeight }
    }
    val transform = { data: List<Float> ->
        when(amplitudeType) {
            AmplitudeType.Avg -> data.average()
            AmplitudeType.Max -> data.max()
            AmplitudeType.Min -> data.min()
        }.toFloat().coerceIn(minHeight, maxHeight)
    }
    return when {
        spikes > amplitudes.count() -> amplitudes.fillToSize(spikes, transform)
        else -> amplitudes.chunkToSize(spikes, transform)
    }.normalize(minHeight, maxHeight)
}


fun DrawScope.drawCustomRoundedRect(
    paint: android.graphics.Paint,
    topLeft: Offset,
    size: Size,
    spikeRadius: Float,
    amplitude: Float,
    waveformAlignment: WaveformAlignment
) {
    drawIntoCanvas { canvas ->
        val path = android.graphics.Path().apply {
            // Calculate coordinates for the rectangle
            val left = topLeft.x
            val top = when (waveformAlignment) {
                WaveformAlignment.Top -> topLeft.y
                WaveformAlignment.Bottom -> topLeft.y + size.height - amplitude
                WaveformAlignment.Center -> topLeft.y + size.height / 2f - amplitude / 2f
                else -> {
                    topLeft.y}
            }
            val right = left + size.width
            val bottom = top + amplitude

            // Move to the top left corner, offset by the radius to start the top line
            moveTo(left + spikeRadius, top)

            // Top line
            lineTo(right - spikeRadius, top)
            // Top right corner
            arcTo(android.graphics.RectF(right - 2 * spikeRadius, top, right, top + 2 * spikeRadius), -90f, 90f)
            // Right line
            lineTo(right, bottom - spikeRadius)
            // Bottom right corner
            arcTo(android.graphics.RectF(right - 2 * spikeRadius, bottom - 2 * spikeRadius, right, bottom), 0f, 90f)
            // Bottom line
            lineTo(left + spikeRadius, bottom)
            // Bottom left corner
            arcTo(android.graphics.RectF(left, bottom - 2 * spikeRadius, left + 2 * spikeRadius, bottom), 90f, 90f)
            // Left line
            lineTo(left, top + spikeRadius)
            // Top left corner
            arcTo(android.graphics.RectF(left, top, left + 2 * spikeRadius, top + 2 * spikeRadius), 180f, 90f)

            close()
        }
        canvas.nativeCanvas.drawPath(path, paint)
    }
}

fun DrawScope.drawCustomRect(
    paint: android.graphics.Paint,
    topLeft: Offset,
    size: Size,
    amplitude: Float,
    waveformAlignment: WaveformAlignment
) {
    drawIntoCanvas { canvas ->
        val path = android.graphics.Path().apply {
            // Calculate coordinates for the rectangle
            val left = topLeft.x
            val top = when (waveformAlignment) {
                WaveformAlignment.Top -> topLeft.y
                WaveformAlignment.Bottom -> topLeft.y + size.height - amplitude
                WaveformAlignment.Center -> topLeft.y + size.height / 2f - amplitude / 2f
                else -> {
                    topLeft.y}
            }
            val right = left + size.width
            val bottom = top + amplitude

            // Move to the top left corner
            moveTo(left, top)
            // Top line
            lineTo(right, top)
            // Right line
            lineTo(right, bottom)
            // Bottom line
            lineTo(left, bottom)
            // Left line
            lineTo(left, top)

            close()
        }
        canvas.nativeCanvas.drawPath(path, paint)
    }
}



