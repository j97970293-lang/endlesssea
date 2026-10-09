package dev.endlesssea.app.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.ui.library.LocalVideoUi
import dev.endlesssea.core.util.FileNames
import dev.endlesssea.data.db.EpisodeDao
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class LocalDetailsCatalog(val media: MediaEntity, val files: List<LocalVideoUi>)

/** Supplies local media to the SAME details/episode/playback model as downloaded and online media. */
class LocalDetailsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPrefs,
    private val mediaDao: MediaDao,
    private val episodeDao: EpisodeDao,
) {
    internal suspend fun load(folder: String): LocalDetailsCatalog = withContext(Dispatchers.IO) {
        val id = LocalMediaIds.series(folder)
        val previous = mediaDao.byId(id)
        val cached = LocalLibraryCache.files.value.filter { it.parentUri == folder }
        val scanned = LocalVideos.scanAsync(context, folder, prefs.showHiddenFiles.value, folderOnly = true)
            .filter { it.parentUri == folder }
        val imported = episodeDao.ofMedia(id).first().associateBy { it.id }
        val meta = LocalVideos.seriesMeta[folder] ?: SeriesMeta()
        val files = (if (scanned.isNotEmpty()) scanned.map { f ->
            LocalVideoUi(uri = f.uri, name = LocalNames.fileName(f.displayName), parentUri = folder,
                sizeBytes = f.sizeBytes, durationMs = cached.firstOrNull { it.uri == f.uri }?.durationMs)
        } else cached.filter { DownloadLocator.exists(context, it.uri) }).map { f ->
            val manual = prefs.localFileMeta(f.uri)
            val ep = imported[f.uri]
            f.copy(customTitle = manual.title ?: LocalVideos.episodeNumber(f.name)?.let { meta.episodeTitles[it] },
                customCoverUri = manual.coverUri, introStartSec = manual.introStartSec,
                introEndSec = manual.introEndSec, outroStartSec = manual.outroStartSec,
                matchedTitle = ep?.title, matchedSeason = ep?.season, matchedNumber = ep?.number)
        }.distinctBy { it.uri }
        val sorted = files.firstOrNull()?.let { localPlaybackQueue(files, it) }.orEmpty()
        val title = previous?.customTitle ?: previous?.title ?: meta.title ?: sorted.firstOrNull()?.folderName ?: LocalNames.pretty(folder)
        val base = previous ?: MediaEntity(id = id, extensionId = "local", type = "ANIME", title = title,
            titleKey = FileNames.normalizedKey(title), synopsis = meta.description,
            externalIdsJson = org.json.JSONObject().put("local_generated", true)
                .put("local_manual_meta", meta.title != null || meta.description != null || meta.author != null || meta.genres.isNotEmpty()).toString(),
            genresJson = org.json.JSONArray(meta.genres).toString(), studiosJson = org.json.JSONArray(listOfNotNull(meta.author)).toString())
        val media = base.copy(posterUrl = prefs.localFileMeta("folder:$folder").coverUri ?: base.customCoverUri ?: base.posterUrl ?: meta.coverUri)
        if (previous == null) mediaDao.upsertAll(listOf(media))
        LocalLibraryCache.publish(LocalLibraryCache.files.value.filterNot { it.parentUri == folder } + sorted)
        LocalDetailsCatalog(media, sorted)
    }
}
