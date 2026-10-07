package dev.endlesssea.app.tracking

import dev.endlesssea.data.db.TrackerAccountEntity
import dev.endlesssea.data.db.TrackerDao
import dev.endlesssea.data.db.TrackerLinkEntity
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
) {

    private val services: List<TrackerService> = TrackerRegistry.all(http)

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
            dao.upsertAccount(entity.copy(lastError = e.message))
            TrackerRegistry.log("connect", "$id : ${e.message}")
            return@withContext null
        } ?: run {
            dao.upsertAccount(entity.copy(lastError = "Identifiants refusés"))
            return@withContext null
        }
        // MAL : échéance connue (~1 h) → le rafraîchissement devient automatique.
        val expires = if (id == "MAL") System.currentTimeMillis() + 55L * 60L * 1000L else 0L
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
        dao.account(id)?.takeIf { it.enabled }

    /** Renouvelle le jeton MAL s'il est sur le point d'expirer. */
    private suspend fun freshAccount(account: TrackerAccountEntity): TrackerAccountEntity {
        if (account.service != "MAL") return account
        if (account.expiresAt == 0L || account.expiresAt > System.currentTimeMillis() + 60_000L) {
            return account
        }
        val mal = service("MAL") as? MalService ?: return account
        val token = mal.refresh(account) ?: return account
        dao.updateTokens("MAL", token, account.refreshToken, System.currentTimeMillis() + 55L * 60L * 1000L)
        return account.copy(accessToken = token)
    }

    // ---------------------------------------------------------------- recherche

    /** Recherche sur un service (rattachement manuel) — renvoie [] sans compte. */
    suspend fun search(id: String, query: String): List<TrackerSearchHit> = withContext(Dispatchers.IO) {
        val svc = service(id) ?: return@withContext emptyList()
        val account = activeAccount(id) ?: return@withContext emptyList()
        runCatching { svc.search(freshAccount(account), query) }.getOrElse { e ->
            TrackerRegistry.log("search", "$id : ${e.message}")
            emptyList()
        }
    }

    // ---------------------------------------------------------------- rattachement

    suspend fun link(mediaId: String, service: String, hit: TrackerSearchHit, status: String = "PLANNING") =
        withContext(Dispatchers.IO) {
            dao.upsertLink(
                TrackerLinkEntity(
                    mediaId = mediaId,
                    service = service,
                    remoteId = hit.remoteId,
                    title = hit.title,
                    totalEpisodes = hit.totalEpisodes ?: 0,
                    progress = 0,
                    status = status,
                ),
            )
        }

    suspend fun unlink(mediaId: String) = withContext(Dispatchers.IO) { dao.deleteLink(mediaId) }

    suspend fun linkOf(mediaId: String): TrackerLinkEntity? =
        withContext(Dispatchers.IO) { dao.link(mediaId) }

    /**
     * §suivi-automatique : marque l'épisode courant comme vu (progression +1).
     * Idempotent : repasser la fin du même épisode ne le compte pas deux fois.
     * Toujours écrit en local, même si le service est injoignable.
     */
    suspend fun markEpisodeWatched(mediaId: String, episodeId: String): Boolean =
        withContext(Dispatchers.IO) {
            val link = dao.link(mediaId) ?: return@withContext false
            if (link.lastEpisodeId == episodeId) return@withContext false
            val next = link.progress + 1
            val total = link.totalEpisodes.takeIf { it > 0 }
            val status = if (total != null && next >= total) "COMPLETED" else "WATCHING"
            dao.upsertLink(
                link.copy(
                    progress = next,
                    status = status,
                    lastEpisodeId = episodeId,
                    pendingSync = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            push(link.copy(progress = next, status = status), next, total, status)
        }

    /** Suivi manuel : « marquer l'épisode suivant vu » et changement de statut. */
    suspend fun setProgress(mediaId: String, progress: Int, status: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            val link = dao.link(mediaId) ?: return@withContext false
            val total = link.totalEpisodes.takeIf { it > 0 }
            val newStatus = status ?: if (total != null && progress >= total) "COMPLETED" else "WATCHING"
            dao.upsertLink(
                link.copy(
                    progress = progress,
                    status = newStatus,
                    pendingSync = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            push(link.copy(progress = progress, status = newStatus), progress, total, newStatus)
        }

    /** Pousse l'état vers le service ; en cas d'échec le drapeau reste levé. */
    private suspend fun push(
        link: TrackerLinkEntity,
        progress: Int,
        total: Int?,
        status: String,
    ): Boolean {
        val account = activeAccount(link.service) ?: return false
        val svc = service(link.service) ?: return false
        val ok = runCatching {
            svc.pushProgress(freshAccount(account), link.remoteId, progress, total, status)
        }.getOrElse { e ->
            dao.setLastError(link.service, e.message)
            TrackerRegistry.log("push", "${link.service} : ${e.message}")
            false
        }
        if (ok) {
            dao.setLastError(link.service, null)
            dao.link(link.mediaId)?.let { dao.upsertLink(it.copy(pendingSync = false)) }
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
        dao.pendingLinks().forEach { link ->
            val total = link.totalEpisodes.takeIf { it > 0 }
            if (push(link, link.progress, total, link.status)) done++
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
        runCatching { svc.details(freshAccount(account), remoteIdWithKind) }.getOrNull()
    }

    /** Recherche TMDB (sert à trouver l'identifiant à partir du titre). */
    suspend fun searchTmdb(query: String): List<TrackerSearchHit> = search("TMDB", query)

    /** Nombre de fiches rattachées à un service (affiché dans les Paramètres). */
    fun linksCount(service: String): Flow<Int> = dao.observeLinkCount(service)

    /** Premier lien connu, pratique pour l'affichage de la fiche. */
    suspend fun firstLink(): TrackerLinkEntity? = withContext(Dispatchers.IO) {
        dao.observeLinks().first().firstOrNull()
    }
}
