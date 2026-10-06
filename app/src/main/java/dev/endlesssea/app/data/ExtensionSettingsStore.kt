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
 *
 * 「Piège corrigé」le séparateur était `\u0000`. Les SharedPreferences sont
 * persistées en **XML**, or NUL est un caractère **illégal en XML 1.0** : la valeur
 * vivait en mémoire pour la session en cours, puis le fichier ne se relisait plus au
 * démarrage suivant (`An invalid XML character (Unicode: 0x0) was found in the value
 * of attribute "name"`). Résultat : l'utilisateur devait resaisir l'adresse de chaque
 * source à chaque lancement. Le séparateur est désormais [SEP], et [migrateLegacyKeys]
 * récupère ce qui aurait survécu en mémoire.
 */
@Singleton
class ExtensionSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("ext_settings", Context.MODE_PRIVATE)

    companion object {
        /** Séparateur id/clé : légal en XML et impossible dans un applicationId. */
        const val SEP = "|"

        /** Ancien séparateur illégal en XML — conservé pour la migration. */
        private const val LEGACY_SEP = "\u0000"
    }

    init {
        migrateLegacyKeys()
    }

    val provider: (String) -> Map<String, String> = { pkg -> getAll(pkg) }

    fun getAll(pkg: String): Map<String, String> {
        val prefix = pkg + SEP
        return prefs.all
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
            .mapValues { it.value?.toString().orEmpty() }
    }

    fun set(pkg: String, key: String, value: String) {
        prefs.edit().putString(pkg + SEP + key, value).apply()
    }

    fun holderKey(pkg: String, key: String) = pkg + SEP + key

    /**
     * Réécrit les clés de l'ancien format (`<id>\u0000<clé>`) avec [SEP].
     * En pratique elles n'ont jamais survécu à un redémarrage, mais la migration
     * rattrape le cas où l'app est mise à jour sans avoir été tuée entre-temps.
     */
    private fun migrateLegacyKeys() {
        val legacy = prefs.all.keys.filter { it.contains(LEGACY_SEP) }
        if (legacy.isEmpty()) return
        val editor = prefs.edit()
        legacy.forEach { old ->
            val value = prefs.all[old]?.toString().orEmpty()
            if (value.isNotEmpty()) editor.putString(old.replace(LEGACY_SEP, SEP), value)
            editor.remove(old)
        }
        editor.apply()
    }
}
