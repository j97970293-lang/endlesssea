package dev.endlesssea.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * §megaskip — cache LOCAL des segments (intro / récap / générique / aperçu)
 * récupérés auprès des bases communautaires (TheIntroDB, IntroDB, AniSkip).
 *
 * Le cache est la source de vérité en lecture : un épisode déjà vu une fois
 * fonctionne ensuite **entièrement hors-ligne** (exigence Megaskip C).
 */
@Entity(tableName = "skip_cache", indices = [Index("mediaKey"), Index("fetchedAt")])
data class SkipCacheEntity(
    /** Clé unique du segment mis en cache : `<provider>|<média>|S<season>E<episode>`. */
    @PrimaryKey val cacheKey: String,
    /** Fournisseur d'origine : theintrodb / introdb / aniskip. */
    val provider: String,
    /** Identifiant du média : id de fiche, `local:<titre>` ou `title:<titre normalisé>`. */
    val mediaKey: String,
    val season: Int = 0,
    val episode: Int = 0,
    /** Segments sérialisés : `[{type,startMs,endMs}]`. */
    val json: String,
    val fetchedAt: Long = System.currentTimeMillis(),
)

/**
 * §megaskip — boutons de saut PERSONNALISÉS (CRUD complet, hors-ligne).
 * Exemples : « +85 s », « Opening », « Filler », « Passer le résumé ».
 */
@Entity(tableName = "skip_buttons")
data class SkipButtonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    /** Durée du saut en secondes (1..3600). */
    val seconds: Int,
    val position: Int = 0,
    val enabled: Boolean = true,
)
