package dev.endlesssea.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.core.util.FileNames
import dev.endlesssea.extensions.api.error.SourceException
import dev.endlesssea.extensions.api.model.FilterSet
import dev.endlesssea.extensions.loader.ExtensionRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<SearchItemUi> = emptyList(),
    val perExtensionErrors: Map<String, String> = emptyMap(),
    /** Types sélectionnés (« ANIME », « MOVIE », « SERIES ») — transmis aux sources. */
    val selTypes: Set<String> = emptySet(),
    /** Langues sélectionnées (« vf », « vostfr », « vo »). */
    val selLangs: Set<String> = emptySet(),
    /** §recherche-source : "ALL" ou l'id de l'extension interrogée seule. */
    val sourceFilter: String = "ALL",
)

/**
 * Agrège les recherches de toutes les extensions activées (spec §12) avec débounce,
 * déduplication (titre normalisé + année + type) et erreurs isolées par extension.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val registry: ExtensionRegistry,
    private val prefs: dev.endlesssea.app.di.AppPrefs,
) : ViewModel() {

    /** §recherche-sources : liste des sources activables (id, nom) et exclusions. */
    val searchExcluded: StateFlow<Set<String>> = prefs.searchExcluded
    val resultsOnly: StateFlow<Boolean> = prefs.searchResultsOnly
    fun availableSources(): List<Pair<String, String>> =
        registry.enabledExtensions().map { (_, ext) -> ext.info.id to ext.info.name }
    fun toggleSearchSource(id: String) {
        prefs.toggleSearchExcluded(id)
        if (_uiState.value.query.isNotBlank()) {
            _uiState.value = _uiState.value.copy(loading = true)
            queryFlow.value = _uiState.value.query + " "
            queryFlow.value = _uiState.value.query
        }
    }
    fun setResultsOnly(v: Boolean) = prefs.setSearchResultsOnly(v)

    private val queryFlow = MutableStateFlow("")
    /** Filtres actifs (type + langue) — transmis aux extensions via FilterSet. */
    private val filtersFlow = MutableStateFlow(dev.endlesssea.extensions.api.model.FilterSet())
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(queryFlow, filtersFlow) { q, f -> q to f }
                .debounce(350)
                .distinctUntilChanged()
                .filter { it.first.isNotBlank() }
                .flatMapLatest { (query, filters) ->
                    kotlinx.coroutines.flow.flow { emit(runSearch(query, filters)) }
                }
                .collect { (results, errors) ->
                    _uiState.value = _uiState.value.copy(loading = false, results = results, perExtensionErrors = errors)
                }
        }
    }

    /**
     * §recherche-source : limite la recherche à UNE extension (id de l'extension),
     * ou "ALL" pour interroger toutes les sources activées.
     */
    fun setSourceFilter(id: String) {
        sourceFilter = id.ifBlank { "ALL" }
        _uiState.value = _uiState.value.copy(sourceFilter = sourceFilter)
        if (_uiState.value.query.isNotBlank()) {
            _uiState.value = _uiState.value.copy(loading = true)
            queryFlow.value = _uiState.value.query + " "   // force un nouveau cycle
            queryFlow.value = _uiState.value.query
        }
    }

    @Volatile
    private var sourceFilter: String = "ALL"

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query, loading = query.isNotBlank())
        queryFlow.value = query
    }

    /** Active/désactive un type de média (ANIME/MOVIE/SERIES) et relance la recherche. */
    fun toggleType(type: String) {
        val cur = _uiState.value.selTypes.toMutableSet().apply { if (!add(type)) remove(type) }
        _uiState.value = _uiState.value.copy(selTypes = cur)
        rebuildFilters()
    }

    /** Active/désactive une langue (« vf » / « vostfr » / « vo ») et relance la recherche. */
    fun toggleLang(lang: String) {
        val cur = _uiState.value.selLangs.toMutableSet().apply { if (!add(lang)) remove(lang) }
        _uiState.value = _uiState.value.copy(selLangs = cur)
        rebuildFilters()
    }

    private fun rebuildFilters() {
        val st = _uiState.value
        filtersFlow.value = FilterSet(
            types = st.selTypes.mapNotNull {
                runCatching { dev.endlesssea.extensions.api.model.MediaType.valueOf(it) }.getOrNull()
            }.toSet(),
            languages = st.selLangs,
        )
        if (st.query.isNotBlank()) _uiState.value = st.copy(loading = true)
    }

    /** Requête fan-out sur les extensions actives, en parallèle, erreurs isolées. */
    private suspend fun runSearch(
        query: String,
        filters: FilterSet = filtersFlow.value,
    ): Pair<List<SearchItemUi>, Map<String, String>> = coroutineScope {
        val results = mutableListOf<Triple<dev.endlesssea.extensions.api.model.SearchItem, String, String>>()
        val errors = mutableMapOf<String, String>()

        registry.enabledExtensions()
            // §recherche-sources : on saute les sources désactivées dans l'écran
            .filter { (_, ext) -> ext.info.id !in prefs.searchExcluded.value }
            .filter { (_, ext) -> sourceFilter == "ALL" || ext.info.id == sourceFilter }
            .map { (name, ext) ->
            async {
                runCatching { ext.search(query, page = 1, filters = filters) }
                    .onSuccess { page ->
                        // id composite « <pkg id>:<url> » — l'écran Détails rappelle cette extension
                        val remapPrefix = ext.info.id
                        // §recherche-groupée : on NAME la source et on déduplique PAR extension
                        // (un même titre peut exister sur plusieurs sources — c'est voulu).
                        val remapped = page.items
                            .map { it.copy(id = "$remapPrefix:${it.url}") }
                            .distinctBy { "${FileNames.normalizedKey(it.title)}#${it.year}#${it.type}" }
                            .map { Triple(it, remapPrefix, ext.info.name) }
                        synchronized(results) { results += remapped }
                    }
                    .onFailure { e ->
                        val msg = if (e is SourceException) e.toUserMessage() else "Erreur inconnue"
                        synchronized(errors) { errors[name] = msg }
                    }
            }
        }.forEach { it.await() }
        // items triés par arrivée ; la dédup est DÉJÀ faite par extension.
        results.toUi() to errors
    }

    private fun List<Triple<dev.endlesssea.extensions.api.model.SearchItem, String, String>>.toUi(): List<SearchItemUi> =
        map { (it, pkg, srcName) ->
            SearchItemUi(
                it.id, it.title, it.posterUrl, subtitle = it.type.name,
                rating = it.rating, audioLangs = it.audioLangs.map { l -> l.name },
                sourcePkg = pkg, sourceName = srcName,
            )
        }

    private fun SourceException.toUserMessage(): String = when (this) {
        is SourceException.SourceUnavailable -> "Source indisponible"
        SourceException.NoResults -> "Aucun résultat"
        is SourceException.CaptchaRequired -> "Vérification requise"
        is SourceException.NetworkError -> "Connexion impossible"
        is SourceException.RateLimited -> "Trop de requêtes"
        else -> message ?: "Erreur inconnue"
    }
}
