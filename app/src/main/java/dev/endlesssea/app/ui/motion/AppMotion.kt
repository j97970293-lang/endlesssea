package dev.endlesssea.app.ui.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

val LocalAppMotion = staticCompositionLocalOf { MotionPolicy(true, true) }

@Composable
fun AppMotionProvider(enabled: Boolean, content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val owner = LocalLifecycleOwner.current
    fun systemScale() = runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    var scale by remember(resolver) { mutableFloatStateOf(systemScale()) }
    var active by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(resolver, owner) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scale = systemScale() }
        }
        val lifecycle = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (active) scale = systemScale()
        }
        val registered = runCatching {
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        }.isSuccess
        owner.lifecycle.addObserver(lifecycle)
        onDispose {
            if (registered) resolver.unregisterContentObserver(observer)
            owner.lifecycle.removeObserver(lifecycle)
        }
    }
    CompositionLocalProvider(LocalAppMotion provides MotionPolicy.resolve(enabled, scale, active), content = content)
}

/** Read the returned state in drawing/layer code, not at the screen root. */
@Composable
fun rememberMotionPhase(milliseconds: Int, resting: Float = 0f): State<Float> {
    if (!LocalAppMotion.current.loop) return rememberUpdatedState(resting)
    val transition = rememberInfiniteTransition(label = "ambientMotion")
    return transition.animateFloat(0f, 1f,
        animationSpec = infiniteRepeatable(tween(milliseconds, easing = LinearEasing)), label = "phase")
}

fun Modifier.motionReveal(key: Any?): Modifier = composed {
    val policy = LocalAppMotion.current
    val amount = remember(key) { Animatable(if (policy.enabled) 0f else 1f) }
    val offset = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(key, policy.enabled) {
        if (policy.enabled) amount.animateTo(1f, tween(MotionPolicy.ENTER, easing = FastOutSlowInEasing))
        else amount.snapTo(1f)
    }
    graphicsLayer { alpha = amount.value; translationY = offset * (1f - amount.value) }
}

fun Modifier.motionClickable(onClick: () -> Unit): Modifier = composed {
    val policy = LocalAppMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed && policy.enabled) 0.985f else 1f,
        tween(policy.duration(MotionPolicy.PRESS)), label = "surfacePress")
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
}
