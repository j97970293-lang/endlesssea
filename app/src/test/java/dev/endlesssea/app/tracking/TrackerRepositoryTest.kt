package dev.endlesssea.app.tracking

import dev.endlesssea.data.db.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class TrackerRepositoryTest {
    private class MemoryDao : TrackerDao {
        val stored = mutableMapOf<String, TrackerLinkEntity>()
        override fun observeAccounts(): Flow<List<TrackerAccountEntity>> = flowOf(emptyList())
        override suspend fun account(service: String): TrackerAccountEntity? = null
        override suspend fun upsertAccount(account: TrackerAccountEntity) = Unit
        override suspend fun setEnabled(service: String, enabled: Boolean) = Unit
        override suspend fun setLastError(service: String, error: String?) = Unit
        override suspend fun updateTokens(service: String, token: String, refresh: String?, expiresAt: Long) = Unit
        override suspend fun deleteAccount(service: String) = Unit
        override fun observeLinks(): Flow<List<TrackerLinkEntity>> = flowOf(stored.values.toList())
        override suspend fun link(mediaId: String): TrackerLinkEntity? = stored[mediaId]
        override suspend fun upsertLink(link: TrackerLinkEntity) { stored[link.mediaId] = link }
        override suspend fun deleteLink(mediaId: String) { stored.remove(mediaId) }
        override suspend fun acknowledge(mediaId: String, remoteId: String, service: String, progress: Int, status: String, updatedAt: Long) {
            val link = stored[mediaId] ?: return
            if (link.remoteId == remoteId && link.service == service && link.progress == progress && link.status == status && link.updatedAt == updatedAt) stored[mediaId] = link.copy(pendingSync = false)
        }
        override suspend fun pendingLinks(): List<TrackerLinkEntity> = stored.values.filter { it.pendingSync }
        override fun observeLinkCount(service: String): Flow<Int> = flowOf(stored.values.count { it.service == service })
    }
    private fun seed(dao: MemoryDao, auto: Boolean = true) {
        dao.stored["local:folder"] = TrackerLinkEntity("local:folder", "ANILIST", "42", totalEpisodes = 12, autoMatchEpisodes = auto, autoMatchSeason = 1)
    }
    @Test fun offlineProgressIsNumberBasedAndRewatchSafe() = runBlocking {
        val dao = MemoryDao(); seed(dao)
        val repo = TrackerRepository(dao, OkHttpClient(), org.mockito.Mockito.mock(dev.endlesssea.app.di.AppPrefs::class.java))
        assertFalse(repo.markEpisodeWatched("local:folder", "ep3", 3f, 1))
        assertEquals(3, dao.link("local:folder")!!.progress)
        assertTrue(dao.link("local:folder")!!.pendingSync)
        repo.markEpisodeWatched("local:folder", "ep2", 2f, 1)
        repo.markEpisodeWatched("local:folder", "ep3", 3f, 1)
        assertEquals(3, dao.link("local:folder")!!.progress)
        repo.markEpisodeWatched("local:folder", "ep12", 12f, 1)
        assertEquals("COMPLETED", dao.link("local:folder")!!.status)
        assertEquals(0, repo.syncPending())
        assertTrue(dao.link("local:folder")!!.pendingSync)
    }
    @Test fun disablingMatchingStillAllowsManualProgress() = runBlocking {
        val dao = MemoryDao(); seed(dao)
        val repo = TrackerRepository(dao, OkHttpClient(), org.mockito.Mockito.mock(dev.endlesssea.app.di.AppPrefs::class.java))
        repo.setEpisodeMatching("local:folder", false, 1)
        repo.markEpisodeWatched("local:folder", "ep3", 3f, 1)
        assertEquals(0, dao.link("local:folder")!!.progress)
        repo.setProgress("local:folder", 1)
        assertEquals(1, dao.link("local:folder")!!.progress)
        repo.setProgress("local:folder", -1)
        repo.setProgress("local:folder", 13)
        assertEquals(1, dao.link("local:folder")!!.progress)
    }
    @Test fun newlyConfirmedLinkIsManualByDefault() = runBlocking {
        val dao = MemoryDao(); val repo = TrackerRepository(dao, OkHttpClient(), org.mockito.Mockito.mock(dev.endlesssea.app.di.AppPrefs::class.java))
        repo.link("local:folder", "MAL", TrackerSearchHit("42", "Title", 12))
        assertFalse(dao.link("local:folder")!!.autoMatchEpisodes)
    }
}
