package dev.endlesssea.app.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.app.ui.library.LocalVideoUi
import dev.endlesssea.data.db.WatchHistoryDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Resume from Home without relying on a prior visit to the local-library screen. */
@HiltViewModel
class LocalResumeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val history: WatchHistoryDao,
    private val prefs: AppPrefs,
) : ViewModel() {
    private var opening = false

    fun resume(uri: String, onReady: (List<LocalVideoUi>, LocalVideoUi) -> Unit, onError: (String) -> Unit) {
        if (opening) return
        opening = true
        viewModelScope.launch {
            try {
                val (queue, selected) = withContext(Dispatchers.IO) {
                    val document = Uri.parse(uri)
                    context.contentResolver.openFileDescriptor(document, "r")?.use { }
                        ?: error("Fichier introuvable")
                    val cached = LocalLibraryCache.files.value
                    val saved = history.byEpisode(uri)
                    val folder = cached.firstOrNull { it.uri == uri }?.parentUri?.takeIf { it.isNotBlank() }
                        ?: saved?.mediaId?.takeIf { it.startsWith("local:") }?.removePrefix("local:")?.takeIf { it.isNotBlank() }
                        ?: parentDocument(document)
                    val metadataByUri = prefs.localFileMetadataSnapshot()
                    val siblings = when {
                        folder == null -> emptyList()
                        DocumentsContract.isTreeUri(Uri.parse(folder)) ->
                            LocalVideos.scanAsync(context, folder, includeHidden = prefs.showHiddenFiles.value, folderOnly = true)
                                .map { file ->
                                    val metadata = metadataByUri[file.uri] ?: AppPrefs.LocalFileMeta()
                                    LocalVideoUi(file.uri, file.displayName, file.parentUri, file.sizeBytes,
                                        customTitle = metadata.title, customCoverUri = metadata.coverUri,
                                        introStartSec = metadata.introStartSec, introEndSec = metadata.introEndSec, outroStartSec = metadata.outroStartSec)
                                }
                        else -> cached.filter { it.parentUri == folder }
                    }
                    val metadata = metadataByUri[uri] ?: AppPrefs.LocalFileMeta()
                    val selected = siblings.firstOrNull { it.uri == uri } ?: cached.firstOrNull { it.uri == uri }
                        ?: LocalVideoUi(uri, LocalNames.fileName(uri), folder.orEmpty(), 0,
                            durationMs = saved?.durationMs, customTitle = metadata.title, customCoverUri = metadata.coverUri,
                            introStartSec = metadata.introStartSec, introEndSec = metadata.introEndSec, outroStartSec = metadata.outroStartSec)
                    localPlaybackQueue(siblings, selected) to selected
                }
                LocalLibraryCache.publish((LocalLibraryCache.files.value.filter { if (selected.parentUri.isBlank()) it.uri != selected.uri else it.parentUri != selected.parentUri } + queue).distinctBy { it.uri })
                onReady(queue, selected)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                onError("Impossible de reprendre ce fichier. Vérifie sa présence et l'autorisation du dossier dans la bibliothèque.")
            } finally { opening = false }
        }
    }

    private fun parentDocument(uri: Uri): String? = runCatching {
        if (!DocumentsContract.isTreeUri(uri)) return@runCatching null
        val id = DocumentsContract.getDocumentId(uri)
        if ('/' !in id) return@runCatching null
        DocumentsContract.buildDocumentUriUsingTree(uri, id.substringBeforeLast('/')).toString()
    }.getOrNull()
}
