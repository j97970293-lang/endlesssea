package dev.endlesssea.app.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Réglages utilisateur des extensions (spec : l'app applique les réglages éditables
 * déclarés via EsExtension.settings()). Stockage SharedPreferences dédié, clés
 * préfixées par l'identifiant d'extension. Exposé au loader via [provider] pour que
 * chaque nouvelle instance reçoive `ExtensionContext.settings`.
 */
@Singleton
class ExtensionSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ext_settings", Context.MODE_PRIVATE)

    val provider: (String) -> Map<String, String> = { pkg -> getAll(pkg) }

    fun getAll(pkg: String): Map<String, String> {
        val prefix = "$pkg\u0000"
        return prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
            .mapValues { it.value?.toString().orEmpty() }
    }

    fun set(pkg: String, key: String, value: String) {
        prefs.edit().putString("$pkg\u0000$key", value).apply()
    }

    fun holderKey(pkg: String, key: String) = "$pkg\u0000$key"
}
