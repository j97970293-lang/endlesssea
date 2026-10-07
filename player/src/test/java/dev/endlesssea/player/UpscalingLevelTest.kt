package dev.endlesssea.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * §upscale-niveaux (conversation 1) — les niveaux et la **garde thermique** :
 * plus l'appareil chauffe, plus on redescend, sans jamais passer sous « off ».
 */
class UpscalingLevelTest {

    @Test
    fun `les niveaux vont du plus leger au plus lourd`() {
        assertThat(UpscalingLevel.entries.map { it.id })
            .containsExactly("off", "auto", "performance", "quality")
            .inOrder()
        assertThat(UpscalingLevel.OFF.scale).isEqualTo(1f)
        assertThat(UpscalingLevel.QUALITY.scale).isEqualTo(2f)
    }

    @Test
    fun `un identifiant inconnu retombe sur desactive`() {
        assertThat(UpscalingLevel.of("n_importe_quoi")).isEqualTo(UpscalingLevel.OFF)
        assertThat(UpscalingLevel.of(null)).isEqualTo(UpscalingLevel.OFF)
    }

    @Test
    fun `la reduction thermique descend cran par cran`() {
        assertThat(UpscalingLevel.downgrade(UpscalingLevel.QUALITY, 1))
            .isEqualTo(UpscalingLevel.PERFORMANCE)
        assertThat(UpscalingLevel.downgrade(UpscalingLevel.PERFORMANCE, 1))
            .isEqualTo(UpscalingLevel.AUTO)
        assertThat(UpscalingLevel.downgrade(UpscalingLevel.AUTO, 1))
            .isEqualTo(UpscalingLevel.OFF)
    }

    @Test
    fun `une chauffe critique coupe directement l'upscaling`() {
        assertThat(UpscalingLevel.downgrade(UpscalingLevel.QUALITY, 3))
            .isEqualTo(UpscalingLevel.OFF)
    }

    @Test
    fun `on ne descend jamais sous desactive`() {
        assertThat(UpscalingLevel.downgrade(UpscalingLevel.OFF, 5))
            .isEqualTo(UpscalingLevel.OFF)
    }
}
