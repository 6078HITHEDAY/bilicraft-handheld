package com.bilicraft.handheld.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bilicraft.handheld.appicon.AppIconCatalog
import com.bilicraft.handheld.ui.nav.AppRoutes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 根导航：NavHost 承载 login / main；开屏动画为 overlay。
 */
@Composable
fun AppRoot(vm: MainViewModel) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val loginOverlay by vm.loginOverlay.collectAsStateWithLifecycle()
    var showSplash by rememberSaveable { mutableStateOf(true) }
    val navController = rememberNavController()
    val start = if (loggedIn && !loginOverlay) AppRoutes.MAIN else AppRoutes.LOGIN

    LaunchedEffect(loggedIn, loginOverlay) {
        val target = if (loggedIn && !loginOverlay) AppRoutes.MAIN else AppRoutes.LOGIN
        val current = navController.currentDestination?.route
        if (current != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = start
        ) {
            composable(AppRoutes.LOGIN) { LoginScreen(vm) }
            composable(AppRoutes.MAIN) { MainScreen(vm) }
        }

        if (showSplash) {
            SplashOverlay(onFinished = { showSplash = false })
        }
    }
}

@Composable
private fun SplashOverlay(onFinished: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val dropDistance = with(density) { maxHeight.toPx() * 0.7f }
        val firstBounceHeight = with(density) { 58.dp.toPx() }
        val secondBounceHeight = with(density) { 24.dp.toPx() }
        val thirdBounceHeight = with(density) { 8.dp.toPx() }
        val shadowOffset = with(density) { 66.dp.toPx() }
        val taglineOffset = with(density) { 104.dp.toPx() }
        val taglineRiseDistance = with(density) { 12.dp.toPx() }
        val iconOffsetY = remember(dropDistance) { Animatable(-dropDistance) }
        val iconScale = remember { Animatable(0.78f) }
        val iconRotation = remember { Animatable(-8f) }
        val rippleProgress = remember { Animatable(0f) }
        val taglineAlpha = remember { Animatable(0f) }
        val taglineOffsetY = remember(taglineRiseDistance) { Animatable(taglineRiseDistance) }
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
            delay(100)
            coroutineScope {
                launch {
                    rippleProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 1_450, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    iconScale.animateTo(
                        targetValue = 1.04f,
                        animationSpec = tween(durationMillis = 1_050, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    taglineAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    taglineOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing)
                    )
                }
            }
            delay(180)
            overlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 520, easing = FastOutLinearInEasing)
            )
            onFinished()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = overlayAlpha.value }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            val distanceFromCenter = (-iconOffsetY.value / dropDistance).coerceIn(0f, 1f)
            repeat(3) { rippleIndex ->
                val rippleDelay = rippleIndex * 0.16f
                val staggeredProgress = (
                    (rippleProgress.value - rippleDelay) / (1f - rippleDelay)
                ).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(132.dp)
                        .graphicsLayer {
                            val rippleScale = 0.82f + staggeredProgress * 1.85f
                            scaleX = rippleScale
                            scaleY = rippleScale
                            alpha = 0.2f * 4f * staggeredProgress * (1f - staggeredProgress)
                        }
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                )
            }
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
                contentDescription = "Bilicraft 掌机",
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
            Text(
                text = "足不出户 · 看遍碧玺事",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.4.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        translationY = taglineOffset + taglineOffsetY.value
                        alpha = taglineAlpha.value
                    }
            )
        }
    }
}
