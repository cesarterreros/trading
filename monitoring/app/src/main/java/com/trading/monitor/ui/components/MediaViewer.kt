package com.trading.monitor.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trading.monitor.ui.theme.AppColors
import com.trading.monitor.ui.theme.AppSpace
import com.trading.monitor.ui.theme.AppType
import com.trading.monitor.ui.theme.reducedMotion
import com.trading.monitor.ui.theme.tabular

/** Full-screen viewer for a post's images with zoom, pan and swipe. */
@Composable
fun MediaViewer(images: List<String>, startIndex: Int, onClose: () -> Unit) {
    if (images.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, images.lastIndex),
    ) { images.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(AppColors.ViewerBackdrop)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            key(page) {
                ZoomablePage(
                    model = images[page],
                    description = "Image ${page + 1} of ${images.size}",
                    onZoomChange = { zoomed = it },
                )
            }
        }
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    style = AppType.Label.tabular(),
                    color = AppColors.ViewerOnBackdrop,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = AppSpace.m),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close image viewer",
                    tint = AppColors.ViewerOnBackdrop,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePage(
    model: String,
    description: String,
    onZoomChange: (Boolean) -> Unit,
) {
    var targetScale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val reduceMotion = reducedMotion()
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = if (reduceMotion) snap() else spring(),
        label = "zoomScale",
    )
    val zoomed = targetScale > 1f
    val onChange by rememberUpdatedState(onZoomChange)
    LaunchedEffect(zoomed) { onChange(zoomed) }
    DisposableEffect(Unit) { onDispose { onChange(false) } }

    fun clamp(raw: Offset, current: Float): Offset {
        if (current <= 1f) return Offset.Zero
        val max = Offset(bounds.width * (current - 1f) / 2f, bounds.height * (current - 1f) / 2f)
        return Offset(raw.x.coerceIn(-max.x, max.x), raw.y.coerceIn(-max.y, max.y))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { bounds = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var pressed = true
                    while (pressed) {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || targetScale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val pinching = multi && zoom != 1f
                            val panning = targetScale > 1f && pan != Offset.Zero
                            if (pinching || panning) {
                                event.changes.forEach { it.consume() }
                                if (pinching) {
                                    targetScale = (targetScale * zoom).coerceIn(1f, 5f)
                                    offset = clamp(offset, targetScale)
                                }
                                if (targetScale > 1f && pan != Offset.Zero) {
                                    offset = clamp(offset + pan, targetScale)
                                }
                            }
                        }
                        pressed = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (targetScale > 1f) {
                            targetScale = 1f
                            offset = Offset.Zero
                        } else {
                            targetScale = 2.5f
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = model,
            contentDescription = description,
            contentScale = ContentScale.Fit,
            placeholder = ColorPainter(AppColors.Placeholder),
            error = ColorPainter(AppColors.Placeholder),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
