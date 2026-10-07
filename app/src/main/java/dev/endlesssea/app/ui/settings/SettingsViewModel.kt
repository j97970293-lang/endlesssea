package dev.endlesssea.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.di.AppPrefs
import dev.endlesssea.data.db.GenreDao
import dev.endlesssea.data.db.GenreEntity
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.LibraryEntity
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class GenreUi(val id: Long, val name: String, val visible: Boolean, val position: Int)

/** Une entrée éditable déclarée par une extension + sa valeur courante. */
data class ExtSettingEntryUi(
    val key: String,
    val title: String,
    val summary: String?,
    val type: dev.endlesssea.extensions.api.model.ExtensionSetting.Type,
    val value: String,
    /** Choix proposés quand type = LIST (puces sélectionnables). */
    val options: List<String> = emptyList(),
)

/** Ensemble de réglages d'une extension, ouvert dans la boîte de dialogue. */
data class ExtSettingsUi(
    val pkg: String,
    val name: String,
    val entries: List<ExtSettingEntryUi>,
)

data class SettingsUiState(
    val storageUri: String? = null,
    val wifiOnly: Boolean = true,
    val partsPerTask: Int = 4,
    val parallelTasks: Int = 2,
    val defaultSpeed: Float = 1f,
    val autoResume: Boolean = true,
    val skipSeconds: Int = 10,
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0,
    val themeMode: Int = AppPrefs.THEME_SYSTEM,
    val glassOverlay: Int = 12,
    val glassScrim: Int = 25,
    val glassVariant: String = "auto",
    val logoTint: String = "original",
    val fontId: String = "system",
    val dnsMode: String = "system",
    val cardStyle: String = "detail",
    val barTabs: Set<String> = AppPrefs.DEFAULT_TABS,
    val tabOrder: List<String> = AppPrefs.ALL_TAB_ROUTES,
    val barMargin: Int = 16,
    val accent: String = AppPrefs.DEFAULT_ACCENT,
    val bgImageUri: String? = null,
    val bgDim: Int = 35,
    val preferredAudioLang: String = "auto",
    val genres: List<GenreUi> = emptyList(),
    val updateAutoCheck: Boolean = true,
    val availableUpdate: dev.endlesssea.app.update.AppUpdateInfo? = null,
    /** Dernière MAJ tentée — permet le « Réessayer » du dialogue d'échec. */
    val lastFailedUpdate: dev.endlesssea.app.update.AppUpdateInfo? = null,
    val updateChecking: Boolean = false,
    /** Extensions activées (pkg → nom) proposées pour les réglages par extension. */
    val extWithSettings: List<Pair<String, String>> = emptyList(),
    /** Boîte de dialogue de réglages d'extension actuellement ouverte. */
    val editingExt: ExtSettingsUi? = null,
    val extSettingsLoading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: AppPrefs,
    private val genreDao: GenreDao,
    private val libraryDao: LibraryDao,
    private val historyDao: WatchHistoryDao,
    private val updateChecker: dev.endlesssea.app.update.UpdateChecker,
    private val extensionDao: dev.endlesssea.data.db.ExtensionDao,
    private val extSettingsStore: dev.endlesssea.app.data.ExtensionSettingsStore,
    private val registry: dev.endlesssea.extensions.loader.ExtensionRegistry,
    private val skipRepository: dev.endlesssea.app.skip.SkipRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    companion object {
        private const val BACKUP_INTERVAL_MS = 24L * 60 * 60 * 1000
    }

    // ---- §anymex-theme : réglages de la page Thème exposés en direct
    val usePosterColor = prefs.usePosterColor
    fun setUsePosterColor(v: Boolean) = prefs.setUsePosterColor(v)
    val bloom = prefs.bloom
    fun setBloom(v: Boolean) = prefs.setBloom(v)
    val grain = prefs.grain
    fun setGrain(v: Boolean) = prefs.setGrain(v)
    val navBarStyle = prefs.navBarStyle
    fun setNavBarStyle(v: String) = prefs.setNavBarStyle(v)

    // ------------------------------------------------ §anymex-ui : page « Interface »
    val navBarLayout: kotlinx.coroutines.flow.StateFlow<String> = prefs.navBarLayout
    fun setNavBarLayout(v: String) = prefs.setNavBarLayout(v)
    val historyCardStyle: kotlinx.coroutines.flow.StateFlow<String> = prefs.historyCardStyle
    fun setHistoryCardStyle(v: String) = prefs.setHistoryCardStyle(v)
    val carouselStyle: kotlinx.coroutines.flow.StateFlow<String> = prefs.carouselStyle
    fun setCarouselStyle(v: String) = prefs.setCarouselStyle(v)
    val glowMultiplier: kotlinx.coroutines.flow.StateFlow<Float> = prefs.glowMultiplier
    fun setGlowMultiplier(v: Float) = prefs.setGlowMultiplier(v)
    val radiusMultiplier: kotlinx.coroutines.flow.StateFlow<Float> = prefs.radiusMultiplier
    fun setRadiusMultiplier(v: Float) = prefs.setRadiusMultiplier(v)
    val blurMultiplier: kotlinx.coroutines.flow.StateFlow<Float> = prefs.blurMultiplier
    fun setBlurMultiplier(v: Float) = prefs.setBlurMultiplier(v)
    val cardRoundness: kotlinx.coroutines.flow.StateFlow<Float> = prefs.cardRoundness
    fun setCardRoundness(v: Float) = prefs.setCardRoundness(v)
    val cardAnimation: kotlinx.coroutines.flow.StateFlow<Boolean> = prefs.cardAnimation
    fun setCardAnimation(v: Boolean) = prefs.setCardAnimation(v)
    val enableAnimation: kotlinx.coroutines.flow.StateFlow<Boolean> = prefs.enableAnimation
    fun setEnableAnimation(v: Boolean) = prefs.setEnableAnimation(v)
    val translucentNav: kotlinx.coroutines.flow.StateFlow<Boolean> = prefs.translucentNav
    fun setTranslucentNav(v: Boolean) = prefs.setTranslucentNav(v)
    val legacyHeader: kotlinx.coroutines.flow.StateFlow<Boolean> = prefs.legacyHeader
    fun setLegacyHeader(v: Boolean) = prefs.setLegacyHeader(v)
    val immersiveModeFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = prefs.immersiveMode
    val immersiveMode = prefs.immersiveMode
    fun setImmersiveMode(v: Boolean) = prefs.setImmersiveMode(v)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch { prefs.storageUri.collect { v -> set { copy(storageUri = v) } } }
        viewModelScope.launch { prefs.wifiOnly.collect { v -> set { copy(wifiOnly = v) } } }
        viewModelScope.launch { prefs.partsPerTask.collect { v -> set { copy(partsPerTask = v) } } }
        viewModelScope.launch { prefs.parallelTasks.collect { v -> set { copy(parallelTasks = v) } } }
        viewModelScope.launch { prefs.defaultSpeed.collect { v -> set { copy(defaultSpeed = v) } } }
        viewModelScope.launch { prefs.autoResume.collect { v -> set { copy(autoResume = v) } } }
        viewModelScope.launch { prefs.themeMode.collect { v -> set { copy(themeMode = v) } } }
        viewModelScope.launch { prefs.glassOverlay.collect { v -> set { copy(glassOverlay = v) } } }
        viewModelScope.launch { prefs.glassScrim.collect { v -> set { copy(glassScrim = v) } } }
        viewModelScope.launch { prefs.glassVariant.collect { v -> set { copy(glassVariant = v) } } }
        viewModelScope.launch { prefs.logoTint.collect { v -> set { copy(logoTint = v) } } }
        viewModelScope.launch { prefs.fontId.collect { v -> set { copy(fontId = v) } } }
        viewModelScope.launch { prefs.dnsMode.collect { v -> set { copy(dnsMode = v) } } }
        viewModelScope.launch { prefs.cardStyle.collect { v -> set { copy(cardStyle = v) } } }
        viewModelScope.launch { prefs.barTabs.collect { v -> set { copy(barTabs = v) } } }
        viewModelScope.launch { prefs.tabOrder.collect { v -> set { copy(tabOrder = v) } } }
        viewModelScope.launch { prefs.skipSeconds.collect { v -> set { copy(skipSeconds = v) } } }
        viewModelScope.launch { prefs.autoBackup.collect { v -> set { copy(autoBackup = v) } } }
        viewModelScope.launch { prefs.bgImageUri.collect { v -> set { copy(bgImageUri = v) } } }
        viewModelScope.launch { prefs.bgDim.collect { v -> set { copy(bgDim = v) } } }
        viewModelScope.launch { prefs.preferredAudioLang.collect { v -> set { copy(preferredAudioLang = v) } } }
        set { copy(lastBackupAt = prefs.lastAutoBackupAt) }
        viewModelScope.launch { maybeAutoBackup() }
        viewModelScope.launch { prefs.barMargin.collect { v -> set { copy(barMargin = v) } } }
        viewModelScope.launch { prefs.accent.collect { v -> set { copy(accent = v) } } }
        viewModelScope.launch { prefs.updateAutoCheck.collect { v -> set { copy(updateAutoCheck = v) } } }
        viewModelScope.launch {
            extensionDao.observeInstalled().collect { list ->
                set {
                    copy(extWithSettings = list.filter { it.status == "ENABLED" }.map { it.pkg to it.name })
                }
            }
        }
        viewModelScope.launch {
            genreDao.observeAll()
                .catch { /* la table est créée par seed — jamais vide de fait */ }
                .collect { g -> set { copy(genres = g.map { GenreUi(it.id, it.name, it.visible, it.position) }) } }
        }
    }

    private inline fun set(block: SettingsUiState.() -> SettingsUiState) {
        _uiState.value = _uiState.value.block()
    }

    // ----------------------------------------------------------- préférences

    fun onStorageChosen(uri: String?) {
        prefs.setStorageUri(uri)
        toastState(if (uri != null) "Emplacement enregistré" else "Emplacement réinitialisé")
    }

    fun setWifiOnly(v: Boolean) { prefs.setWifiOnly(v); toastState(if (v) "Wi-Fi uniquement activé" else "Téléchargement sur données mobiles autorisé") }
    fun setPartsPerTask(v: Int) { prefs.setPartsPerTask(v); toastState("$v segments par fichier") }
    fun setParallelTasks(v: Int) { prefs.setParallelTasks(v); toastState("$v tâche(s) simultanée(s)") }
    fun setDefaultSpeed(v: Float) { prefs.setDefaultSpeed(v); toastState("Vitesse par défaut : ${v}×") }
    fun setAutoResume(v: Boolean) { prefs.setAutoResume(v); toastState(if (v) "Reprise automatique activée" else "Reprise automatique désactivée") }
    fun setThemeMode(mode: Int) { prefs.setThemeMode(mode) }
    fun setGlassOverlay(v: Int) { prefs.setGlassOverlay(v) }
        /** §verre-liquide : intensité du reflet « lentille » des cartes verre (0..100). */
    val liquidGlass: StateFlow<Int> = prefs.liquidGlass
    /** §couleurs : liaison accent → verre (fusion demandée). */
    val accentGlassLink: StateFlow<Boolean> = prefs.accentLinkedToGlass
    fun setGlassVariant(v: String) { prefs.setGlassVariant(v) }
    fun setLiquidGlass(v: Int) { prefs.setLiquidGlass(v) }
    /** §mégaskip / §auto-skip / §orientation-lecteur */
    val megaSkipSeconds: StateFlow<Int> = prefs.megaSkipSeconds

    /** §debit (conversation 10) : plafond de bande passante des téléchargements. */
    val downloadSpeedLimitKb: StateFlow<Int> = prefs.downloadSpeedLimitKb
    fun setDownloadSpeedLimitKb(kb: Int) = prefs.setDownloadSpeedLimitKb(kb)

    /** §netto-automatique (conversation 10) : purge des fichiers terminés (jours). */
    val downloadAutoCleanDays: StateFlow<Int> = prefs.downloadAutoCleanDays
    fun setDownloadAutoCleanDays(days: Int) = prefs.setDownloadAutoCleanDays(days)
    val autoSkipMarkers: StateFlow<Boolean> = prefs.autoSkipMarkers
    val playerOrientation: StateFlow<String> = prefs.playerOrientation
    fun setMegaSkipSeconds(v: Int) { prefs.setMegaSkipSeconds(v) }
    fun setAutoSkipMarkers(v: Boolean) { prefs.setAutoSkipMarkers(v) }

    // ---- §megaskip : segments communautaires + boutons de saut personnalisés
    val skipAutoIntro: StateFlow<Boolean> = prefs.skipAutoIntro
    val skipAutoRecap: StateFlow<Boolean> = prefs.skipAutoRecap
    val skipAutoCredits: StateFlow<Boolean> = prefs.skipAutoCredits
    val skipAutoPreview: StateFlow<Boolean> = prefs.skipAutoPreview
    val skipCountdown: StateFlow<Int> = prefs.skipCountdown
    val skipShowButton: StateFlow<Boolean> = prefs.skipShowButton
    val skipProviderTheIntroDb: StateFlow<Boolean> = prefs.skipProviderTheIntroDb
    val skipProviderIntroDb: StateFlow<Boolean> = prefs.skipProviderIntroDb
    val skipProviderAniSkip: StateFlow<Boolean> = prefs.skipProviderAniSkip
    fun setSkipAutoIntro(v: Boolean) { prefs.setSkipAutoIntro(v) }
    fun setSkipAutoRecap(v: Boolean) { prefs.setSkipAutoRecap(v) }
    fun setSkipAutoCredits(v: Boolean) { prefs.setSkipAutoCredits(v) }
    fun setSkipAutoPreview(v: Boolean) { prefs.setSkipAutoPreview(v) }
    fun setSkipCountdown(v: Int) { prefs.setSkipCountdown(v) }
    fun setSkipShowButton(v: Boolean) { prefs.setSkipShowButton(v) }
    fun setSkipProviderTheIntroDb(v: Boolean) { prefs.setSkipProviderTheIntroDb(v) }
    fun setSkipProviderIntroDb(v: Boolean) { prefs.setSkipProviderIntroDb(v) }
    fun setSkipProviderAniSkip(v: Boolean) { prefs.setSkipProviderAniSkip(v) }

    // ---- §gestes-lecteur + §stats
    val pinchZoom: StateFlow<Boolean> = prefs.pinchZoom
    val swapVolumeBrightness: StateFlow<Boolean> = prefs.swapVolumeBrightness
    val playerStats: StateFlow<Boolean> = prefs.playerStats
    fun setPinchZoom(v: Boolean) { prefs.setPinchZoom(v) }
    fun setSwapVolumeBrightness(v: Boolean) { prefs.setSwapVolumeBrightness(v) }
    fun setPlayerStats(v: Boolean) { prefs.setPlayerStats(v) }

    /** Boutons de saut personnalisés (table locale — disponibles hors-ligne). */
    val skipButtons: kotlinx.coroutines.flow.Flow<List<dev.endlesssea.app.skip.CustomSkipButton>> =
        skipRepository.observeButtons()
    fun addSkipButton(label: String, seconds: Int) = viewModelScope.launch {
        skipRepository.addButton(label, seconds)
    }
    fun updateSkipButton(button: dev.endlesssea.app.skip.CustomSkipButton) = viewModelScope.launch {
        skipRepository.updateButton(button)
    }
    fun deleteSkipButton(id: Long) = viewModelScope.launch { skipRepository.deleteButton(id) }
    fun clearSkipCache() = viewModelScope.launch {
        skipRepository.clearCache()
        _uiState.value = _uiState.value.copy(message = "Cache Megaskip vidé")
    }
    fun setPlayerOrientation(v: String) { prefs.setPlayerOrientation(v) }

    // §placements-lecteur / §barre-progression / §theme-lecteur
    val progressPosition: StateFlow<String> = prefs.progressPosition
    val toolsPosition: StateFlow<String> = prefs.toolsPosition
    val megaSkipSide: StateFlow<String> = prefs.megaSkipSide
    val progressThickness: StateFlow<Int> = prefs.progressThickness
    val progressRounded: StateFlow<Boolean> = prefs.progressRounded
    val playerTheme: StateFlow<String> = prefs.playerTheme
    val videoEnhance: StateFlow<String> = prefs.videoEnhance
    val videoScale: StateFlow<Float> = prefs.videoScale
    val videoSharpen: StateFlow<Float> = prefs.videoSharpen
    fun setVideoScale(v: Float) = prefs.setVideoScale(v)
    fun setVideoSharpen(v: Float) = prefs.setVideoSharpen(v)
    // §fusion-filtres : les filtres vidéo vivent avec les autres réglages du lecteur
    val videoContrast: StateFlow<Float> = prefs.videoContrast
    val videoGamma: StateFlow<Float> = prefs.videoGamma
    val videoSharp: StateFlow<Float> = prefs.videoSharp
    val videoTemp: StateFlow<Float> = prefs.videoTemp
    fun setVideoAdvanced(contrast: Float, gamma: Float, sharp: Float, temp: Float) =
        prefs.setVideoAdvanced(contrast, gamma, sharp, temp)
    val borderStrength: StateFlow<Int> = prefs.borderStrength
    fun setProgressPosition(v: String) = prefs.setProgressPosition(v)
    fun setToolsPosition(v: String) = prefs.setToolsPosition(v)
    fun setMegaSkipSide(v: String) = prefs.setMegaSkipSide(v)
    fun setProgressThickness(v: Int) = prefs.setProgressThickness(v)
    fun setProgressRounded(v: Boolean) = prefs.setProgressRounded(v)
    fun setPlayerTheme(v: String) = prefs.setPlayerTheme(v)
    fun setVideoEnhance(v: String) = prefs.setVideoEnhance(v)
    fun setBorderStrength(v: Int) = prefs.setBorderStrength(v)

    // §fond-flou / §historique-local / §fichiers-caches / §reprise-fiche
    val uiBrightness: StateFlow<Int> = prefs.uiBrightness
    val textOutline: StateFlow<Boolean> = prefs.textOutline
    fun setTextOutline(v: Boolean) = prefs.setTextOutline(v)
    fun setUiBrightness(v: Int) = prefs.setUiBrightness(v)
    val storageRoot: StateFlow<String?> = prefs.storageRoot
    fun setStorageRoot(uri: String?) = prefs.setStorageRoot(uri)
    val bgBlur: StateFlow<Int> = prefs.bgBlur
    fun setBgBlur(v: Int) = prefs.setBgBlur(v)
    val localInHistory: StateFlow<Boolean> = prefs.localInHistory
    fun setLocalInHistory(v: Boolean) = prefs.setLocalInHistory(v)
    val showHiddenFiles: StateFlow<Boolean> = prefs.showHiddenFiles
    fun setShowHiddenFiles(v: Boolean) = prefs.setShowHiddenFiles(v)
    val resumePrompt: StateFlow<Boolean> = prefs.resumePrompt
    fun setResumePrompt(v: Boolean) = prefs.setResumePrompt(v)

    /** §rendu-vidéo : TextureView (filtres) ou SurfaceView (perf/HDR). */
    val videoRender: StateFlow<String> = prefs.videoRender
    fun setVideoRender(v: String) { prefs.setVideoRender(v) }
    fun setAccentGlassLink(v: Boolean) { prefs.setAccentLinkedToGlass(v) }
    fun setLogoTint(v: String) { prefs.setLogoTint(v); toastState(if (v == "original") "Logo original" else "Logo teinté $v") }
    fun setFontId(v: String) { prefs.setFontId(v); toastState("Police appliquée") }
    fun setDnsMode(v: String) {
        prefs.setDnsMode(v)
        toastState(
            when (v) {
                "system" -> "DNS système (par défaut)"
                "cloudflare" -> "DNS Cloudflare — appliqué au prochain démarrage"
                else -> "DNS Google — appliqué au prochain démarrage"
            },
        )
    }
    /** Choisit / retire l'image de fond (URI pris en charge en dur pour survire aux redémarrages). */
    fun setBgImage(uri: String?) { prefs.setBgImage(uri); toastState(if (uri != null) "Image de fond appliquée" else "Image de fond retirée") }
    fun setBgDim(v: Int) { prefs.setBgDim(v) }
    fun setPreferredAudioLang(v: String) {
        prefs.setPreferredAudioLang(v)
        toastState("Langue préférée : " + when (v) { "vf" -> "VF"; "vostfr" -> "VOSTFR"; "vo" -> "VO"; else -> "Auto" })
    }
    fun setGlassScrim(v: Int) { prefs.setGlassScrim(v) }
    fun setCardStyle(v: String) { prefs.setCardStyle(v) }
    fun setSkipSeconds(v: Int) { prefs.setSkipSeconds(v) }
    fun setAutoBackup(v: Boolean) { prefs.setAutoBackup(v) }
    fun setBarTab(route: String, enabled: Boolean) { prefs.setBarTab(route, enabled) }
    fun moveTab(route: String, delta: Int) { prefs.moveTab(route, delta) }
    fun setBarMargin(dp: Int) { prefs.setBarMargin(dp) }
    fun setAccent(name: String) { prefs.setAccent(name) }

    // ------------------------------------------------- réglages par extension

    /** Ouvre la boîte des réglages de l'extension [pkg] (déclaration chargée à la demande). */
    fun openExtSettings(pkg: String, name: String) = viewModelScope.launch {
        set { copy(extSettingsLoading = true, editingExt = null) }
        val decl = runCatching { registry.instance(pkg).settings() }
            .onFailure { toastState("Impossible de lire les réglages de « $name » : ${it.message}") }
            .getOrNull()
        if (!decl.isNullOrEmpty()) {
            val values = extSettingsStore.getAll(pkg)
            set {
                copy(
                    editingExt = ExtSettingsUi(
                        pkg = pkg, name = name,
                        entries = decl.map { s ->
                            ExtSettingEntryUi(
                                key = s.key, title = s.title, summary = s.summary, type = s.type,
                                options = s.options,
                                value = values[s.key] ?: s.defaultValue,
                            )
                        },
                    ),
                )
            }
        } else if (decl != null) {
            toastState("« $name » ne déclare aucun réglage")
        }
        set { copy(extSettingsLoading = false) }
    }

    /** Écrit une valeur, invalide l'instance (l'extension la relira au prochain appel). */
    fun saveExtSetting(pkg: String, key: String, value: String) {
        extSettingsStore.set(pkg, key, value)
        viewModelScope.launch { registry.invalidate(pkg) }
        set {
            copy(
                editingExt = editingExt?.let { cur ->
                    cur.copy(entries = cur.entries.map { if (it.key == key) it.copy(value = value) else it })
                },
            )
        }
        toastState("Réglage enregistré — pris en compte à la prochaine ouverture")
    }

    fun closeExtSettings() { set { copy(editingExt = null) } }

    // ---------------------------------------------------------------- genres

    fun addGenre(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isBlank()) { toastState("Nom de genre vide"); return@launch }
        val nextPosition = (genreDao.observeAll().first().maxOfOrNull { it.position } ?: -1) + 1
        genreDao.upsert(GenreEntity(name = trimmed, position = nextPosition, visible = true))
        toastState("Genre « $trimmed » ajouté")
    }

    fun renameGenre(id: Long, newName: String) = viewModelScope.launch {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) { toastState("Nom vide — rien renommé"); return@launch }
        genreDao.rename(id, trimmed)
        toastState("Genre renommé en « $trimmed »")
    }

    fun deleteGenre(id: Long) = viewModelScope.launch {
        genreDao.delete(id)
        toastState("Genre supprimé")
    }

    fun toggleGenre(id: Long, visible: Boolean) = viewModelScope.launch {
        genreDao.setVisible(id, visible)
    }

    fun moveGenre(id: Long, delta: Int) = viewModelScope.launch {
        val all = genreDao.observeAll().first()
        val idx = all.indexOfFirst { it.id == id }
        val target = idx + delta
        if (idx < 0 || target !in all.indices) return@launch
        genreDao.reorder(all[idx].id, all[target].position)
        genreDao.reorder(all[target].id, all[idx].position)
    }

    // --------------------------------------------------------- mises à jour

    fun setUpdateAutoCheck(v: Boolean) {
        prefs.setUpdateAutoCheck(v)
        toastState(if (v) "Recherche de mises à jour automatique activée" else "Recherche automatique désactivée")
    }

    /** Vérification manuelle (« Vérifier les mises à jour »). */
    fun checkForUpdate() = viewModelScope.launch {
        set { copy(updateChecking = true, availableUpdate = null) }
        val latest = updateChecker.latest()
        when {
            latest == null -> toastState("Vérification impossible pour l'instant (réseau ?)")
            updateChecker.isNewer(latest.tag) -> set { copy(availableUpdate = latest) }
            else -> toastState("Vous êtes déjà à jour")
        }
        set { copy(updateChecking = false) }
    }

    fun dismissUpdate() { set { copy(availableUpdate = null) } }

    /** §5 : démarre le téléchargement in-app avec dialogue de progression. */
    fun startUpdate(context: android.content.Context) {
        val info = _uiState.value.availableUpdate ?: return
        set { copy(availableUpdate = null, lastFailedUpdate = info) }
        viewModelScope.launch { updateChecker.downloadUpdate(context, info) }
    }

    fun retryUpdateDownload(context: android.content.Context, update: dev.endlesssea.app.update.AppUpdateInfo) {
        viewModelScope.launch { updateChecker.downloadUpdate(context, update) }
    }

    // ------------------------------------------------------------ sauvegarde

    /** Produit le JSON de sauvegarde (bibliothèque, favoris, historique). */
    suspend fun buildBackupJson(): String {
        val library = libraryDao.observeByCategory("ANIME").first() +
            libraryDao.observeByCategory("FILM").first() +
            libraryDao.observeByCategory("SERIE").first() +
            libraryDao.observeByCategory("OVA").first() +
            libraryDao.observeByCategory("ONA").first() +
            libraryDao.observeFavorites().first()
        val uniqueLib = library.distinctBy { it.mediaId }
        val history = historyDao.observeContinueWatching(500).first()

        val root = JSONObject().apply {
            put("app", "endless_sea")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("library", JSONArray().apply {
                uniqueLib.forEach {
                    put(JSONObject().apply {
                        put("mediaId", it.mediaId)
                        put("category", it.category)
                        put("favorite", it.favorite)
                        put("addedAt", it.addedAt)
                    })
                }
            })
            put("history", JSONArray().apply {
                history.forEach {
                    put(JSONObject().apply {
                        put("episodeId", it.episodeId)
                        put("mediaId", it.mediaId)
                        put("positionMs", it.positionMs)
                        put("durationMs", it.durationMs)
                        put("watched", it.watched)
                        put("updatedAt", it.updatedAt)
                    })
                }
            })
        }
        return root.toString(2)
    }

    /** Restaure le JSON produit par [buildBackupJson]. Retourne un message de bilan. */
    suspend fun restoreBackup(json: String): String = runCatching {
        val root = JSONObject(json)
        var libCount = 0
        root.optJSONArray("library")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                libraryDao.upsert(
                    LibraryEntity(
                        mediaId = e.getString("mediaId"),
                        category = e.optString("category", "ANIME"),
                        favorite = e.optBoolean("favorite", false),
                        addedAt = e.optLong("addedAt", System.currentTimeMillis()),
                    ),
                )
                libCount++
            }
        }
        var histCount = 0
        root.optJSONArray("history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                historyDao.upsert(
                    WatchHistoryEntity(
                        episodeId = e.getString("episodeId"),
                        mediaId = e.optString("mediaId", ""),
                        positionMs = e.optLong("positionMs", 0),
                        durationMs = e.optLong("durationMs", 0),
                        watched = e.optBoolean("watched", false),
                        updatedAt = e.optLong("updatedAt", System.currentTimeMillis()),
                    ),
                )
                histCount++
            }
        }
        "Sauvegarde restaurée : $libCount entrée(s) de bibliothèque, $histCount historique(s)"
    }.getOrElse { "Sauvegarde illisible : ${it.message}" }

    /** Sauvegarde planifiée locale : une fois par jour, fichiers datés dans le dossier privé. */
    private suspend fun maybeAutoBackup() {
        if (!prefs.autoBackup.value) return
        val now = System.currentTimeMillis()
        if (now - prefs.lastAutoBackupAt < BACKUP_INTERVAL_MS) return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val dir = (context.getExternalFilesDir(null) ?: context.filesDir)
                    .resolve("EndlessSea/backups").apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date(now))
                dir.resolve("endlesssea-backup-$stamp.json").writeText(buildBackupJson())
                prefs.lastAutoBackupAt = now
                set { copy(lastBackupAt = now) }
            }
        }
    }

    fun onBackupExported(ok: Boolean) = toastState(if (ok) "Fichier de sauvegarde écrit" else "Export annulé")
    fun onBackupImported(message: String) = toastState(message)

    fun toastState(message: String) { set { copy(message = message) } }
    fun clearMessage() { set { copy(message = null) } }
}
