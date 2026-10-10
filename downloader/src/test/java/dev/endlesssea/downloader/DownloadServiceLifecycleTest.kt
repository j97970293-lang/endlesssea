package dev.endlesssea.downloader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DownloadServiceLifecycleTest {
    @Test
    fun `pause resume and restart map to distinct commands`() {
        assertThat(DownloadServiceCommands.describe(DownloadServiceCommands.PAUSE, true)).isEqualTo("pause")
        assertThat(DownloadServiceCommands.describe(DownloadServiceCommands.RESUME, true)).isEqualTo("resume")
        assertThat(DownloadServiceCommands.describe(DownloadServiceCommands.RESUME_ALL, false)).isEqualTo("resume-queue")
        assertThat(DownloadServiceCommands.describe(DownloadServiceCommands.CANCEL, false)).isEqualTo("ignore")
        assertThat(DownloadServiceCommands.describe(null, false)).isEqualTo("start")
    }
}
