package dev.endlesssea.extensions.loader

/** Environnement global léger injecté par l'app (langue audio préférée). */
object AppEnv {
    /** "auto" | "vf" | "vostfr" | "vo" — visible dans les réglages sources via clé `app.pref_lang`. */
    @JvmField
    @Volatile
    var preferredAudioLang: String = "auto"
}
