package dev.endlesssea.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Préférences UI/téléchargement/lecteur (SharedPreferences léger, aucun DataStore requis).
 * Chaque écriture met à jour un StateFlow → Compose se recompose instantanément.
 */
@Singleton
class AppPrefs @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        /**
         * Variantes « tout teinté » pour le verre (§24) : couleurs PROFONDES et
         * désaturées — un verre tout bleu/vert/rouge DOIT rester lisible.
         * (Versus ACCENTS qui sont des pastels de bordure.)
         */
        val GLASS_VARIANTS = linkedMapOf(
            "ocean"     to 0xFF0E5D7EL,
            "emeraude"  to 0xFF0F6B4AL,
            "rubis"     to 0xFF8C2B3EL,
            "amethyste" to 0xFF5B3E8CL,
            "ambre"     to 0xFF9C5B12L,
            "ardoise"   to 0xFF3C4B5CL,
        )
        val GLASS_VARIANT_LABELS = mapOf(
            "ocean" to "Océan", "emeraude" to "Émeraude", "rubis" to "Rubis",
            "amethyste" to "Améthyste", "ambre" to "Ambre", "ardoise" to "Ardoise",
        )

        // Thème : 0 = système, 1 = clair, 2 = sombre, 3 = AMOLED (noir pur)
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_AMOLED = 3

        // Toutes les destinations possibles de la barre
        val ALL_TAB_ROUTES = listOf("home", "explore", "search", "library", "downloads")
        val DEFAULT_TABS = setOf("home", "explore", "search", "library", "downloads")

        // Palettes d'accent (primary) proposées dans les réglages
        val ACCENTS = linkedMapOf(
            "lavande" to 0xFFB9C1FF,
            "cyan" to 0xFF7AD8FF,
            "menthe" to 0xFF8CD6C3,
            "corail" to 0xFFFFB4A2,
            "ambre" to 0xFFFFD08A,
            "rose" to 0xFFFFB1C5,
        )
        const val DEFAULT_ACCENT = "lavande"
    }

    private val p = context.getSharedPreferences("endless_sea_prefs", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- thème
    // AMOLED par défaut (esthétique cible) ; l'utilisateur peut revenir à Système.
    private val _themeMode = MutableStateFlow(p.getInt("theme_mode", THEME_AMOLED))
    val themeMode: StateFlow<Int> = _themeMode

    fun setThemeMode(mode: Int) {
        p.edit().putInt("theme_mode", mode).apply(); _themeMode.value = mode
    }

    // --------------------------------------------------------- barre flottante
    private val _barTabs = MutableStateFlow(
        p.getStringSet("bar_tabs", DEFAULT_TABS)?.toSet() ?: DEFAULT_TABS,
    )
    val barTabs: StateFlow<Set<String>> = _barTabs
    fun setBarTab(route: String, enabled: Boolean) {
        val next = (_barTabs.value + if (enabled) setOf(route) else emptySet()) -
            if (enabled) emptySet() else setOf(route)
        // jamais moins d'un onglet
        val safe = if (next.isEmpty()) setOf("home") else next
        p.edit().putStringSet("bar_tabs", safe).apply(); _barTabs.value = safe
    }

    // ---------------------------------------------------------------- accent
    private val _accent = MutableStateFlow(p.getString("accent", DEFAULT_ACCENT) ?: DEFAULT_ACCENT)
    val accent: StateFlow<String> = _accent
    fun setAccent(name: String) {
        val safe = if (ACCENTS.containsKey(name)) name else DEFAULT_ACCENT
        p.edit().putString("accent", safe).apply(); _accent.value = safe
    }

    // Ordre des onglets dans la barre flottante (CSV de routes)
    private val _tabOrder = MutableStateFlow(
        (p.getString("tab_order", null)?.split(",")?.filter { it in ALL_TAB_ROUTES }
            ?.takeIf { it.isNotEmpty() } ?: ALL_TAB_ROUTES),
    )
    val tabOrder: StateFlow<List<String>> = _tabOrder
    fun moveTab(route: String, delta: Int) {
        val cur = _tabOrder.value.toMutableList()
        val i = cur.indexOf(route); val j = i + delta
        if (i < 0 || j !in cur.indices) return
        cur.add(j, cur.removeAt(i))
        p.edit().putString("tab_order", cur.joinToString(",")).apply(); _tabOrder.value = cur
    }

    // Marge horizontale de la barre flottante (dp)
    private val _barMargin = MutableStateFlow(p.getInt("bar_margin", 16))
    val barMargin: StateFlow<Int> = _barMargin
    fun setBarMargin(dp: Int) {
        val c = dp.coerceIn(0, 64); p.edit().putInt("bar_margin", c).apply(); _barMargin.value = c
    }

    // ---------------------------------------------------------------- stockage
    private val _storageUri = MutableStateFlow(p.getString("storage_uri", null))
    val storageUri: StateFlow<String?> = _storageUri
    fun setStorageUri(uri: String?) {
        p.edit().apply { if (uri == null) remove("storage_uri") else putString("storage_uri", uri) }.apply()
        _storageUri.value = uri
    }

    // ---------------------------------------------------------------- téléchargement
    private val _wifiOnly = MutableStateFlow(p.getBoolean("wifi_only", true))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly
    fun setWifiOnly(v: Boolean) { p.edit().putBoolean("wifi_only", v).apply(); _wifiOnly.value = v }

    private val _partsPerTask = MutableStateFlow(p.getInt("parts", 4))
    val partsPerTask: StateFlow<Int> = _partsPerTask
    fun setPartsPerTask(v: Int) { val c = v.coerceIn(1, 16); p.edit().putInt("parts", c).apply(); _partsPerTask.value = c }

    private val _parallelTasks = MutableStateFlow(p.getInt("tasks", 2))
    val parallelTasks: StateFlow<Int> = _parallelTasks
    fun setParallelTasks(v: Int) { val c = v.coerceIn(1, 4); p.edit().putInt("tasks", c).apply(); _parallelTasks.value = c }

    // ---------------------------------------------------------------- mises à jour
    private val _updateAutoCheck = MutableStateFlow(p.getBoolean("update_auto_check", true))
    val updateAutoCheck: StateFlow<Boolean> = _updateAutoCheck
    fun setUpdateAutoCheck(v: Boolean) { p.edit().putBoolean("update_auto_check", v).apply(); _updateAutoCheck.value = v }

    // ---------------------------------------------------------------- lecteur
    private val _defaultSpeed = MutableStateFlow(p.getFloat("speed", 1f))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed
    fun setDefaultSpeed(v: Float) { val c = v.coerceIn(0.25f, 3f); p.edit().putFloat("speed", c).apply(); _defaultSpeed.value = c }

    private val _autoResume = MutableStateFlow(p.getBoolean("auto_resume", true))
    val autoResume: StateFlow<Boolean> = _autoResume
    fun setAutoResume(v: Boolean) { p.edit().putBoolean("auto_resume", v).apply(); _autoResume.value = v }

    // ---------------------------------------------------------------- liquid mode
    private val _glassOverlay = MutableStateFlow(p.getInt("glass_overlay", 12))
    val glassOverlay: StateFlow<Int> = _glassOverlay
    fun setGlassOverlay(v: Int) { val c = v.coerceIn(0, 30); p.edit().putInt("glass_overlay", c).apply(); _glassOverlay.value = c }

    private val _glassScrim = MutableStateFlow(p.getInt("glass_scrim", 25))
    val glassScrim: StateFlow<Int> = _glassScrim
    fun setGlassScrim(v: Int) { val c = v.coerceIn(0, 100); p.edit().putInt("glass_scrim", c).apply(); _glassScrim.value = c }

    // ---------------------------------------------------------------- cartes
    private val _cardStyle = MutableStateFlow(p.getString("card_style", "detail") ?: "detail")
    val cardStyle: StateFlow<String> = _cardStyle
    fun setCardStyle(v: String) { val c = if (v in setOf("detail", "poster", "minimal")) v else "detail"; p.edit().putString("card_style", c).apply(); _cardStyle.value = c }

    // ---------------------------------------------------------------- lecteur avancé
    private val _skipSeconds = MutableStateFlow(p.getInt("skip_sec", 10))
    val skipSeconds: StateFlow<Int> = _skipSeconds
    fun setSkipSeconds(v: Int) { val c = if (v in setOf(5, 10, 15, 30, 85)) v else 10; p.edit().putInt("skip_sec", c).apply(); _skipSeconds.value = c }

    // Filtre vidéo appliqué à la lecture (teinte / saturation / luminosité)
    private val _videoBrightness = MutableStateFlow(p.getFloat("vf_bright", 0f))
    val videoBrightness: StateFlow<Float> = _videoBrightness
    private val _videoSaturation = MutableStateFlow(p.getFloat("vf_sat", 0f))
    val videoSaturation: StateFlow<Float> = _videoSaturation
    private val _videoHue = MutableStateFlow(p.getFloat("vf_hue", 0f))
    val videoHue: StateFlow<Float> = _videoHue
    private val _videoPreset = MutableStateFlow(p.getString("vf_preset", "none") ?: "none")
    val videoPreset: StateFlow<String> = _videoPreset

    /** Applique & persiste un triplet de filtre (valeurs -100..100). [preset] = nom affiché. */
    fun setVideoFilter(brightness: Float, saturation: Float, hue: Float, preset: String) {
        val b = brightness.coerceIn(-100f, 100f); val s = saturation.coerceIn(-100f, 100f); val h = hue.coerceIn(-180f, 180f)
        p.edit().putFloat("vf_bright", b).putFloat("vf_sat", s).putFloat("vf_hue", h).putString("vf_preset", preset).apply()
        _videoBrightness.value = b; _videoSaturation.value = s; _videoHue.value = h; _videoPreset.value = preset
    }

    // Préréglages personnalisés sauvegardés : JSON [{name,b,s,h}]
    private val _videoPresetsJson = MutableStateFlow(p.getString("vf_presets", "[]") ?: "[]")
    val videoPresetsJson: StateFlow<String> = _videoPresetsJson
    fun setVideoPresetsJson(json: String) { p.edit().putString("vf_presets", json).apply(); _videoPresetsJson.value = json }

    // ---------------------------------------------------------------- sauvegarde
    private val _autoBackup = MutableStateFlow(p.getBoolean("auto_backup", true))
    val autoBackup: StateFlow<Boolean> = _autoBackup
    fun setAutoBackup(v: Boolean) { p.edit().putBoolean("auto_backup", v).apply(); _autoBackup.value = v }

    // ---------------------------------------------------------------- fond d'écran
    /** Image de fond personnalisée (URI de contenu), dessinée derrière toute l'app. */
    private val _bgImageUri = MutableStateFlow(p.getString("bg_image_uri", null))
    val bgImageUri: StateFlow<String?> = _bgImageUri
    fun setBgImage(uri: String?) {
        p.edit().apply { if (uri == null) remove("bg_image_uri") else putString("bg_image_uri", uri) }.apply()
        _bgImageUri.value = uri
    }

    /** Assombrissement de l'image de fond (%) pour garder le texte lisible. */
    private val _bgDim = MutableStateFlow(p.getInt("bg_dim", 35))
    val bgDim: StateFlow<Int> = _bgDim
    fun setBgDim(v: Int) { val c = v.coerceIn(0, 90); p.edit().putInt("bg_dim", c).apply(); _bgDim.value = c }

    var lastAutoBackupAt: Long
        get() = p.getLong("auto_backup_at", 0L)
        set(v) = p.edit().putLong("auto_backup_at", v).apply()

    // ---------------------------------------------------------------- langue audio préférée
    /** Préférence globale VF / VOSTFR appliquée au tri des liens et transmise aux sources. */
    private val _preferredAudioLang = MutableStateFlow(p.getString("pref_audio_lang", "auto") ?: "auto")
    val preferredAudioLang: StateFlow<String> = _preferredAudioLang
    fun setPreferredAudioLang(v: String) {
        val s = if (v in listOf("auto", "vf", "vostfr", "vo")) v else "auto"
        p.edit().putString("pref_audio_lang", s).apply()
        _preferredAudioLang.value = s
    }

    // ---------------------------------------------------------------- apparence avancée
    /** Teinte appliquée sur le logo de démarrage ("original" ou un nom de ACCENTS). */
    private val _logoTint = MutableStateFlow(p.getString("logo_tint", "original") ?: "original")
    val logoTint: StateFlow<String> = _logoTint
    fun setLogoTint(v: String) { p.edit().putString("logo_tint", v).apply(); _logoTint.value = v }

    /** §bibliothèque-locale : liste des arborescences vidéo SAF (JSON list, persistée). */
    private val _localVideoDirs = MutableStateFlow<List<String>>(loadJsonStringList("local_video_dirs"))
    val localVideoDirs: StateFlow<List<String>> = _localVideoDirs
    fun setLocalVideoDirs(dirs: List<String>) {
        val json = "[\"" + dirs.joinToString("\",\"") { it.replace("\"", " ") } + "\"]"
        p.edit().putString("local_video_dirs", json).apply()
        _localVideoDirs.value = dirs
    }

    /** §bibliothèque-locale : métadonnées éditées par fileUri (JSON objet). */
    fun localFileMeta(uri: String): Pair<String?, String?> {
        val json = p.getString("local_file_meta", "{}") ?: "{}"
        val m = Regex(java.util.regex.Pattern.quote(""" + uri + """) +
            "\s*:\s*\[\s*"([^"]*)"\s*,\s*"([^"]*)"\s*\]").find(json)
        return if (m != null) {
            val t = m.groupValues[1].takeIf { it.isNotBlank() }
            val c = m.groupValues[2].takeIf { it.isNotBlank() }
            t to c
        } else null to null
    }
    fun setLocalFileMeta(uri: String, title: String?, coverUri: String?) {
        val json = p.getString("local_file_meta", "{}") ?: "{}"
        val key = """ + uri.replace(""", " ") + """
        val entry = "$key:["${title.orEmpty().replace(""", "'")}","${coverUri.orEmpty().replace(""", "'")}"]"
        val base = json.trim().removePrefix("{").removeSuffix("}").trim()
        val parts = base.split(Regex(",\s*(?=")")).filterNot { it.startsWith(key + ":") }.filter { it.isNotBlank() }
        val newBody = (parts + entry).joinToString(",")
        p.edit().putString("local_file_meta", "{" + newBody + "}").apply()
        _visibleLocalMetaTick.value++
    }
    private val _visibleLocalMetaTick = MutableStateFlow(0)
    val localMetaTick: StateFlow<Int> = _visibleLocalMetaTick

        /** §glisser-serveurs : ordre de priorité des serveurs, persistant (JSON liste). */
    private val _serverOrder = MutableStateFlow<List<String>>(loadJsonStringList("server_order"))
    val serverOrder: StateFlow<List<String>> = _serverOrder
    fun setServerOrder(order: List<String>) {
        val safe = order.map { it.replace("\\", " ").replace("\"", "'") }
        val json = "[\"" + safe.joinToString("\",\"") + "\"]"
        p.edit().putString("server_order", json).apply()
        _serverOrder.value = safe
    }

    private fun loadJsonStringList(key: String): List<String> =
        p.getString(key, null)?.let { json ->
            Regex("\"([^\"]*)\"").findAll(json).map { it.groupValues[1] }.toList()
        } ?: emptyList()

    /** Variante Glass §24 : teinte dominante du verre ("auto" ou nom de ACCENTS). */
    private val _glassVariant = MutableStateFlow(p.getString("glass_variant", "auto") ?: "auto")
    val glassVariant: StateFlow<String> = _glassVariant
    fun setGlassVariant(v: String) { p.edit().putString("glass_variant", v).apply(); _glassVariant.value = v }

    /** Police de l'app : "system" (défaut) | "outfit" | "rubik" | "lora". */
    private val _fontId = MutableStateFlow(p.getString("font_id", "system") ?: "system")
    val fontId: StateFlow<String> = _fontId
    fun setFontId(v: String) { p.edit().putString("font_id", v).apply(); _fontId.value = v }

    // ---------------------------------------------------------------- réseau
    /** Mode DNS §9 : "system" (défaut) | "cloudflare" | "google". Appliqué au prochain démarrage. */
    private val _dnsMode = MutableStateFlow(p.getString("dns_mode", "system") ?: "system")
    val dnsMode: StateFlow<String> = _dnsMode
    fun setDnsMode(v: String) {
        val m = if (v in listOf("system", "cloudflare", "google")) v else "system"
        p.edit().putString("dns_mode", m).apply()
        _dnsMode.value = m
        dev.endlesssea.core.net.EsNet.dnsMode = m
    }

    init {
        // Synchronise le résolveur réseau global dès la création des préférences.
        dev.endlesssea.core.net.EsNet.dnsMode = _dnsMode.value
    }
}
