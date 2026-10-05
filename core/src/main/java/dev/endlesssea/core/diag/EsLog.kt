package dev.endlesssea.core.diag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal centralisé des erreurs de l'application (spec §7 « ErrorManager »).
 *
 * - anneau mémoire (300 entrées) : ne grossit jamais indéfiniment ;
 * - **rien de sensible** n'est admis ici : appelants, PAS de mots de passe,
 *   tokens, cookies, clés API, contenus de pages ;
 * - lectures thread-safe depuis n'importe quel thread.
 */
object EsLog {

    data class Entry(
        val timestamp: Long,
        val category: String,      // Extension / WebView / Network / DNS / Download / Player / Update / Image / Metadata / Storage
        val component: String,     // ex. « HlsEngine », « CaptchaActivity »…
        val message: String,       // court, lisible
        val details: String = "",
        val recoverable: Boolean = true,
    ) {
        fun format(): String {
            val ts = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE).format(Date(timestamp))
            return "[$ts] [$category/$component] $message" +
                if (details.isBlank()) "" else "\n    $details"
        }
    }

    private const val MAX_ENTRIES = 300
    private val lock = Any()
    private val entries = ArrayDeque<Entry>(MAX_ENTRIES)

    /** Enregistre une erreur (catégories de la spec §7). */
    fun e(category: String, component: String, message: String, details: String = "", recoverable: Boolean = true) {
        if (message.isBlank()) return
        synchronized(lock) {
            entries.removeFirstOrNullIfFull()
            entries.addLast(Entry(System.currentTimeMillis(), category, component, message.take(200), details.take(2000), recoverable))
        }
    }

    /** Instantané (le plus récent en premier). */
    fun entries(): List<Entry> = synchronized(lock) { entries.toList().asReversed() }

    fun distinctCategories(): Set<String> = synchronized(lock) { entries.map { it.category }.toSet() }

    fun clear() = synchronized(lock) { entries.clear() }

    /** Export texte complet (copier/partager). */
    fun export(): String = buildString {
        appendLine("EndlessSea — journal d'erreurs (${entries.size} entrées)")
        appendLine("Exporté le ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())}")
        appendLine()
        entries().forEach { appendLine(it.format()) }
    }

    private fun <T> ArrayDeque<T>.removeFirstOrNullIfFull() {
        if (size >= MAX_ENTRIES) removeFirstOrNull()
    }
}
