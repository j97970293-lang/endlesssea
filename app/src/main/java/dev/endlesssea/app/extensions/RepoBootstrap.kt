package dev.endlesssea.app.extensions

import dev.endlesssea.data.db.RepoDao
import dev.endlesssea.data.db.RepoEntity
import dev.endlesssea.app.di.AppPrefs

/**
 * The French extension index the project already publishes.
 * It is remembered outside Room so a database rebuild does not ask for the URL again.
 */
object RepoBootstrap {
    const val PLUGINS_FR =
        "https://github.com/j97970293-lang/endlesssea-plugins-fr/releases/latest/download/index.json"

    fun plan(saved: List<String>, inDatabase: List<String>, seeded: Boolean): List<String> {
        val known = (saved + inDatabase).map { it.trim() }.filter { it.startsWith("http") }.distinct()
        if (known.isEmpty() && !seeded) return listOf(PLUGINS_FR)
        return known
    }

    suspend fun ensure(dao: RepoDao, prefs: AppPrefs) {
        val existing = dao.all()
        val urls = plan(prefs.extensionRepos.value, existing.map { it.url }, prefs.extensionRepoSeeded.value)
        urls.filter { url -> existing.none { it.url == url } }.forEach { url ->
            dao.upsert(
                RepoEntity(
                    url = url,
                    name = if (url == PLUGINS_FR) "Endless Sea · Extensions FR" else url.substringAfterLast('/'),
                ),
            )
        }
        prefs.setExtensionRepos(urls)
        prefs.setExtensionRepoSeeded(true)
    }
}
