package dev.endlesssea.app.ui.explore

import java.text.Normalizer
import java.util.Locale

/** Rubriques éditoriales facultatives déclarées par une extension. */
internal enum class ExploreSpecialKind {
    PROGRAMS,
    NEWS,
}

/**
 * Classe les catégories annoncées par les extensions sans inventer de contenu :
 * seules les rubriques dont la clé ou le titre évoque explicitement un agenda,
 * un planning ou des actualités sont agrégées dans ces sections.
 */
internal fun exploreSpecialKind(categoryKey: String, title: String): ExploreSpecialKind? {
    val searchable = normalizeCategoryText("$categoryKey $title")
    return when {
        NEWS_TERMS.any { term -> searchable.contains(term) } -> ExploreSpecialKind.NEWS
        PROGRAM_TERMS.any { term -> searchable.contains(term) } -> ExploreSpecialKind.PROGRAMS
        else -> null
    }
}

private fun normalizeCategoryText(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private val NEWS_TERMS = listOf(
    "actualite", "actus", "news", "article", "journal", "blog",
)

private val PROGRAM_TERMS = listOf(
    "programme", "programmes", "planning", "schedule", "calendrier",
    "sortie", "sorties", "a venir", "upcoming", "simulcast", "airing",
    "broadcast", "diffusion", "emission",
)
