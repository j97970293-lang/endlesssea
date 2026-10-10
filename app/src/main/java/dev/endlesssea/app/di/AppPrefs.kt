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
        private val PLAYBACK_QUALITY_OPTIONS = setOf("auto", "2160", "1440", "1080", "720", "480", "360")

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
            // §couleurs : palette étendue (teintes profondes — lisibilité préservée)
            "saphir"    to 0xFF21407BL,
            "lagune"    to 0xFF0F6E78L,
            "fuchsia"   to 0xFF7A2563L,
            "bordeaux"  to 0xFF6B1F31L,
            "foret"     to 0xFF1B4A33L,
            "miel"      to 0xFF7A5318L,
            "prune"     to 0xFF4E376CL,
            "nuit"      to 0xFF25314EL,
            "peche"     to 0xFF8A4A2EL,
            "chartreuse" to 0xFF4E6116L,
            // §couleurs-2 : seconde vague de teintes profondes
            "indigo"    to 0xFF2E3A8C,
            "violet"    to 0xFF5A2E8C,
            "magenta"   to 0xFF7E2370,
            "cerise"    to 0xFF8E2347,
            "brique"    to 0xFF8A3A24,
            "cuivre"    to 0xFF8A5A2B,
            "olive"     to 0xFF4F5A1E,
            "jade"      to 0xFF17604F,
            "turquoise" to 0xFF12616B,
            "ciel"      to 0xFF1C5A8C,
            "acier"     to 0xFF36506B,
            "graphite"  to 0xFF32373D,
            "charbon"   to 0xFF24262B,
            "sable"     to 0xFF6E5A36,
            "cafe"      to 0xFF4A332A,
            "menthe"    to 0xFF1F6B55,
            "lagon"     to 0xFF0E6A86,
            "orchidee"  to 0xFF6A3184,
            "corail"    to 0xFF8C3A3A,
            "safran"    to 0xFF8A6214,
        )
        val GLASS_VARIANT_LABELS = mapOf(
            "ocean" to "Océan", "emeraude" to "Émeraude", "rubis" to "Rubis",
            "amethyste" to "Améthyste", "ambre" to "Ambre", "ardoise" to "Ardoise",
            "saphir" to "Saphir", "lagune" to "Lagune", "fuchsia" to "Fuchsia",
            "bordeaux" to "Bordeaux", "foret" to "Forêt", "miel" to "Miel",
            "prune" to "Prune", "nuit" to "Nuit", "peche" to "Pêche",
            "chartreuse" to "Chartreuse",
            "indigo" to "Indigo", "violet" to "Violet", "magenta" to "Magenta",
            "cerise" to "Cerise", "brique" to "Brique", "cuivre" to "Cuivre",
            "olive" to "Olive", "jade" to "Jade", "turquoise" to "Turquoise",
            "ciel" to "Ciel", "acier" to "Acier", "graphite" to "Graphite",
            "charbon" to "Charbon", "sable" to "Sable", "cafe" to "Café",
            "menthe" to "Menthe", "lagon" to "Lagon", "orchidee" to "Orchidée",
            "corail" to "Corail", "safran" to "Safran",
        )

        // Thème : 0 = système, 1 = clair, 2 = sombre, 3 = AMOLED (noir pur)
        const val THEME_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2
        const val THEME_AMOLED = 3

        // Toutes les destinations possibles de la barre
        // §recherche-dans-explorer : l'onglet « Recherche » a quitté la barre.
        val ALL_TAB_ROUTES = listOf("home", "explore", "library", "downloads", "statistics")
        val DEFAULT_TABS = setOf("home", "explore", "library", "downloads", "statistics")

        // Palettes d'accent (primary) proposées dans les réglages
        val ACCENTS = linkedMapOf(
            // §accent-anymex (conversation 3) : le bleu #4FC3F7 est l'accent
            // par défaut — toute la palette Material 3 en découle.
            "anymex" to 0xFF4FC3F7,
            "lavande" to 0xFFB9C1FF,
            "cyan" to 0xFF7AD8FF,
            "menthe" to 0xFF8CD6C3,
            "corail" to 0xFFFFB4A2,
            "ambre" to 0xFFFFD08A,
            "rose" to 0xFFFFB1C5,
            // §couleurs : demande « beaucoup de couleurs » (contrastes AA vérifiés)
            "violet" to 0xFFBC92FF,
            "indigo" to 0xFF92A6FF,
            "océan" to 0xFF60C8FF,
            "turquoise" to 0xFF5FE3DB,
            "emeraude" to 0xFF74DFA0,
            "citron" to 0xFFDCE67A,
            "orange" to 0xFFFFB36B,
            "framboise" to 0xFFFF8FB1,
            "magenta" to 0xFFFF8BD1,
            "rouge" to 0xFFFF9285,
            "doré" to 0xFFE7C96F,
            "chocolat" to 0xFFDDB08C,
            "neige" to 0xFFD9E1F2,
            // §couleurs-2 : palette étendue (toutes testées sur fond sombre)
            "pervenche" to 0xFFA8B8FF,
            "bleuet" to 0xFF8FB4FF,
            "azur" to 0xFF79CBFF,
            "glacier" to 0xFF9FE4F2,
            "aigue" to 0xFF7FE6D2,
            "jade" to 0xFF86E0B8,
            "sauge" to 0xFFAEDBA6,
            "tilleul" to 0xFFC9E79A,
            "mangue" to 0xFFFFCD7A,
            "abricot" to 0xFFFFC199,
            "terracotta" to 0xFFE8A188,
            "brique" to 0xFFE89B8F,
            "cerise" to 0xFFFF9BA8,
            "orchidee" to 0xFFE2A5FF,
            "lilas" to 0xFFCDB4FF,
            "mauve" to 0xFFD7A8E8,
            "prune" to 0xFFC79BD8,
            "perle" to 0xFFE8E2F2,
            // §couleurs-3 : teintes vives demandées (retour #6)
            "saphir" to 0xFF6FA8FF,
            "cobalt" to 0xFF7E9BFF,
            "electrique" to 0xFF6FD2FF,
            "lagon" to 0xFF5FD6C8,
            "menthol" to 0xFF7CE8B4,
            "pistache" to 0xFFB6E88A,
            "olive" to 0xFFCBD98A,
            "miel" to 0xFFFFD98A,
            "cuivre" to 0xFFF0A878,
            "corail_vif" to 0xFFFF9A8A,
            "fuchsia" to 0xFFFF8ED0,
            "bonbon" to 0xFFFFA6D2,
            "amethyste" to 0xFFC49BFF,
            "nuit_bleue" to 0xFF8FA8E8,
            "argent" to 0xFFD2D8E2,
            "sable" to 0xFFE6D5B8,
            "argent" to 0xFFCFD6DF,
            "etain" to 0xFFB9C2CC,
            "sable" to 0xFFE4D2A8,
            "moutarde" to 0xFFE0C06A,
            "caramel" to 0xFFD9AE7E,
            "cacao" to 0xFFC9A18A,
        )
        const val DEFAULT_ACCENT = "anymex"

        /** Unique habillage actif du lecteur. Les anciens identifiants restent reconnus pour migrer les sauvegardes. */
        val PLAYER_THEMES = linkedMapOf(
            "cinema" to Pair(0xFF73B7FFL, "Essentiel"),
        )
        private val LEGACY_PLAYER_THEME_IDS = setOf(
            "default", "zen", "orbit", "compactbar", "neonframe", "split", "floating",
        )
        fun isKnownPlayerTheme(id: String): Boolean = id == "cinema" || id in LEGACY_PLAYER_THEME_IDS
        @Suppress("UNUSED_PARAMETER")
        fun migratePlayerTheme(old: String?): String = "cinema"

        /** §anymex-ui : styles de carte média (capture « Card Style »). */
        val CARD_STYLES = listOf("saikou", "exotic", "minimal_exotic", "modern")

        /** Anciennes valeurs (0.12-) → style AnyMEX équivalent. */
        fun migrateCardStyle(v: String?): String = when (v) {
            null, "detail" -> "saikou"
            "poster" -> "modern"
            "minimal" -> "minimal_exotic"
            in CARD_STYLES -> v!!
            else -> "saikou"
        }
    }

    private val p = context.getSharedPreferences("endless_sea_prefs", Context.MODE_PRIVATE)

    init {
        // Preserve existing order, append new destinations, and expose Statistics once.
        val savedOrder = p.getString("tab_order", null)?.split(",")
        val edit = p.edit()
        if (savedOrder != null) {
            edit.putString("tab_order", normalizeTabOrder(savedOrder, ALL_TAB_ROUTES).joinToString(","))
        }
        if (!p.getBoolean("statistics_tab_migrated", false)) {
            if (savedOrder == null || "statistics" !in savedOrder) {
                edit.putStringSet("bar_tabs", p.getStringSet("bar_tabs", DEFAULT_TABS).orEmpty() + "statistics")
            }
            edit.putBoolean("statistics_tab_migrated", true)
        }
        edit.apply()
    }

    private val _recordHistory = MutableStateFlow(p.getBoolean("record_history", true))
    val recordHistory: StateFlow<Boolean> = _recordHistory
    fun setRecordHistory(v: Boolean) { p.edit().putBoolean("record_history", v).apply(); _recordHistory.value = v }

    // ---------------------------------------------------------------- thème
    // AMOLED par défaut (esthétique cible) ; l'utilisateur peut revenir à Système.
    private val _themeMode = MutableStateFlow(p.getInt("theme_mode", THEME_AMOLED))
    val themeMode: StateFlow<Int> = _themeMode

    fun setThemeMode(mode: Int) {
        p.edit().putInt("theme_mode", mode).apply(); _themeMode.value = mode
    }

    // --------------------------------------------------------- barre flottante
    private val _barTabs = MutableStateFlow(
        (p.getStringSet("bar_tabs", DEFAULT_TABS)?.toSet() ?: DEFAULT_TABS)
            .filter { it in ALL_TAB_ROUTES }.toSet().ifEmpty { DEFAULT_TABS },
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

    // ---- §theme-extra : teinte de l'affiche sur la fiche + lueur « bloom » + grain + immersif
    private val _usePosterColor = MutableStateFlow(p.getBoolean("use_poster_color", true))
    val usePosterColor: StateFlow<Boolean> = _usePosterColor
    fun setUsePosterColor(v: Boolean) { _usePosterColor.value = v; p.edit().putBoolean("use_poster_color", v).apply() }

    private val _bloom = MutableStateFlow(p.getBoolean("bloom_enabled", true))
    val bloom: StateFlow<Boolean> = _bloom
    fun setBloom(v: Boolean) { _bloom.value = v; p.edit().putBoolean("bloom_enabled", v).apply() }

    private val _grain = MutableStateFlow(p.getBoolean("grain_enabled", false))
    val grain: StateFlow<Boolean> = _grain
    fun setGrain(v: Boolean) { _grain.value = v; p.edit().putBoolean("grain_enabled", v).apply() }

    private val _immersiveMode = MutableStateFlow(p.getBoolean("immersive_mode", false))
    val immersiveMode: StateFlow<Boolean> = _immersiveMode
    fun setImmersiveMode(v: Boolean) { _immersiveMode.value = v; p.edit().putBoolean("immersive_mode", v).apply() }

    // ---- §barre-dynamique : « pill » = onglet sélectionné avec libellé, autres icônes seules
    private val _navBarStyle = MutableStateFlow(p.getString("nav_bar_style", "dynamic") ?: "dynamic")
    val navBarStyle: StateFlow<String> = _navBarStyle
    fun setNavBarStyle(v: String) { _navBarStyle.value = v; p.edit().putString("nav_bar_style", v).apply() }

    fun setBarMargin(dp: Int) {
        val c = dp.coerceIn(0, 64); p.edit().putInt("bar_margin", c).apply(); _barMargin.value = c
    }

    // ---------------------------------------------------------------- stockage
    private val _storageUri = MutableStateFlow(p.getString("storage_uri", null))
    val storageUri: StateFlow<String?> = _storageUri
    fun setStorageUri(uri: String?) {
        p.edit().apply { if (uri == null) remove("storage_uri") else putString("storage_uri", uri) }.apply()
        _storageUri.value = uri
        // §emplacement : on MÉMORISE l'ancien emplacement pour que les
        // téléchargements déjà présents restent trouvables, lisibles et
        // supprimables exactement comme les nouveaux.
        if (uri != null) rememberStorageRoot(uri)
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
    private val _glassOverlay = MutableStateFlow(p.getInt("glass_overlay", 22))
    val glassOverlay: StateFlow<Int> = _glassOverlay
    fun setGlassOverlay(v: Int) { val c = v.coerceIn(0, 30); p.edit().putInt("glass_overlay", c).apply(); _glassOverlay.value = c }

    private val _glassScrim = MutableStateFlow(p.getInt("glass_scrim", 16))
    val glassScrim: StateFlow<Int> = _glassScrim
    fun setGlassScrim(v: Int) { val c = v.coerceIn(0, 100); p.edit().putInt("glass_scrim", c).apply(); _glassScrim.value = c }

    // ---------------------------------------------------------------- cartes
    private val _cardStyle = MutableStateFlow(migrateCardStyle(p.getString("card_style", "saikou")))
    val cardStyle: StateFlow<String> = _cardStyle
    fun setCardStyle(v: String) {
        val c = if (v in CARD_STYLES) v else "saikou"
        p.edit().putString("card_style", c).apply(); _cardStyle.value = c
    }

    // ---------------------------------------------------------------- lecteur avancé
    private val _skipSeconds = MutableStateFlow(p.getInt("skip_sec", 10))
    val skipSeconds: StateFlow<Int> = _skipSeconds
    fun setSkipSeconds(v: Int) { val c = if (v in setOf(5, 10, 15, 30, 85)) v else 10; p.edit().putInt("skip_sec", c).apply(); _skipSeconds.value = c }

    /** §mégaskip : secondes du « grand saut » (+85s façon anime), réglable 30..180. Défaut 85. */
    private val _megaSkipSeconds = MutableStateFlow(p.getInt("mega_skip_sec", 85))
    val megaSkipSeconds: StateFlow<Int> = _megaSkipSeconds
    fun setMegaSkipSeconds(v: Int) {
        val c = v.coerceIn(30, 180)
        p.edit().putInt("mega_skip_sec", c).apply(); _megaSkipSeconds.value = c
    }

    /** §auto-skip : passer automatiquement les plages intro/outro marquées (fichiers locaux). */
    private val _autoSkipMarkers = MutableStateFlow(p.getBoolean("auto_skip_markers", true))
    val autoSkipMarkers: StateFlow<Boolean> = _autoSkipMarkers
    fun setAutoSkipMarkers(v: Boolean) { p.edit().putBoolean("auto_skip_markers", v).apply(); _autoSkipMarkers.value = v }

    /**
     * §rendu-vidéo : surface de rendu du lecteur.
     *  - "texture" (défaut) : TextureView, nécessaire aux filtres vidéo ;
     *  - "surface" : SurfaceView avec effets GPU désactivés ; privilégie le
     *    chemin HDR natif de l'appareil quand source/décodeur/écran le permettent.
     */
    private val _videoRender = MutableStateFlow(p.getString("video_render", "texture") ?: "texture")
    val videoRender: StateFlow<String> = _videoRender
    fun setVideoRender(v: String) {
        val safe = if (v == "surface") "surface" else "texture"
        p.edit().putString("video_render", safe).apply(); _videoRender.value = safe
    }

    /** §orientation-lecteur : "landscape" (défaut, lecture à l'horizontale) ou "portrait". */
    private val _playerOrientation = MutableStateFlow(p.getString("player_orientation", "landscape") ?: "landscape")
    val playerOrientation: StateFlow<String> = _playerOrientation
    fun setPlayerOrientation(v: String) {
        val safe = if (v == "portrait") "portrait" else "landscape"
        p.edit().putString("player_orientation", safe).apply(); _playerOrientation.value = safe
    }

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

    /** Qualité de lecture visée : « auto » ou une résolution en pixels. */
    private val _preferredPlaybackQuality = MutableStateFlow(
        p.getString("playback_quality", "auto")?.takeIf { it in PLAYBACK_QUALITY_OPTIONS } ?: "auto",
    )
    val preferredPlaybackQuality: StateFlow<String> = _preferredPlaybackQuality
    fun setPreferredPlaybackQuality(v: String) {
        val safe = v.takeIf { it in PLAYBACK_QUALITY_OPTIONS } ?: "auto"
        p.edit().putString("playback_quality", safe).apply()
        _preferredPlaybackQuality.value = safe
    }

    // ---------------------------------------------------------------- apparence avancée
    /** Teinte appliquée sur le logo de démarrage ("original" ou un nom de ACCENTS). */
    private val _appLogo = MutableStateFlow(dev.endlesssea.app.branding.AppLogos.resolve(p.getString("app_logo", null)).id)
    val appLogo: StateFlow<String> = _appLogo
    fun setAppLogo(id: String) {
        val safe = dev.endlesssea.app.branding.AppLogos.resolve(id).id
        p.edit().putString("app_logo", safe).apply()
        _appLogo.value = safe
    }

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

    /**
     * §bibliothèque-locale : métadonnées éditées par fileUri (JSON objet).
     * Tableau de 6 champs : [titre, affiche, débutIntro(s), finIntro(s), débutOutro(s), type].
     * Les anciens objets à 2 ou 5 champs restent lisibles (champs manquants = null).
     */
    data class LocalFileMeta(
        val title: String? = null,
        val coverUri: String? = null,
        val introStartSec: Int? = null,
        val introEndSec: Int? = null,
        val outroStartSec: Int? = null,
        val mediaType: String? = null,
    )

    fun localFileMetadataSnapshot(): Map<String, LocalFileMeta> =
        dev.endlesssea.app.local.LocalMetadataCodec.decodeAll(p.getString("local_file_meta", "{}") ?: "{}")

    fun localFileMeta(uri: String): LocalFileMeta = localFileMetadataSnapshot()[uri] ?: LocalFileMeta()

    fun setLocalFileMeta(uri: String, meta: LocalFileMeta) {
        val values = localFileMetadataSnapshot() + (uri to meta)
        p.edit().putString("local_file_meta", dev.endlesssea.app.local.LocalMetadataCodec.encode(values)).apply()
        _visibleLocalMetaTick.value++
    }

    /** Nouvelle signature (tableaux) — compatibilité binaire non requise (usage interne). */
    fun setLocalFileMeta(uri: String, title: String?, coverUri: String?) =
        setLocalFileMeta(uri, LocalFileMeta(title = title, coverUri = coverUri))

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

    /**
     * §catégories-perso : catégories créées par l'utilisateur dans la bibliothèque
     * (onglets en plus de Favoris/Anime/Films/…). Stockées en liste JSON.
     */
    private val _customCategories = MutableStateFlow<List<String>>(loadJsonStringList("lib_categories"))
    val customCategories: StateFlow<List<String>> = _customCategories
    fun addCustomCategory(name: String) {
        val clean = name.trim().replace("\"", "'").take(24)
        if (clean.isBlank() || _customCategories.value.any { it.equals(clean, true) }) return
        val next = _customCategories.value + clean
        p.edit().putString("lib_categories", org.json.JSONArray(next).toString()).apply()
        _customCategories.value = next
    }
    fun removeCustomCategory(name: String) {
        val next = _customCategories.value.filterNot { it == name }
        p.edit().putString("lib_categories", org.json.JSONArray(next).toString()).apply()
        _customCategories.value = next
        p.edit().remove("lib_cat_items_" + name).apply()
        _categoryItemsTick.value++
    }

    /** §catégories-perso : contenu d'une catégorie (URIs de fichiers locaux ou mediaId). */
    private val _categoryItemsTick = MutableStateFlow(0)
    val categoryItemsTick: StateFlow<Int> = _categoryItemsTick
    fun categoryItems(name: String): List<String> = loadJsonStringList("lib_cat_items_" + name)
    fun toggleCategoryItem(name: String, item: String) {
        val cur = categoryItems(name)
        val next = if (item in cur) cur - item else cur + item
        p.edit().putString("lib_cat_items_" + name, org.json.JSONArray(next).toString()).apply()
        _categoryItemsTick.value++
    }

    fun setCategoryItems(name: String, items: List<String>) {
        p.edit().putString("lib_cat_items_" + name, org.json.JSONArray(items.distinct()).toString()).apply()
        _categoryItemsTick.value++
    }

    /** Merge only portable user collections; no storage permissions or arbitrary preference keys. */
    fun mergeBackupCollections(categories: Map<String, List<String>>, metadata: Map<String, LocalFileMeta>) {
        val merged = dev.endlesssea.app.backup.mergeBackupCategories(
            _customCategories.value.associateWith { categoryItems(it) }, categories,
        )
        val names = merged.keys.toList()
        val edit = p.edit()
        merged.forEach { (name, items) ->
            edit.putString("lib_cat_items_" + name, org.json.JSONArray(items).toString())
        }
        edit.putString("lib_categories", org.json.JSONArray(names).toString())
        // Existing local edits win over imported values.
        edit.putString("local_file_meta", dev.endlesssea.app.local.LocalMetadataCodec.encode(metadata + localFileMetadataSnapshot()))
        edit.apply()
        _customCategories.value = names
        _categoryItemsTick.value++
        _visibleLocalMetaTick.value++
    }

    /** §bordures : force des liserés de l'interface (0 = aucune, 100 = marquées). */
    private val _borderStrength = MutableStateFlow(p.getInt("border_strength", 18))
    val borderStrength: StateFlow<Int> = _borderStrength
    fun setBorderStrength(v: Int) {
        val safe = v.coerceIn(0, 100)
        p.edit().putInt("border_strength", safe).apply(); _borderStrength.value = safe
    }

    /** §recherche-sources : identifiants d'extensions EXCLUES de la recherche. */
    private val _searchExcluded = MutableStateFlow(loadJsonStringList("search_excluded").toSet())
    val searchExcluded: StateFlow<Set<String>> = _searchExcluded
    fun toggleSearchExcluded(id: String) {
        val next = if (id in _searchExcluded.value) _searchExcluded.value - id else _searchExcluded.value + id
        p.edit().putString("search_excluded", "[\"" + next.joinToString("\",\"") + "\"]").apply()
        _searchExcluded.value = next
    }

    /** §recherche-resultats : n'afficher que les résultats (sans en-têtes de source). */
    private val _searchResultsOnly = MutableStateFlow(p.getBoolean("search_results_only", false))
    val searchResultsOnly: StateFlow<Boolean> = _searchResultsOnly
    fun setSearchResultsOnly(v: Boolean) {
        p.edit().putBoolean("search_results_only", v).apply(); _searchResultsOnly.value = v
    }

    /** §bibliotheque-locale-fusion : afficher les fichiers locaux avec le reste. */
    private val _mergeLocalLibrary = MutableStateFlow(p.getBoolean("merge_local_library", true))
    val mergeLocalLibrary: StateFlow<Boolean> = _mergeLocalLibrary
    fun setMergeLocalLibrary(v: Boolean) {
        p.edit().putBoolean("merge_local_library", v).apply(); _mergeLocalLibrary.value = v
    }

    // ---------------------------------------------------------------- LECTEUR
    /** §lecteur-placement : "bottom" (défaut) ou "top" pour la barre de progression. */
    private val _progressPosition = MutableStateFlow(p.getString("player_progress_pos", "bottom") ?: "bottom")
    val progressPosition: StateFlow<String> = _progressPosition
    fun setProgressPosition(v: String) {
        val safe = if (v == "top") "top" else "bottom"
        p.edit().putString("player_progress_pos", safe).apply(); _progressPosition.value = safe
    }

    /** §lecteur-placement : où vivent les outils (verrou, vitesse, filtres…). */
    private val _toolsPosition = MutableStateFlow(p.getString("player_tools_pos", "top") ?: "top")
    val toolsPosition: StateFlow<String> = _toolsPosition
    fun setToolsPosition(v: String) {
        val safe = if (v == "bottom") "bottom" else "top"
        p.edit().putString("player_tools_pos", safe).apply(); _toolsPosition.value = safe
    }

    /** §lecteur-placement : côté de la pastille mégaskip ("right" / "left"). */
    private val _megaSkipSide = MutableStateFlow(p.getString("player_mega_side", "right") ?: "right")
    val megaSkipSide: StateFlow<String> = _megaSkipSide
    fun setMegaSkipSide(v: String) {
        val safe = if (v == "left") "left" else "right"
        p.edit().putString("player_mega_side", safe).apply(); _megaSkipSide.value = safe
    }

    /** §barre-progression : épaisseur en dp (2..12) et bouts arrondis. */
    private val _progressThickness = MutableStateFlow(p.getInt("player_progress_thickness", 4).coerceIn(2, 8))
    val progressThickness: StateFlow<Int> = _progressThickness
    fun setProgressThickness(v: Int) {
        val safe = v.coerceIn(2, 8)
        p.edit().putInt("player_progress_thickness", safe).apply(); _progressThickness.value = safe
    }
    private val _progressRounded = MutableStateFlow(p.getBoolean("player_progress_round", true))
    val progressRounded: StateFlow<Boolean> = _progressRounded
    fun setProgressRounded(v: Boolean) {
        p.edit().putBoolean("player_progress_round", v).apply(); _progressRounded.value = v
    }

    /**
     * §theme-lecteur : unique habillage Essentiel. Les anciens identifiants sont
     * migrés vers "cinema", y compris lors de la restauration d'une sauvegarde.
     */
    private val _playerTheme = MutableStateFlow(run {
        // Make the requested visual rebuild visible once; keep the old choice recoverable.
        if (!p.getBoolean("player_essential_v1", false)) {
            p.edit().putString("player_theme_before_essential", p.getString("player_theme", "default"))
                .putString("player_theme", "cinema").putBoolean("player_essential_v1", true).apply()
            "cinema"
        } else migratePlayerTheme(p.getString("player_theme", "cinema"))
    })
    val playerTheme: StateFlow<String> = _playerTheme
    fun setPlayerTheme(v: String) {
        val safe = migratePlayerTheme(v)
        p.edit().putString("player_theme", safe).apply(); _playerTheme.value = safe
    }

    /** §cadrage-video : mode mémorisé entre deux lectures (0..3). */
    private val _playerFramingMode = MutableStateFlow(p.getInt("player_framing_mode", 0).coerceIn(0, 3))
    val playerFramingMode: StateFlow<Int> = _playerFramingMode
    fun setPlayerFramingMode(v: Int) {
        val safe = v.coerceIn(0, 3)
        p.edit().putInt("player_framing_mode", safe).apply()
        _playerFramingMode.value = safe
    }

    private val _seekThumb = MutableStateFlow(p.getInt("seek_thumb", 12).coerceIn(8, 20))
    val seekThumb: StateFlow<Int> = _seekThumb
    fun setSeekThumb(v: Int) { val safe = v.coerceIn(8, 20); p.edit().putInt("seek_thumb", safe).apply(); _seekThumb.value = safe }
    private val _seekBuffer = MutableStateFlow(p.getInt("seek_buffer", 3).coerceIn(2, 8))
    val seekBuffer: StateFlow<Int> = _seekBuffer
    fun setSeekBuffer(v: Int) { val safe = v.coerceIn(2, 8); p.edit().putInt("seek_buffer", safe).apply(); _seekBuffer.value = safe }
    private val _seekHideThumb = MutableStateFlow(p.getBoolean("seek_hide_thumb", false))
    val seekHideThumb: StateFlow<Boolean> = _seekHideThumb
    fun setSeekHideThumb(v: Boolean) { p.edit().putBoolean("seek_hide_thumb", v).apply(); _seekHideThumb.value = v }

    /**
     * §sous-titres (conversation 1) : décalage appliqué aux pistes externes
     * (négatif = sous-titres en avance, positif = en retard), en millisecondes.
     */
    private val _subtitleDelayMs = MutableStateFlow(p.getInt("player_sub_delay_ms", 0))
    val subtitleDelayMs: StateFlow<Int> = _subtitleDelayMs
    fun setSubtitleDelayMs(v: Int) {
        val ms = v.coerceIn(-30_000, 30_000)
        p.edit().putInt("player_sub_delay_ms", ms).apply(); _subtitleDelayMs.value = ms
    }

    /** §audio (conversation 1) : boost de 100 % (normal) à 200 % (×2). */
    private val _audioBoostPercent = MutableStateFlow(p.getInt("player_audio_boost", 100))
    val audioBoostPercent: StateFlow<Int> = _audioBoostPercent
    fun setAudioBoostPercent(v: Int) {
        val percent = v.coerceIn(100, 200)
        p.edit().putInt("player_audio_boost", percent).apply(); _audioBoostPercent.value = percent
    }

    /** §amelioration-video : post-traitement léger appliqué à l'image. */
    private val _videoEnhance = MutableStateFlow(p.getString("video_enhance", "none") ?: "none")
    val videoEnhance: StateFlow<String> = _videoEnhance
    fun setVideoEnhance(v: String) {
        p.edit().putString("video_enhance", v).apply(); _videoEnhance.value = v
    }

    /** §filtres-video : contraste (0.5..2), gamma (0.5..2), netteté (0..1), température (-1..1). */
    private val _videoContrast = MutableStateFlow(p.getFloat("vf_contrast", 1f))
    val videoContrast: StateFlow<Float> = _videoContrast
    private val _videoGamma = MutableStateFlow(p.getFloat("vf_gamma", 1f))
    val videoGamma: StateFlow<Float> = _videoGamma
    private val _videoSharp = MutableStateFlow(p.getFloat("vf_sharp", 0f))
    val videoSharp: StateFlow<Float> = _videoSharp
    private val _videoTemp = MutableStateFlow(p.getFloat("vf_temp", 0f))
    val videoTemp: StateFlow<Float> = _videoTemp
    fun setVideoAdvanced(contrast: Float, gamma: Float, sharp: Float, temp: Float) {
        p.edit()
            .putFloat("vf_contrast", contrast).putFloat("vf_gamma", gamma)
            .putFloat("vf_sharp", sharp).putFloat("vf_temp", temp).apply()
        _videoContrast.value = contrast; _videoGamma.value = gamma
        _videoSharp.value = sharp; _videoTemp.value = temp
    }

    /** §historique-local : les vidéos locales apparaissent dans « Reprendre la lecture ». */
    private val _localInHistory = MutableStateFlow(p.getBoolean("local_in_history", true))
    val localInHistory: StateFlow<Boolean> = _localInHistory
    fun setLocalInHistory(v: Boolean) {
        p.edit().putBoolean("local_in_history", v).apply(); _localInHistory.value = v
    }

    /** §fichiers-caches : inclure les fichiers/dossiers commençant par « . ». */
    private val _showHiddenFiles = MutableStateFlow(p.getBoolean("show_hidden_files", false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles
    fun setShowHiddenFiles(v: Boolean) {
        p.edit().putBoolean("show_hidden_files", v).apply(); _showHiddenFiles.value = v
    }

    /** §affichage-dossiers : "folders" (par dossier) ou "flat" (tous les fichiers). */
    private val _localFolderView = MutableStateFlow(p.getString("local_folder_view", "folders") ?: "folders")
    val localFolderView: StateFlow<String> = _localFolderView
    fun setLocalFolderView(v: String) {
        val safe = if (v == "flat") "flat" else "folders"
        p.edit().putString("local_folder_view", safe).apply(); _localFolderView.value = safe
    }

    /** §reprise-fiche : proposer de reprendre la lecture en ouvrant une fiche. */
    private val _resumePrompt = MutableStateFlow(p.getBoolean("resume_prompt", true))
    val resumePrompt: StateFlow<Boolean> = _resumePrompt
    fun setResumePrompt(v: Boolean) {
        p.edit().putBoolean("resume_prompt", v).apply(); _resumePrompt.value = v
    }

    /** §fond-flou : flou appliqué à l'image d'arrière-plan (0..25 dp). */
    private val _bgBlur = MutableStateFlow(p.getInt("bg_blur", 0))
    val bgBlur: StateFlow<Int> = _bgBlur
    fun setBgBlur(v: Int) {
        val safe = v.coerceIn(0, 25)
        p.edit().putInt("bg_blur", safe).apply(); _bgBlur.value = safe
    }

    /** §langues-extensions : langues retenues pour les sources (vide = toutes). */
    private val _extLanguages = MutableStateFlow(loadJsonStringList("ext_languages").toSet())
    val extLanguages: StateFlow<Set<String>> = _extLanguages
    fun toggleExtLanguage(code: String) {
        val next = if (code in _extLanguages.value) _extLanguages.value - code else _extLanguages.value + code
        p.edit().putString("ext_languages", "[\"" + next.joinToString("\",\"") + "\"]").apply()
        _extLanguages.value = next
    }

    /**
     * §upscale : facteur d'agrandissement de l'image (1 = natif, 1.5, 2…).
     * On parle d'ÉCHELLE, pas de « 720p/1080p » : le résultat dépend de la
     * définition de la source, pas d'une cible fixe.
     */
    private val _videoScale = MutableStateFlow(p.getFloat("video_scale", 1f))
    val videoScale: StateFlow<Float> = _videoScale
    fun setVideoScale(v: Float) {
        val safe = v.coerceIn(1f, 2f)
        p.edit().putFloat("video_scale", safe).apply(); _videoScale.value = safe
    }

    /**
     * §upscale-niveaux (conversation 1) : niveau global d'agrandissement —
     * off / auto / performance / quality. Le niveau pilote l'échelle et les
     * contours ; les curseurs manuels restent disponibles en réglage fin.
     */
    private val _upscaleLevel = MutableStateFlow(p.getString("upscale_level", "off") ?: "off")
    val upscaleLevel: StateFlow<String> = _upscaleLevel
    fun setUpscaleLevel(v: String) {
        p.edit().putString("upscale_level", v).apply(); _upscaleLevel.value = v
    }

    /** §thermique (conversation 1) : réduire l'upscaling si l'appareil chauffe. */
    private val _thermalGuard = MutableStateFlow(p.getBoolean("thermal_guard", true))
    val thermalGuard: StateFlow<Boolean> = _thermalGuard
    fun setThermalGuard(v: Boolean) {
        p.edit().putBoolean("thermal_guard", v).apply(); _thermalGuard.value = v
    }

    /** §upscale : intensité du renforcement de contours (0..2). */
    private val _videoSharpen = MutableStateFlow(p.getFloat("video_sharpen", 0.6f))
    val videoSharpen: StateFlow<Float> = _videoSharpen
    fun setVideoSharpen(v: Float) {
        val safe = v.coerceIn(0f, 2f)
        p.edit().putFloat("video_sharpen", safe).apply(); _videoSharpen.value = safe
    }

    /** §telechargements : tous les emplacements de stockage déjà utilisés. */
    private val _storageHistory = MutableStateFlow(
        (p.getString("storage_history", "") ?: "").split("|").filter { it.isNotBlank() },
    )
    val storageHistory: StateFlow<List<String>> = _storageHistory
    fun rememberStorageRoot(uri: String) {
        if (uri.isBlank()) return
        val next = (_storageHistory.value + uri).distinct().takeLast(8)
        p.edit().putString("storage_history", next.joinToString("|")).apply()
        _storageHistory.value = next
    }

    /** §lisibilite : ombre portée sur les textes (meilleure lecture). */
    private val _textOutline = MutableStateFlow(p.getBoolean("text_outline", true))
    val textOutline: StateFlow<Boolean> = _textOutline
    fun setTextOutline(v: Boolean) {
        p.edit().putBoolean("text_outline", v).apply(); _textOutline.value = v
    }

    /** §luminosite : éclaircissement des surfaces sombres (0..40 %). */
    private val _playerScreenBrightness = MutableStateFlow(p.getInt("player_screen_brightness", 75).coerceIn(10, 100))
    val playerScreenBrightness: StateFlow<Int> = _playerScreenBrightness
    fun setPlayerScreenBrightness(value: Int) {
        val safe = value.coerceIn(10, 100)
        p.edit().putInt("player_screen_brightness", safe).apply()
        _playerScreenBrightness.value = safe
    }

    private val _uiBrightness = MutableStateFlow(p.getInt("ui_brightness", 10))
    val uiBrightness: StateFlow<Int> = _uiBrightness
    fun setUiBrightness(v: Int) {
        val safe = v.coerceIn(0, 40)
        p.edit().putInt("ui_brightness", safe).apply(); _uiBrightness.value = safe
    }

    /**
     * §stockage : dossier choisi par l'utilisateur (SAF), style Aniyomi.
     * C'est le MÊME réglage que « Emplacement des téléchargements » — un seul
     * dossier pour les téléchargements et la bibliothèque hors ligne.
     */
    val storageRoot: StateFlow<String?> get() = storageUri
    fun setStorageRoot(uri: String?) = setStorageUri(uri)

    /** L'utilisateur a fermé la demande d'emplacement sans en choisir un. */
    private val _storagePromptDismissed = MutableStateFlow(p.getBoolean("storage_prompt_dismissed", false))
    val storagePromptDismissed: StateFlow<Boolean> = _storagePromptDismissed
    fun setStoragePromptDismissed(v: Boolean) {
        p.edit().putBoolean("storage_prompt_dismissed", v).apply()
        _storagePromptDismissed.value = v
    }

    private fun loadJsonStringList(key: String): List<String> = runCatching {
        val values = org.json.JSONArray(p.getString(key, "[]") ?: "[]")
        (0 until values.length()).map { values.getString(it) }
    }.getOrDefault(emptyList())

    /** Variante Glass §24 : teinte dominante du verre ("auto" ou nom de ACCENTS). */
    private val _glassVariant = MutableStateFlow(p.getString("glass_variant", "auto") ?: "auto")
    val glassVariant: StateFlow<String> = _glassVariant
    fun setGlassVariant(v: String) { p.edit().putString("glass_variant", v).apply(); _glassVariant.value = v }

    /** §verre-liquide : intensité du dégradé « liquide » sur les cartes verre (0 = off, 100 = fort). */
    private val _liquidGlass = MutableStateFlow(p.getInt("liquid_glass", 70))
    val liquidGlass: StateFlow<Int> = _liquidGlass
    fun setLiquidGlass(v: Int) {
        val c = v.coerceIn(0, 100)
        p.edit().putInt("liquid_glass", c).apply(); _liquidGlass.value = c
    }

    /** §couleurs : liaisons accent → verre (les deux se fondent ; une couleur pour tout). */
    private val _accentLinkedToGlass = MutableStateFlow(p.getBoolean("accent_glass_link", true))
    val accentLinkedToGlass: StateFlow<Boolean> = _accentLinkedToGlass
    fun setAccentLinkedToGlass(v: Boolean) { p.edit().putBoolean("accent_glass_link", v).apply(); _accentLinkedToGlass.value = v }

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

    // ------------------------------------------------ §anymex-ui : styles & mise en page
    // Chaque réglage visuel de la page « Interface » (capture AnyMEX) est persisté ici
    // et republié dans UiTuning par MainActivity, pour que les composants « verre »,
    // les cartes et la barre de navigation le lisent sans injection Hilt.

    /** Style des cartes d'historique : "regular" | "frosted" | "bootiful". */
    private val _historyCardStyle = MutableStateFlow(p.getString("history_card_style", "bootiful") ?: "bootiful")
    val historyCardStyle: StateFlow<String> = _historyCardStyle
    fun setHistoryCardStyle(v: String) {
        val c = if (v in setOf("regular", "frosted", "bootiful")) v else "bootiful"
        p.edit().putString("history_card_style", c).apply(); _historyCardStyle.value = c
    }

    /** Carrousel d'accueil : "classic" (bannière pleine largeur) | "portrait" (affiches verticales). */
    private val _carouselStyle = MutableStateFlow(p.getString("carousel_style", "classic") ?: "classic")
    val carouselStyle: StateFlow<String> = _carouselStyle
    fun setCarouselStyle(v: String) {
        val c = if (v in setOf("classic", "portrait")) v else "classic"
        p.edit().putString("carousel_style", c).apply(); _carouselStyle.value = c
    }

    /** Disposition de la barre : "legacy" (mes onglets) | "modern" (Accueil/Explorer/Biblio/Stats). */
    private val _navBarLayout = MutableStateFlow(p.getString("nav_bar_layout", "legacy") ?: "legacy")
    val navBarLayout: StateFlow<String> = _navBarLayout
    fun setNavBarLayout(v: String) {
        val c = if (v in setOf("legacy", "modern")) v else "legacy"
        p.edit().putString("nav_bar_layout", c).apply(); _navBarLayout.value = c
    }

    private fun mult(key: String, def: Float) = MutableStateFlow(p.getFloat(key, def).coerceIn(0f, 5f))
    private fun saveMult(key: String, flow: MutableStateFlow<Float>, v: Float) {
        val c = (Math.round(v * 10f) / 10f).coerceIn(0f, 5f)
        p.edit().putFloat(key, c).apply(); flow.value = c
    }

    /** Intensité du halo lumineux des éléments (0..5, défaut 1). */
    private val _glowMultiplier = mult("glow_mult", 1f)
    val glowMultiplier: StateFlow<Float> = _glowMultiplier
    fun setGlowMultiplier(v: Float) = saveMult("glow_mult", _glowMultiplier, v)

    /** Rayon des coins des éléments d'interface (0..5, défaut 1). */
    private val _radiusMultiplier = mult("radius_mult", 1f)
    val radiusMultiplier: StateFlow<Float> = _radiusMultiplier
    fun setRadiusMultiplier(v: Float) = saveMult("radius_mult", _radiusMultiplier, v)

    /** Intensité du flou des halos (0..5, défaut 1). */
    private val _blurMultiplier = mult("blur_mult", 1f)
    val blurMultiplier: StateFlow<Float> = _blurMultiplier
    fun setBlurMultiplier(v: Float) = saveMult("blur_mult", _blurMultiplier, v)

    /** Arrondi de toutes les cartes média (0..5, défaut 1). */
    private val _cardRoundness = mult("card_roundness", 1f)
    val cardRoundness: StateFlow<Float> = _cardRoundness
    fun setCardRoundness(v: Float) = saveMult("card_roundness", _cardRoundness, v)

    /** Animation d'appui sur les cartes média. */
    private val _cardAnimation = MutableStateFlow(p.getBoolean("card_animation", true))
    val cardAnimation: StateFlow<Boolean> = _cardAnimation
    fun setCardAnimation(v: Boolean) { p.edit().putBoolean("card_animation", v).apply(); _cardAnimation.value = v }

    /** Animations des carrousels (défilement auto et transitions). */
    private val _enableAnimation = MutableStateFlow(p.getBoolean("enable_animation", true))
    val enableAnimation: StateFlow<Boolean> = _enableAnimation
    fun setEnableAnimation(v: Boolean) { p.edit().putBoolean("enable_animation", v).apply(); _enableAnimation.value = v }

    /** Barre de navigation translucide (sinon opaque, meilleure lisibilité). */
    private val _translucentNav = MutableStateFlow(p.getBoolean("translucent_nav", true))
    val translucentNav: StateFlow<Boolean> = _translucentNav
    fun setTranslucentNav(v: Boolean) { p.edit().putBoolean("translucent_nav", v).apply(); _translucentNav.value = v }

    /** En-tête classique (titre simple) au lieu de l'en-tête immersif des écrans d'accueil. */
    private val _legacyHeader = MutableStateFlow(p.getBoolean("legacy_header", false))
    val legacyHeader: StateFlow<Boolean> = _legacyHeader
    fun setLegacyHeader(v: Boolean) { p.edit().putBoolean("legacy_header", v).apply(); _legacyHeader.value = v }

    // ---------------------------------------------------------------- megaskip (segments en ligne)
    /** §megaskip : saut automatique par type de segment (intro, récap, générique, aperçu). */
    private val _skipAutoIntro = MutableStateFlow(p.getBoolean("skip_auto_intro", true))
    val skipAutoIntro: StateFlow<Boolean> = _skipAutoIntro
    fun setSkipAutoIntro(v: Boolean) { p.edit().putBoolean("skip_auto_intro", v).apply(); _skipAutoIntro.value = v }

    private val _skipAutoRecap = MutableStateFlow(p.getBoolean("skip_auto_recap", false))
    val skipAutoRecap: StateFlow<Boolean> = _skipAutoRecap
    fun setSkipAutoRecap(v: Boolean) { p.edit().putBoolean("skip_auto_recap", v).apply(); _skipAutoRecap.value = v }

    private val _skipAutoCredits = MutableStateFlow(p.getBoolean("skip_auto_credits", true))
    val skipAutoCredits: StateFlow<Boolean> = _skipAutoCredits
    fun setSkipAutoCredits(v: Boolean) { p.edit().putBoolean("skip_auto_credits", v).apply(); _skipAutoCredits.value = v }

    private val _skipAutoPreview = MutableStateFlow(p.getBoolean("skip_auto_preview", false))
    val skipAutoPreview: StateFlow<Boolean> = _skipAutoPreview
    fun setSkipAutoPreview(v: Boolean) { p.edit().putBoolean("skip_auto_preview", v).apply(); _skipAutoPreview.value = v }

    /** §megaskip : délai (s) avant le saut automatique · 0 = immédiat. */
    private val _skipCountdown = MutableStateFlow(p.getInt("skip_countdown", 3))
    val skipCountdown: StateFlow<Int> = _skipCountdown
    fun setSkipCountdown(v: Int) { val c = v.coerceIn(0, 10); p.edit().putInt("skip_countdown", c).apply(); _skipCountdown.value = c }

    /** §megaskip : afficher la pastille « Passer » même quand le saut auto est actif. */
    private val _skipShowButton = MutableStateFlow(p.getBoolean("skip_show_button", true))
    val skipShowButton: StateFlow<Boolean> = _skipShowButton
    fun setSkipShowButton(v: Boolean) { p.edit().putBoolean("skip_show_button", v).apply(); _skipShowButton.value = v }

    /** §megaskip : fournisseurs activés (TheIntroDB, IntroDB, AniSkip). */
    private val _skipProviderTheIntroDb = MutableStateFlow(p.getBoolean("skip_provider_theintrodb", true))
    val skipProviderTheIntroDb: StateFlow<Boolean> = _skipProviderTheIntroDb
    fun setSkipProviderTheIntroDb(v: Boolean) { p.edit().putBoolean("skip_provider_theintrodb", v).apply(); _skipProviderTheIntroDb.value = v }

    private val _skipProviderIntroDb = MutableStateFlow(p.getBoolean("skip_provider_introdb", true))
    val skipProviderIntroDb: StateFlow<Boolean> = _skipProviderIntroDb
    fun setSkipProviderIntroDb(v: Boolean) { p.edit().putBoolean("skip_provider_introdb", v).apply(); _skipProviderIntroDb.value = v }

    private val _skipProviderAniSkip = MutableStateFlow(p.getBoolean("skip_provider_aniskip", true))
    val skipProviderAniSkip: StateFlow<Boolean> = _skipProviderAniSkip
    fun setSkipProviderAniSkip(v: Boolean) { p.edit().putBoolean("skip_provider_aniskip", v).apply(); _skipProviderAniSkip.value = v }

    // ---------------------------------------------------------------- gestes du lecteur
    /** §gestes-lecteur : pincer pour zoomer (1×–3×) + déplacement à deux doigts. */
    private val _pinchZoom = MutableStateFlow(p.getBoolean("gesture_pinch_zoom", true))
    val pinchZoom: StateFlow<Boolean> = _pinchZoom
    fun setPinchZoom(v: Boolean) { p.edit().putBoolean("gesture_pinch_zoom", v).apply(); _pinchZoom.value = v }

    /** §gestes-lecteur : inverser les glissers verticaux (volume à gauche, luminosité à droite). */
    private val _swapVolumeBrightness = MutableStateFlow(p.getBoolean("gesture_swap_vb", false))
    val swapVolumeBrightness: StateFlow<Boolean> = _swapVolumeBrightness
    fun setSwapVolumeBrightness(v: Boolean) { p.edit().putBoolean("gesture_swap_vb", v).apply(); _swapVolumeBrightness.value = v }

    /** §suivi : marquer automatiquement l'épisode suivant comme vu sur les services. */
    private val _autoMarkWatched = MutableStateFlow(p.getBoolean("tracker_auto_mark", true))
    val autoMarkWatched: StateFlow<Boolean> = _autoMarkWatched
    fun setAutoMarkWatched(v: Boolean) {
        p.edit().putBoolean("tracker_auto_mark", v).apply(); _autoMarkWatched.value = v
    }

    /** §suivi : utiliser TMDB pour compléter affiches et bandes-annonces manquantes. */
    private val _enrichWithTmdb = MutableStateFlow(p.getBoolean("tracker_enrich_tmdb", true))
    val enrichWithTmdb: StateFlow<Boolean> = _enrichWithTmdb
    fun setEnrichWithTmdb(v: Boolean) {
        p.edit().putBoolean("tracker_enrich_tmdb", v).apply(); _enrichWithTmdb.value = v
    }

    /** Langue TMDB des titres, synopsis et genres (n'affecte pas les services de suivi). */
    private val _metadataLanguage = MutableStateFlow(p.getString("metadata_language", "fr-FR") ?: "fr-FR")
    val metadataLanguage: StateFlow<String> = _metadataLanguage
    fun setMetadataLanguage(v: String) {
        val safe = v.takeIf { it in listOf("fr-FR", "en-US", "ja-JP", "es-ES") } ?: "fr-FR"
        p.edit().putString("metadata_language", safe).apply()
        _metadataLanguage.value = safe
    }

    // ---------------------------------------------------------------- téléchargement avancé
    /** §debit (conversation 10) : plafond de bande passante en Ko/s (0 = illimité). */
    private val _downloadSpeedLimitKb = MutableStateFlow(p.getInt("dl_speed_limit_kb", 0))
    val downloadSpeedLimitKb: StateFlow<Int> = _downloadSpeedLimitKb
    fun setDownloadSpeedLimitKb(v: Int) {
        val c = v.coerceIn(0, 50_000)
        p.edit().putInt("dl_speed_limit_kb", c).apply(); _downloadSpeedLimitKb.value = c
    }

    /** One task and one connection. Low available memory applies the same cap even when this is off. */
    private val _dataSaver = MutableStateFlow(p.getBoolean("dl_data_saver", false))
    val dataSaver: StateFlow<Boolean> = _dataSaver
    fun setDataSaver(v: Boolean) {
        p.edit().putBoolean("dl_data_saver", v).apply(); _dataSaver.value = v
    }

    private val _extensionRepos = MutableStateFlow(loadJsonStringList("extension_repos"))
    val extensionRepos: StateFlow<List<String>> = _extensionRepos
    private val _extensionRepoSeeded = MutableStateFlow(p.getBoolean("extension_repo_seeded", false))
    val extensionRepoSeeded: StateFlow<Boolean> = _extensionRepoSeeded
    fun setExtensionRepos(urls: List<String>) {
        val next = urls.map { it.trim() }.filter { it.startsWith("http") }.distinct()
        p.edit().putString("extension_repos", org.json.JSONArray(next).toString()).apply()
        _extensionRepos.value = next
    }
    fun rememberExtensionRepo(url: String) = setExtensionRepos(_extensionRepos.value + url)
    fun forgetExtensionRepo(url: String) {
        setExtensionRepos(_extensionRepos.value.filterNot { it == url })
        setExtensionRepoSeeded(true)
    }
    fun setExtensionRepoSeeded(v: Boolean) {
        p.edit().putBoolean("extension_repo_seeded", v).apply()
        _extensionRepoSeeded.value = v
    }

    /** §nettoyage (conversation 10) : supprime les téléchargements terminés après N jours (0 = jamais). */
    private val _downloadAutoCleanDays = MutableStateFlow(p.getInt("dl_auto_clean_days", 0))
    val downloadAutoCleanDays: StateFlow<Int> = _downloadAutoCleanDays
    fun setDownloadAutoCleanDays(v: Int) {
        val c = if (v in setOf(0, 3, 7, 30, 90)) v else 0
        p.edit().putInt("dl_auto_clean_days", c).apply(); _downloadAutoCleanDays.value = c
    }

    /** §stats : surimpression technique (résolution, débit, codec, images perdues). */
    private val _playerStats = MutableStateFlow(p.getBoolean("player_stats", false))
    val playerStats: StateFlow<Boolean> = _playerStats
    fun setPlayerStats(v: Boolean) { p.edit().putBoolean("player_stats", v).apply(); _playerStats.value = v }

    // ---------------------------------------------------------------- tracker par défaut
    /** §tracker-choose : service de tracking par défaut pour le tracking automatique. */
    private val _defaultTrackerService = MutableStateFlow(p.getString("default_tracker_service", null))
    val defaultTrackerService: StateFlow<String?> = _defaultTrackerService
    fun setDefaultTrackerService(v: String?) {
        p.edit().putString("default_tracker_service", v).apply(); _defaultTrackerService.value = v
    }
    fun getDefaultTrackerService(): String? = _defaultTrackerService.value

    init {
        // Synchronise le résolveur réseau global dès la création des préférences.
        dev.endlesssea.core.net.EsNet.dnsMode = _dnsMode.value
    }
}
