package dev.endlesssea.player

import android.content.Context
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * §thermique (conversation 1) — surveille la **pression thermique** de
 * l'appareil pour réduire l'upscaling avant que le décodeur ne saccade.
 *
 * Android expose l'état depuis Android 10 (`PowerManager.getCurrentThermalStatus`) :
 *
 *  - 0 NONE / 1 LIGHT  → tout va bien ;
 *  - 2 MODERATE        → on évite la qualité maximale ;
 *  - 3 SEVERE          → on redescend d'un cran ;
 *  - 4 CRITICAL / 5+   → upscaling coupé.
 *
 * La surveillance ne consomme rien de perceptible : une lecture toutes les
 * 10 secondes, sans wakelock.
 */
class ThermalMonitor(
    context: Context,
    private val scope: CoroutineScope,
) {

    private val power = context.applicationContext
        .getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _status = MutableStateFlow(currentStatus())
    val status: StateFlow<Int> = _status

    /** Libellé lisible, affiché dans la surimpression de statistiques. */
    val label: String
        get() = when (_status.value) {
            0 -> "normal"
            1 -> "léger"
            2 -> "modéré"
            3 -> "sévère"
            4 -> "critique"
            5 -> "urgence"
            else -> "arrêt"
        }

    private fun currentStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { power?.currentThermalStatus ?: 0 }.getOrDefault(0)
        } else 0

    /** Démarre la surveillance (10 s) ; sans effet avant Android 10. */
    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                _status.value = currentStatus()
                delay(10_000)
            }
        }
    }
}

/** Niveau d'upscaling de la conversation 1, du plus léger au plus lourd. */
enum class UpscalingLevel(val id: String, val label: String, val scale: Float, val sharpen: Float) {
    OFF("off", "Désactivé", 1f, 0f),
    AUTO("auto", "Auto", 1.5f, 0.6f),
    PERFORMANCE("performance", "Performance", 2f, 0.35f),
    QUALITY("quality", "Qualité", 2f, 1f);

    companion object {
        fun of(id: String?): UpscalingLevel = entries.firstOrNull { it.id == id } ?: OFF

        /**
         * Niveau réduit quand l'appareil chauffe : qualité → performance →
         * auto → désactivé. [steps] = nombre de crans à descendre.
         */
        fun downgrade(level: UpscalingLevel, steps: Int = 1): UpscalingLevel {
            val index = (level.ordinal - steps.coerceAtLeast(0)).coerceAtLeast(0)
            return entries[index]
        }
    }
}
