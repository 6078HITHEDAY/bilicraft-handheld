package com.bilicraft.handheld.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bilicraft.handheld.appicon.AppIconCatalog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 根导航：极简双屏切换。未登录 → 登录页；已登录 → 主控页。
 * 不引入 Navigation 组件，因为只有两个状态，一个布尔足够，避免过度设计。
 */
@Composable
fun AppRoot(vm: MainViewModel) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val loginOverlay by vm.loginOverlay.collectAsStateWithLifecycle()
    var showSplash by rememberSaveable { mutableStateOf(true) }

    Box(Modifier.fillMaxSize()) {
        if (loggedIn && !loginOverlay) {
            MainScreen(vm)
        } else {
            LoginScreen(vm)
        }

        if (showSplash) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val dropDistance = with(density) { maxHeight.toPx() * 0.7f }
                val firstBounceHeight = with(density) { 58.dp.toPx() }
                val secondBounceHeight = with(density) { 24.dp.toPx() }
                val thirdBounceHeight = with(density) { 8.dp.toPx() }
                val shadowOffset = with(density) { 66.dp.toPx() }
                val iconOffsetY = remember(dropDistance) { Animatable(-dropDistance) }
                val iconScale = remember { Animatable(0.78f) }
                val iconRotation = remember { Animatable(-8f) }
                val overlayAlpha = remember { Animatable(1f) }

                LaunchedEffect(dropDistance) {
                    coroutineScope {
                        launch {
                            iconOffsetY.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 1_120
                                    -dropDistance at 0
                                    0f at 560
                                    -firstBounceHeight at 700
                                    0f at 820
                                    -secondBounceHeight at 910
                                    0f at 990
                                    -thirdBounceHeight at 1_040
                                    0f at 1_120
                                }
                            )
                        }
                        launch {
                            iconScale.animateTo(
                                targetValue = 1f,
                                animationSpec = keyframes {
                                    durationMillis = 1_120
                                    0.78f at 0
                                    1.07f at 560
                                    0.96f at 700
                                    1.035f at 820
                                    0.985f at 910
                                    1.015f at 990
                                    1f at 1_120
                                }
                            )
                        }
                        launch {
                            iconRotation.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 1_120
                                    -8f at 0
                                    1.8f at 560
                                    -1.2f at 820
                                    0.5f at 990
                                    0f at 1_120
                                }
                            )
                        }
                    }
                    delay(120)
                    coroutineScope {
                        launch {
                            iconScale.animateTo(
                                targetValue = 9f,
                                animationSpec = tween(
                                    durationMillis = 900,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        }
                        launch {
                            delay(480)
                            overlayAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 420,
                                    easing = FastOutLinearInEasing
                                )
                            )
                        }
                    }
                    showSplash = false
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = overlayAlpha.value }
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    val distanceFromCenter = (-iconOffsetY.value / dropDistance).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(width = 76.dp, height = 12.dp)
                            .graphicsLayer {
                                translationY = shadowOffset
                                scaleX = 1f - distanceFromCenter * 0.55f
                                scaleY = 1f - distanceFromCenter * 0.3f
                                alpha = 0.18f * (1f - distanceFromCenter)
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                    Image(
                        painter = painterResource(AppIconCatalog.default.previewResId),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(112.dp)
                            .graphicsLayer {
                                translationY = iconOffsetY.value
                                scaleX = iconScale.value
                                scaleY = iconScale.value
                                rotationZ = iconRotation.value
                            }
                            .shadow(18.dp, RoundedCornerShape(26.dp))
                            .clip(RoundedCornerShape(26.dp))
                    )
                }
            }
        }
    }
}
