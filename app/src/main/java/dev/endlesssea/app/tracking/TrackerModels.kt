package dev.endlesssea.app.tracking

/**
 * §suivi (conversation 11) — modèles communs aux services de suivi gratuits.
 *
 * Le principe : aucune donnée n'est envoyée si l'utilisateur n'a pas connecté
 * de compte. Chaque service reste facultatif et indépendant des autres.
 */
enum class TrackerId(val label: String) {
    ANILIST("AniList"),
    MAL("MyAnimeList"),
    SHIKIMORI("Shikimori"),
    TMDB("TMDB (métadonnées)"),
    ;

    /** TMDB ne suit pas la progression : il fournit affiches et bandes-annonces. */
    val tracksProgress: Boolean get() = this != TMDB

    val isAnimeService: Boolean get() = this != TMDB
}

/**
 * Identifiants d'un service. Selon le service on utilise :
 *  · [token]      — jeton d'accès (AniList, MAL, Shikimori une fois échangé) ;
 *  · [apiKey]     — clé d'API (TMDB v3) ;
 *  · [clientId] / [clientSecret] — application OAuth déclarée par l'utilisateur ;
 *  · [refreshToken] — renouvellement MAL.
 */
data class TrackerCredentials(
    val token: String = "",
    val apiKey: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val refreshToken: String = "",
    /** Code_verifier PKCE gardé le temps de l'échange (MAL). */
    val codeVerifier: String = "",
    /** Pseudo affiché une fois le compte vérifié. */
    val userName: String = "",
    val connectedAt: Long = 0L,
) {
    val hasToken: Boolean get() = token.isNotBlank()
    val hasKey: Boolean get() = apiKey.isNotBlank()

    /** Vrai si assez d'informations pour tenter une requête. */
    fun usable(id: TrackerId): Boolean = when (id) {
        TrackerId.TMDB -> hasKey
        else -> hasToken
    }
}

/** Résultat d'une recherche de titre sur un service de suivi. */
data class TrackerMediaHit(
    val remoteId: String,
    val title: String,
    val episodes: Int? = null,
    val posterUrl: String? = null,
    /** Année de diffusion : évite de rattacher une saison à la mauvaise fiche. */
    val year: Int? = null,
)

/**
 * Rattachement local d'une fiche de l'application à une entrée du service.
 * Conservé en base locale : le suivi fonctionne même hors-ligne (les appels
 * réseau sont simplement rejoués plus tard).
 */
data class TrackerLink(
    val id: TrackerId,
    val remoteId: String,
    val title: String = "",
    val totalEpisodes: Int? = null,
    /** Nombre d'épisodes vus d'après le service (ou d'après l'application). */
    val progress: Int = 0,
    /** Dernier épisode local déjà compté — évite de compter deux fois. */
    val lastEpisodeId: String = "",
)

/** Statuts communs (traduits dans le vocabulaire propre à chaque service). */
enum class TrackerStatus(val label: String) {
    WATCHING("En cours"),
    COMPLETED("Terminé"),
    PLANNING("À voir"),
    DROPPED("Abandonné"),
    ;

    companion object {
        fun fromLabel(label: String): TrackerStatus =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: WATCHING
    }
}

/** Ce qu'un service renvoie après vérification d'un compte. */
data class TrackerAccount(
    val id: TrackerId,
    val userName: String,
    val avatarUrl: String? = null,
)
