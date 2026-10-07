package dev.endlesssea.app.tracking

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.endlesssea.app.di.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §suivi (conversation 11) — point d'entrée unique pour les services de suivi.
 *
 * · les identifiants et les rattachements vivent dans les préférences locales
 *   (aucun serveur intermédiaire, rien ne sort sans action de l'utilisateur) ;
 * · chaque appel réseau est « best effort » : hors ligne, la progression est
 *   mémorisée localement et renvoyée au service à la prochaine occasion ;
 * · TMDB ne sert qu'aux visuels (affiche, bannière, bande-annonce).
 */
@Singleton
class TrackerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    base: OkHttpClient,
    private val prefs: AppPrefs,
) {

    private val anilist = AniListTracker(base)
    private val mal = MyAnimeListTracker(base)
    private val shikimori = ShikimoriTracker(base)
    private val tmdb = TmdbTracker(base)

    val trackers: List<Tracker> = listOf(anilist, mal, shikimori, tmdb)

    /** Dernier compte vérifié par service (affiché dans l'écran « Comptes »). */
    private val _accounts = MutableStateFlow<Map<TrackerId, TrackerAccount>>(emptyMap())
    val accounts: StateFlow<Map<TrackerId, TrackerAccount>> = _accounts

    /** Message d'état lisible (succès/erreur) pour l'interface. */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    init {
        // Les comptes déjà connectés sont revérifiés en tâche de fond : un jeton
        // révoqué côté service disparaît proprement de l'écran.
        _accounts.value = readAccounts().mapNotNull { (id, creds) ->
            creds.userName.takeIf { it.isNotBlank() }?.let { id to TrackerAccount(id = id, userName = it) }
        }.toMap()
    }

    // ---------------------------------------------------------------- comptes

    fun tracker(id: TrackerId): Tracker = trackers.first { it.id == id }

    fun credentials(id: TrackerId): TrackerCredentials = readAccounts()[id] ?: TrackerCredentials()

    fun isConnected(id: TrackerId): Boolean = credentials(id).usable(id)

    fun saveCredentials(id: TrackerId, creds: TrackerCredentials, label: String = "") {
        val all = readAccounts().toMutableMap()
        all[id] = creds
        writeAccounts(all)
        _status.value = label.ifBlank { "${id.label} : identifiants enregistrés." }
    }

    fun disconnect(id: TrackerId) {
        val all = readAccounts().toMutableMap()
        all.remove(id)
        writeAccounts(all)
        _accounts.value = _accounts.value - id
        _status.value = "${id.label} : compte déconnecté (les rattachements restent)."
    }

    /** Vérifie les identifiants et renvoie le pseudo (met à jour l'état affiché). */
    suspend fun verify(id: TrackerId, creds: TrackerCredentials = credentials(id)): TrackerAccount? {
        val account = runCatching { tracker(id).verify(creds) }.getOrElse { e ->
            _status.value = "${id.label} : ${e.message ?: "connexion impossible"}"
            return null
        }
        if (account == null) {
            _status.value = "${id.label} : identifiants refusés."
            return null
        }
        saveCredentials(id, creds.copy(userName = account.userName), "${id.label} : connecté en tant que ${account.userName}.")
        _accounts.value = _accounts.value + (id to account)
        return account
    }

    /** Démarre un parcours OAuth : génère le PKCE MAL au besoin, renvoie l'URL. */
    fun authorizeUrl(id: TrackerId, clientId: String, clientSecret: String = ""): String {
        val redirect = when (id) {
            TrackerId.MAL -> MyAnimeListTracker.SUGGESTED_REDIRECT
            TrackerId.SHIKIMORI -> ShikimoriTracker.SUGGESTED_REDIRECT
            else -> "https://localhost/endlesssea"
        }
        val existing = credentials(id)
        val verifier = if (id == TrackerId.MAL) {
            existing.codeVerifier.ifBlank { MyAnimeListTracker.newCodeVerifier() }
        } else existing.codeVerifier
        val creds = existing.copy(
            clientId = clientId, clientSecret = clientSecret, codeVerifier = verifier,
        )
        saveCredentials(id, creds, "")
        return tracker(id).authorizeUrl(creds, redirect)
    }

    /** Termine un parcours OAuth : échange le code contre un jeton puis vérifie. */
    suspend fun completeAuthorization(id: TrackerId, code: String): TrackerAccount? {
        val redirect = when (id) {
            TrackerId.MAL -> MyAnimeListTracker.SUGGESTED_REDIRECT
            TrackerId.SHIKIMORI -> ShikimoriTracker.SUGGESTED_REDIRECT
            else -> "https://localhost/endlesssea"
        }
        val creds = credentials(id)
        val updated = runCatching { tracker(id).exchangeCode(creds, code, redirect) }.getOrElse { e ->
            _status.value = "${id.label} : ${e.message ?: "échange du code impossible"}"
            return null
        }
        if (updated == null) {
            _status.value = "${id.label} : code refusé ou expiré."
            return null
        }
        return verify(id, updated)
    }

    // ---------------------------------------------------------------- rattachements

    fun links(): Map<String, TrackerLink> = readLinks()
    fun linkFor(mediaId: String, id: TrackerId? = null): TrackerLink? =
        links()[mediaId]?.takeIf { id == null || it.id == id }

    fun link(mediaId: String, hit: TrackerMediaHit, id: TrackerId) {
        val all = readLinks().toMutableMap()
        all[mediaId] = TrackerLink(
            id = id,
            remoteId = hit.remoteId,
            title = hit.title,
            totalEpisodes = hit.episodes,
            progress = all[mediaId]?.takeIf { it.remoteId == hit.remoteId }?.progress ?: 0,
        )
        writeLinks(all)
        _status.value = "Rattaché à ${id.label} : ${hit.title}"
    }

    fun unlink(mediaId: String) {
        val all = readLinks().toMutableMap()
        all.remove(mediaId)
        writeLinks(all)
        _status.value = "Rattachement supprimé."
    }

    /** Recherche un titre sur un service (rattachement manuel depuis la fiche). */
    suspend fun search(id: TrackerId, title: String): List<TrackerMediaHit> =
        withContext(Dispatchers.IO) {
            runCatching { tracker(id).search(credentials(id), title) }.getOrElse { e ->
                _status.value = "${id.label} : ${e.message ?: "recherche impossible"}"
                emptyList()
            }
        }

    /**
     * §suivi — marque un épisode vu sur les services connectés (progression +1).
     * Idempotent : un même épisode local n'est compté qu'une fois, même si le
     * lecteur repasse par-dessus la fin plusieurs fois.
     */
    suspend fun markEpisodeWatched(mediaId: String, episodeId: String): Boolean {
        if (mediaId.isBlank()) return false
        val all = readLinks()
        val link = all[mediaId] ?: return false
        if (link.lastEpisodeId == episodeId) return false
        val creds = credentials(link.id)
        if (!creds.usable(link.id)) return false
        val next = link.progress + 1
        val status = if (link.totalEpisodes != null && next >= link.totalEpisodes) {
            TrackerStatus.COMPLETED
        } else TrackerStatus.WATCHING
        val ok = runCatching {
            tracker(link.id).updateProgress(creds, link.remoteId, next, link.totalEpisodes, status)
        }.getOrDefault(false)
        // Que le service ait répondu ou non, l'épisode est coché localement : la
        // progression repartira du bon endroit au prochain épisode.
        val updated = all.toMutableMap()
        updated[mediaId] = link.copy(progress = next, lastEpisodeId = episodeId)
        writeLinks(updated)
        _status.value = if (ok) {
            "Épisode ${link.progress + 1} marqué vu sur ${link.id.label}"
        } else {
            "Épisode marqué localement (${link.id.label} injoignable, il sera synchronisé plus tard)"
        }
        return ok
    }

    /** Fixe une progression précise (bouton « Marquer vu » de la fiche). */
    suspend fun setProgress(mediaId: String, progress: Int, status: TrackerStatus? = null): Boolean {
        val link = readLinks()[mediaId] ?: return false
        val creds = credentials(link.id)
        if (!creds.usable(link.id)) return false
        val total = link.totalEpisodes
        val st = status ?: if (total != null && progress >= total) TrackerStatus.COMPLETED else TrackerStatus.WATCHING
        val ok = runCatching {
            tracker(link.id).updateProgress(creds, link.remoteId, progress, total, st)
        }.getOrDefault(false)
        val all = readLinks().toMutableMap()
        all[mediaId] = link.copy(progress = progress)
        writeLinks(all)
        _status.value = if (ok) "Progression mise à jour sur ${link.id.label} ($progress épisodes)"
        else "${link.id.label} injoignable — progression gardée localement."
        return ok
    }

    /** Statut courant d'une fiche (d'après l'avancement enregistré). */
    fun statusOf(mediaId: String): TrackerStatus {
        val link = linkFor(mediaId) ?: return TrackerStatus.PLANNING
        val total = link.totalEpisodes
        return when {
            total != null && link.progress >= total -> TrackerStatus.COMPLETED
            link.progress > 0 -> TrackerStatus.WATCHING
            else -> TrackerStatus.PLANNING
        }
    }

    // ---------------------------------------------------------------- métadonnées TMDB

    /**
     * §bandes-annonces (conversation 11) : complète affiche/bannière/bande-annonce
     * d'une fiche quand l'extension ne les fournit pas. Renvoie null sans clé
     * TMDB — aucune requête n'est alors tentée.
     */
    suspend fun tmdbVisuals(title: String, year: Int? = null): TmdbTracker.Visuals? {
        if (!prefs.enrichWithTmdb.value) return null
        val creds = credentials(TrackerId.TMDB)
        if (!creds.hasKey) return null
        return runCatching { tmdb.visualsFor(creds, title, year) }.getOrNull()
    }

    // ---------------------------------------------------------------- stockage local

    private fun readAccounts(): Map<TrackerId, TrackerCredentials> = runCatching {
        val root = JSONObject(prefs.trackerAccounts.value)
        TrackerId.entries.mapNotNull { id ->
            root.optJSONObject(id.name)?.let { o -> id to o.toCredentials() }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeAccounts(map: Map<TrackerId, TrackerCredentials>) {
        val root = JSONObject()
        map.forEach { (id, creds) -> root.put(id.name, creds.toJson()) }
        prefs.setTrackerAccounts(root.toString())
    }

    private fun readLinks(): Map<String, TrackerLink> = runCatching {
        val root = JSONObject(prefs.trackerLinks.value)
        root.keys().asSequence().mapNotNull { mediaId ->
            root.optJSONObject(mediaId)?.let { o ->
                val id = TrackerId.entries.firstOrNull { it.name == o.optString("id") }
                    ?: return@mapNotNull null
                mediaId to TrackerLink(
                    id = id,
                    remoteId = o.optString("remoteId"),
                    title = o.optString("title"),
                    totalEpisodes = o.optInt("total").takeIf { it > 0 },
                    progress = o.optInt("progress"),
                    lastEpisodeId = o.optString("lastEpisodeId"),
                )
            }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun writeLinks(map: Map<String, TrackerLink>) {
        val root = JSONObject()
        map.forEach { (mediaId, link) ->
            root.put(
                mediaId,
                JSONObject()
                    .put("id", link.id.name)
                    .put("remoteId", link.remoteId)
                    .put("title", link.title)
                    .put("total", link.totalEpisodes ?: 0)
                    .put("progress", link.progress)
                    .put("lastEpisodeId", link.lastEpisodeId),
            )
        }
        prefs.setTrackerLinks(root.toString())
    }

    private fun TrackerCredentials.toJson() = JSONObject()
        .put("token", token)
        .put("apiKey", apiKey)
        .put("clientId", clientId)
        .put("clientSecret", clientSecret)
        .put("refreshToken", refreshToken)
        .put("codeVerifier", codeVerifier)
        .put("userName", userName)
        .put("connectedAt", connectedAt)

    private fun JSONObject.toCredentials() = TrackerCredentials(
        token = optString("token"),
        apiKey = optString("apiKey"),
        clientId = optString("clientId"),
        clientSecret = optString("clientSecret"),
        refreshToken = optString("refreshToken"),
        codeVerifier = optString("codeVerifier"),
        userName = optString("userName"),
        connectedAt = optLong("connectedAt"),
    )
}
