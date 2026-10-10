package dev.endlesssea.app.extensions

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RepoSeedTest {
    @Test
    fun `a fresh install receives the french repository once`() {
        assertThat(RepoBootstrap.plan(emptyList(), emptyList(), seeded = false))
            .containsExactly(RepoBootstrap.PLUGINS_FR)
    }

    @Test
    fun `removing the repository is remembered`() {
        assertThat(RepoBootstrap.plan(emptyList(), emptyList(), seeded = true)).isEmpty()
    }

    @Test
    fun `a wiped database restores the saved url`() {
        val saved = listOf("https://example.com/index.json")
        assertThat(RepoBootstrap.plan(saved, emptyList(), seeded = true)).containsExactlyElementsIn(saved)
    }
}
