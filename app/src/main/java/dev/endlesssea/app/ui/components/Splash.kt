package dev.endlesssea.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.endlesssea.app.R
import dev.endlesssea.app.di.AppPrefs
import kotlinx.coroutines.delay

/**
 * Écran de démarrage « pas trop lourd » : logo qui grandit en fondu (~400 ms)
 * + titre qui apparaît, puis main (≈800 ms au total). Couleurs originales du logo, sans recoloration.
 */
@Composable
fun SplashScreen(logoId: String = dev.endlesssea.app.branding.AppLogos.DEFAULT_ID, onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    var titleVisible by remember { mutableStateOf(false) }
    var gone by remember { mutableStateOf(false) }

    val motion = dev.endlesssea.app.ui.motion.LocalAppMotion.current
    LaunchedEffect(motion.enabled) {
        if (!motion.enabled) { onFinished(); return@LaunchedEffect }
        visible = true
        delay(180)
        titleVisible = true
        delay(520)
        gone = true
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.72f,
        animationSpec = tween(420), label = "splashScale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(420), label = "splashAlpha",
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = tween(360), label = "splashTitle",
    )
    val globalAlpha by animateFloatAsState(
        targetValue = if (gone) 0f else 1f,
        animationSpec = tween(220), label = "splashOut",
    )

    Box(
        Modifier
            .fillMaxSize()
            .alpha(globalAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(dev.endlesssea.app.branding.AppLogos.resolve(logoId).drawable),
                contentDescription = "EndlessSea",
                modifier = Modifier
                    .size(168.dp)
                    .scale(logoScale)
                    .alpha(logoAlpha),
                // Preserve the original artwork, independently of the interface accent.
            )
            Text(
                "EndlessSea",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 14.dp).alpha(titleAlpha),
            )
        }
    }
}
