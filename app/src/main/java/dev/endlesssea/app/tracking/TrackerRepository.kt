package dev.endlesssea.app.tracking

import dev.endlesssea.data.db.TrackerAccountEntity
import dev.endlesssea.data.db.TrackerDao
import dev.endlesssea.data.db.TrackerLinkEntity
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §suivi (conversation 11) — façade unique pour les services de suivi.
 *
 * Principes :
 *  · **jamais de réseau sans compte** : si aucun jeton n'est enregistré, aucune
 *    requête n'est émise (hors recherche TMDB explicitement demandée) ;
 *  · la progression est **écrite en local d'abord** puis poussée au service —
 *    un épisode vu hors ligne est donc marqué, et le `pendingSync` indique
 *    simplement qu'il reste à synchroniser ;
 *  · chaque échec réseau est mémorisé sur le rattachement, jamais fatal.
 */
@Singleton
class TrackerRepository @Inject constructor(
    private val dao: TrackerDao,
    http: OkHttpClient,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
) {

    private val services: List<TrackerService> = TrackerRegistry.all(http)

    val defaultService get() = prefs.defaultTrackerService

    val accounts: Flow<List<TrackerAccountEntity>> = dao.observeAccounts()
    val links: Flow<List<TrackerLinkEntity>> = dao.observeLinks()

    private fun service(id: String): TrackerService? = services.firstOrNull { it.id == id }

    // ---------------------------------------------------------------- comptes

    /** Enregistre/changement un jeton ou une clé d'API après vérification. */
    suspend fun connect(
        id: String,
        token: String? = null,
        apiKey: String? = null,
        clientId: String? = null,
        refreshToken: String? = null,
        enabled: Boolean = true,
    ): String? = withContext(Dispatchers.IO) {
        val svc = service(id) ?: return@withContext null
        val entity = TrackerAccountEntity(
            service = id,
            accessToken = token?.takeIf { it.isNotBlank() },
            apiKey = apiKey?.takeIf { it.isNotBlank() },
            clientId = clientId?.takeIf { it.isNotBlank() },
            refreshToken = refreshToken?.takeIf { it.isNotBlank() },
            enabled = enabled,
        )
        val name = runCatching { svc.whoAmI(entity) }.getOrElse { e ->
            // Message court et actionnable : « Jeton refusé », « HTTP 500 »…
            dao.account(id)?.let { dao.setLastError(id, e.message) }
            TrackerRegistry.log("connect", "$id : ${e.message}")
            return@withContext null
        } ?: run {
            dao.account(id)?.let { dao.setLastError(id, "Identifiants refusés") }
            return@withContext null
        }
        // Un jeton collé n'indique pas sa date d'expiration : ne pas l'inventer.
        val expires = 0L
        dao.upsertAccount(entity.copy(userName = name, expiresAt = expires, lastError = null))
        name
    }

    suspend fun disconnect(id: String) = withContext(Dispatchers.IO) {
        dao.deleteAccount(id)
        // On garde les rattachements : rebrancher le même compte reprend le suivi.
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        dao.setEnabled(id, enabled)
    }

    suspend fun account(id: String): TrackerAccountEntity? = withContext(Dispatchers.IO) { dao.account(id) }

    /** Compte connecté ET actif (un compte désactivé n'envoie rien). */
    private suspend fun activeAccount(id: String): TrackerAccountEntity? =
        dao.account(id)?.takeIf { it.enabled && it.userName.isNotBlank() }

    private suspend fun renewMal(account: TrackerAccountEntity): TrackerAccountEntity? {
        val mal = service("MAL") as? MalService ?: return null
        val token = mal.refresh(account) ?: return null
        dao.updateTokens("MAL", token.accessToken, token.refreshToken, token.expiresAt)
        return account.copy(accessToken = token.accessToken, refreshToken = token.refreshToken, expiresAt = token.expiresAt)
    }

    /** Une échéance inconnue est renouvelée sur HTTP 401, pas sur une durée inventée. */
    private suspend fun <T> withFreshAccount(account: TrackerAccountEntity, action: suspend (TrackerAccountEntity) -> T): T {
        val fresh = if (account.service == "MAL" && account.expiresAt > 0L &&
            account.expiresAt <= System.currentTimeMillis() + 60_000L) renewMal(account) ?: account else account
        return try {
            action(fresh)
        } catch (error: TrackerError) {
            if (fresh.service != "MAL" || error.httpCode != 401) throw error
            val renewed = renewMal(fresh) ?: throw error
            action(renewed)
        }
    }

    // ---------------------------------------------------------------- recherche

    /** Errors remain distinguishable from a successful empty result for metadata selection. */
    suspend fun searchChecked(id: String, query: String): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val svc = service(id) ?: throw TrackerError("Service inconnu")
        val account = activeAccount(id) ?: throw TrackerError("Connectez ou réactivez ce compte")
        withFreshAccount(account) { fresh ->
            if (svc is TmdbService) svc.search(fresh, query, prefs.metadataLanguage.value)
            else svc.search(fresh, query)
        }
    }

    suspend fun search(id: String, query: String): List<TrackerSearchHit> = try {
        searchChecked(id, query)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) {
        TrackerRegistry.log("search", "$id : ${e.message}")
        emptyList()
    }

    // ---------------------------------------------------------------- rattachement

    suspend fun link(
        mediaId: String, service: String, hit: TrackerSearchHit, status: String = "PLANNING",
        autoMatchEpisodes: Boolean = false, autoMatchSeason: Int? = null,
    ) =
        withContext(Dispatchers.IO) {
            if (hit.remoteId.isBlank() || service == "TMDB") return@withContext false
            progressMutex.withLock {
                val existing = dao.link(mediaId)?.takeIf { it.service == service && it.remoteId == hit.remoteId }
                val account = activeAccount(service)
                val remote = if (account == null) null else try {
                    withFreshAccount(account) { service(service)?.readProgress(it, hit.remoteId) }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    dao.setLastError(service, "Impossible de lire la progression : ${e.message}")
                    return@withLock false
                }
                val progress = maxOf(existing?.progress ?: 0, remote?.progress ?: 0)
                dao.upsertLink(TrackerLinkEntity(
                    mediaId = mediaId, service = service, remoteId = hit.remoteId, title = hit.title,
                    totalEpisodes = hit.totalEpisodes ?: existing?.totalEpisodes ?: 0,
                    progress = progress,
                    status = if (remote != null && remote.progress >= (existing?.progress ?: 0)) remote.status else existing?.status ?: status,
                    autoMatchEpisodes = autoMatchEpisodes, autoMatchSeason = autoMatchSeason,
                    pendingSync = existing?.pendingSync ?: false,
                ))
                true
            }
        }

    suspend fun unlink(mediaId: String) = withContext(Dispatchers.IO) { dao.deleteLink(mediaId) }

    suspend fun linkOf(mediaId: String): TrackerLinkEntity? =
        withContext(Dispatchers.IO) { dao.link(mediaId) }

    private val progressMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun setEpisodeMatching(mediaId: String, enabled: Boolean, season: Int?) =
        withContext(Dispatchers.IO) {
            progressMutex.withLock {
                dao.link(mediaId)?.let {
                    dao.upsertLink(it.copy(autoMatchEpisodes = enabled, autoMatchSeason = season))
                }
            }
        }

    /** À 90 %, applique le numéro de la saison choisie ; une relecture ne compte jamais deux fois. */
    suspend fun markEpisodeWatched(
        mediaId: String, episodeId: String, number: Float?, season: Int?,
        service: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        progressMutex.withLock {
            val link = dao.link(mediaId)?.takeIf { it.remoteId.isNotBlank() } ?: return@withLock false
            val next = EpisodeMatching.nextProgress(
                link.autoMatchEpisodes, link.autoMatchSeason, season, number,
                link.progress, link.totalEpisodes,
            ) ?: return@withLock false
            val total = link.totalEpisodes.takeIf { it > 0 }
            val status = if (total != null && next == total) "COMPLETED" else "WATCHING"
            val updated = link.copy(
                progress = next, status = status, lastEpisodeId = episodeId,
                pendingSync = true, updatedAt = System.currentTimeMillis(),
            )
            dao.upsertLink(updated)
            push(updated, next, total, status)
        }
    }

    /** Suivi manuel : « marquer l'épisode suivant vu » et changement de statut. */
    suspend fun setProgress(mediaId: String, progress: Int, status: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            progressMutex.withLock {
                val link = dao.link(mediaId) ?: return@withLock false
                if (progress < 0 || (link.totalEpisodes > 0 && progress > link.totalEpisodes)) return@withLock false
                val total = link.totalEpisodes.takeIf { it > 0 }
                val newStatus = status ?: if (total != null && progress >= total) "COMPLETED" else "WATCHING"
                val updated = link.copy(
                    progress = progress, status = newStatus, pendingSync = true,
                    updatedAt = System.currentTimeMillis(),
                )
                dao.upsertLink(updated)
                push(updated, progress, total, newStatus)
            }
        }

    /** Pousse l'état vers le service ; en cas d'échec le drapeau reste levé. */
    private suspend fun push(
        link: TrackerLinkEntity,
        progress: Int,
        total: Int?,
        status: String,
    ): Boolean {
        if (link.remoteId.isBlank()) return false
        val account = activeAccount(link.service) ?: return false
        val svc = service(link.service) ?: return false
        var reconciled = link
        val ok = runCatching {
            withFreshAccount(account) { fresh ->
                val remote = svc.readProgress(fresh, link.remoteId)
                if (remote != null && remote.progress > progress) {
                    reconciled = link.copy(progress = remote.progress, status = remote.status)
                    dao.upsertLink(reconciled)
                }
                svc.pushProgress(fresh, link.remoteId, reconciled.progress, total, reconciled.status)
            }
        }.getOrElse { e ->
            dao.setLastError(link.service, e.message)
            TrackerRegistry.log("push", "${link.service} : ${e.message}")
            false
        }
        if (ok) {
            dao.setLastError(link.service, null)
            dao.acknowledge(link.mediaId, link.remoteId, link.service, reconciled.progress, reconciled.status, reconciled.updatedAt)
        }
        return ok
    }

    /**
     * Rejoue les mises à jour en attente (appelé à l'ouverture de l'écran
     * Comptes ou après une reconnexion) : un épisode marqué dans le train part
     * une fois le réseau revenu.
     */
    suspend fun syncPending(): Int = withContext(Dispatchers.IO) {
        var done = 0
        progressMutex.withLock {
            dao.pendingLinks().forEach { link ->
                val total = link.totalEpisodes.takeIf { it > 0 }
                if (push(link, link.progress, total, link.status)) done++
            }
        }
        done
    }

    // ---------------------------------------------------------------- métadonnées

    /**
     * §bandes-annonces : complète affiche/bannière/bande-annonce d'une fiche via
     * TMDB (uniquement si une clé est enregistrée — sinon null, aucun appel).
     */
    suspend fun enrich(
        remoteIdWithKind: String,
    ): TrackerDetails? = withContext(Dispatchers.IO) {
        val account = activeAccount("TMDB") ?: return@withContext null
        val svc = service("TMDB") ?: return@withContext null
        runCatching {
            if (svc is TmdbService) svc.details(account, remoteIdWithKind, prefs.metadataLanguage.value)
            else svc.details(account, remoteIdWithKind)
        }.getOrNull()
    }

    /** Read-only pull. No remote list entry is created and no pending local write is overwritten. */
    suspend fun pullLibrary(id: String): List<RemoteLibraryEntry> = withContext(Dispatchers.IO) {
        val account = activeAccount(id) ?: throw TrackerError("Connectez et activez AniList dans Comptes & suivi")
        val svc = service(id) ?: throw TrackerError("Service indisponible")
        val entries = kotlinx.coroutines.withTimeout(120_000) { withFreshAccount(account) { svc.library(it) } }
        if (activeAccount(id)?.accessToken != account.accessToken) throw TrackerError("Compte changé pendant l'import ; réessayez")
        progressMutex.withLock {
            val byId = entries.associateBy { it.id }
            dao.observeLinks().first().filter { it.service == id && !it.pendingSync }.forEach { local ->
                byId[local.remoteId]?.let { remote ->
                    dao.upsertLink(local.copy(progress = remote.progress, status = remote.status,
                        totalEpisodes = remote.total ?: local.totalEpisodes, updatedAt = System.currentTimeMillis()))
                }
            }
        }
        dao.setLastError(id, null)
        entries
    }

    suspend fun metadata(serviceId: String, remoteId: String): TrackerDetails? = withContext(Dispatchers.IO) {
        val svc = service(serviceId) ?: throw TrackerError("Service indisponible")
        val account = activeAccount(serviceId) ?: throw TrackerError("Service non connecté")
        withFreshAccount(account) { fresh ->
            if (svc is TmdbService) svc.details(fresh, remoteId, prefs.metadataLanguage.value)
            else svc.details(fresh, remoteId)
        }
    }

    /** Recherche TMDB (sert à trouver l'identifiant à partir du titre). */
    suspend fun searchTmdb(query: String): List<TrackerSearchHit> = search("TMDB", query)

    /** Nombre de fiches rattachées à un service (affiché dans les Paramètres). */
    fun linksCount(service: String): Flow<Int> = dao.observeLinkCount(service)

    /** Premier lien connu, pratique pour l'affichage de la fiche. */
    suspend fun firstLink(): TrackerLinkEntity? = withContext(Dispatchers.IO) {
        dao.observeLinks().first().firstOrNull()
    }

    /** §tracker-choose : récupère tous les services activés pour permettre à l'utilisateur de choisir. */
    suspend fun getEnabledServices(): List<TrackerAccountEntity> = withContext(Dispatchers.IO) {
        dao.observeAccounts().first().filter { it.enabled && it.userName.isNotBlank() }
    }

    /** §tracker-choose : définit le service par défaut pour le tracking automatique. */
    suspend fun setDefaultService(service: String) = withContext(Dispatchers.IO) {
        prefs.setDefaultTrackerService(service)
    }

    /** §tracker-choose : récupère le service par défaut pour le tracking automatique. */
    fun getDefaultService(): String? = prefs.defaultTrackerService.value
}
