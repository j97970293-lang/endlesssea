package dev.endlesssea.downloader

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The service process can see the same pause / resume / restart contract as the unit tests. */
@RunWith(AndroidJUnit4::class)
class DownloadServiceInstrumentedTest {
    @Test
    fun serviceExposesTheLifecycleActions() {
        assertEquals(DownloadServiceCommands.PAUSE, DownloadService.ACTION_PAUSE)
        assertEquals(DownloadServiceCommands.RESUME, DownloadService.ACTION_RESUME)
        assertEquals("resume-queue", DownloadServiceCommands.describe(DownloadService.ACTION_RESUME_ALL, false))
    }
}
