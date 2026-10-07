package dev.endlesssea.app.skip

/**
 * §megaskip — modèles du système de saut intelligent (segments communautaires
 * + boutons personnalisés), partagés par le lecteur et les Réglages.
 */

/** Types de segments sautables (mêmes libellés que les bases communautaires). */
enum class SkipType(val key: String, val label: String) {
    INTRO("op", "Intro"),
    RECAP("recap", "Récap"),
    CREDITS("ed", "Générique"),
    PREVIEW("preview", "Aperçu"),
    CUSTOM("custom", "Personnalisé");

    /** Libellé du bouton affiché dans le lecteur (« Passer l'intro »). */
    val actionLabel: String
        get() = when (this) {
            INTRO -> "Passer l'intro"
            RECAP -> "Passer le récap"
            CREDITS -> "Passer le générique"
            PREVIEW -> "Passer l'aperçu"
            CUSTOM -> "Passer"
        }
}

/** Un segment sautable, bornes en millisecondes. */
data class SkipSegment(
    val type: SkipType,
    val startMs: Long,
    val endMs: Long,
    /** Fournisseur d'origine : theintrodb / introdb / aniskip / local. */
    val provider: String = "local",
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    val humanDuration: String
        get() {
            val s = durationMs / 1000
            return if (s >= 60) "${s / 60} min ${"%02d".format(s % 60)}" else "$s s"
        }

    fun contains(positionMs: Long): Boolean = positionMs >= startMs && positionMs < endMs
}

/**
 * §megaskip — identité d'un média vue par les fournisseurs de segments.
 * Tous les identifiants sont optionnels : le résolveur complète ce qu'il peut
 * (titre → MAL via Jikan, IMDb → MAL via AniZip) puis interroge les bases.
 */
data class SkipTarget(
    val title: String? = null,
    val mediaId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val tmdbId: String? = null,
    val imdbId: String? = null,
    val tvdbId: String? = null,
    val malId: String? = null,
    val anilistId: String? = null,
    val durationMs: Long = 0L,
) {
    /** Clé stable du média (cache hors-ligne). */
    val mediaKey: String
        get() = mediaId?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }?.let { "title:" + normalize(it) }
            ?: "unknown"

    fun cacheKey(provider: String): String = "$provider|$mediaKey|S$season" + "E$episode"

    /** Vrai si aucun identifiant exploitable : inutile d'interroger le réseau. */
    val isAnonymous: Boolean
        get() = tmdbId.isNullOrBlank() && tvdbId.isNullOrBlank() &&
            imdbId.isNullOrBlank() && malId.isNullOrBlank() && title.isNullOrBlank()

    companion object {
        /** Normalisation de titre commune (recherche + clé de cache). */
        fun normalize(raw: String): String = raw.lowercase()
            .replace(Regex("[^a-z0-9àâäéèêëîïôöùûüç ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        /** Extrait saison / épisode d'un nom de fichier ou d'un id (« S01E03 », « 1x03 », « - 12 »). */
        fun parseSeasonEpisode(raw: String?): Pair<Int, Int> {
            val s = raw ?: return 0 to 0
            Regex("(?i)s(\\d{1,2})[ ._-]*e(\\d{1,3})").find(s)?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
            Regex("(?i)(\\d{1,2})x(\\d{1,3})").find(s)?.let {
                return it.groupValues[1].toInt() to it.groupValues[2].toInt()
            }
            Regex("(?i)[Ee][Pp]?(\\d{1,3})(?:\\b|$)").find(s)?.let {
                return 1 to it.groupValues[1].toInt()
            }
            return 0 to 0
        }
    }
}

/** §megaskip — bouton de saut personnalisé (persisté en base, disponible hors-ligne). */
data class CustomSkipButton(
    val id: Long = 0L,
    val label: String,
    val seconds: Int,
    val position: Int = 0,
    val enabled: Boolean = true,
) {
    val human: String
        get() = when {
            seconds >= 60 && seconds % 60 == 0 -> "+${seconds / 60} min"
            seconds >= 60 -> "+${seconds / 60}min${"%02d".format(seconds % 60)}"
            else -> "+$seconds s"
        }
}

/** §megaskip — réglages (persistés dans AppPrefs, exposés au lecteur). */
data class SkipSettings(
    val autoIntro: Boolean = true,
    val autoRecap: Boolean = false,
    val autoCredits: Boolean = true,
    val autoPreview: Boolean = false,
    /** Secondes avant le saut automatique (0 = immédiat). */
    val countdownSec: Int = 3,
    val providerTheIntroDb: Boolean = true,
    val providerIntroDb: Boolean = true,
    val providerAniSkip: Boolean = true,
    /** Afficher la pastille « Passer » même quand le saut auto est actif. */
    val showButton: Boolean = true,
) {
    fun autoFor(type: SkipType): Boolean = when (type) {
        SkipType.INTRO -> autoIntro
        SkipType.RECAP -> autoRecap
        SkipType.CREDITS -> autoCredits
        SkipType.PREVIEW -> autoPreview
        SkipType.CUSTOM -> false
    }
}
