package dev.endlesssea.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §suivi (conversation 11) — comptes des services de suivi gratuits.
 *
 * Les jetons restent en base LOCALE : aucune synchronisation vers un serveur
 * tiers n'est faite par l'application, et chaque service peut être coupé
 * individuellement (la ligne est alors supprimée).
 */
@Entity(tableName = "tracker_accounts")
data class TrackerAccountEntity(
    /** ANILIST · MAL · SHIKIMORI · TMDB */
    @PrimaryKey val service: String,
    val userName: String = "",
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val clientId: String? = null,
    /** Clé d'API (TMDB) — distincte d'un jeton OAuth. */
    val apiKey: String? = null,
    /** Échéance du jeton (MAL : 1 h) ; 0 = jeton sans expiration connue. */
    val expiresAt: Long = 0L,
    /** Désactiver garde le compte mais arrête tout envoi (bouton de l'écran Comptes). */
    val enabled: Boolean = true,
    val connectedAt: Long = System.currentTimeMillis(),
    /** Dernier message (erreur d'authentification, quota…). */
    val lastError: String? = null,
)

/**
 * §suivi — rattachement d'une fiche de l'application à une entrée du service
 * distant (anime/film). La progression est stockée ici puis poussée au service,
 * ce qui permet de marquer un épisode vu **hors ligne**.
 */
@Entity(tableName = "tracker_links", indices = [Index("service")])
data class TrackerLinkEntity(
    /** mediaId local (extension:clé). */
    @PrimaryKey val mediaId: String,
    val service: String,
    val remoteId: String,
    val title: String = "",
    val totalEpisodes: Int = 0,
    val progress: Int = 0,
    /** WATCHING · COMPLETED · PLANNING · DROPPED */
    val status: String = "PLANNING",
    /** Dernier épisode local déjà compté (évite les doublons). */
    val lastEpisodeId: String = "",
    /** Vrai si le service n'a pas pu être mis à jour (à rejouer). */
    val pendingSync: Boolean = false,
    /** Autorisation explicite : progression par numéro, jamais par compteur de lectures. */
    @androidx.room.ColumnInfo(defaultValue = "0")
    val autoMatchEpisodes: Boolean = false,
    /** Saison liée ; null désigne exclusivement les épisodes sans saison renseignée. */
    val autoMatchSeason: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
