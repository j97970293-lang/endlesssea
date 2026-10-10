package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadParallelismTest {
    @Test
    fun `data saver and low memory use a single connection`() {
        assertThat(effectiveDownloadParallelism(3, 6, dataSaver = true, availableMemoryBytes = 2_000_000_000))
            .isEqualTo(DownloadParallelism(1, 1))
        assertThat(effectiveDownloadParallelism(3, 6, dataSaver = false, availableMemoryBytes = 80L * 1024 * 1024))
            .isEqualTo(DownloadParallelism(1, 1))
    }

    @Test
    fun `normal memory keeps the user choice`() {
        assertThat(effectiveDownloadParallelism(2, 4, dataSaver = false, availableMemoryBytes = 800L * 1024 * 1024))
            .isEqualTo(DownloadParallelism(2, 4))
    }
}
