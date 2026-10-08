package dev.endlesssea.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.endlesssea.app.tracking.TrackerRepository
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.data.db.WatchHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val trackerRepository: TrackerRepository,
    private val historyDao: WatchHistoryDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsState(loading = true))
    val uiState: StateFlow<StatisticsState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    fun refresh() {
        loadStatistics()
    }

    private fun loadStatistics() = viewModelScope.launch {
        _uiState.value = _uiState.value.copy(loading = true, error = null)

        try {
            // §stats-tracker : récupérer les statistiques depuis les trackers
            val links = trackerRepository.links
            val accounts = trackerRepository.accounts

            // Calculer les stats par service
            val statsByService = mutableMapOf<String, ServiceStats>()
            links.collect { linkList ->
                linkList.groupBy { it.service }.forEach { (service, serviceLinks) ->
                    val totalEpisodes = serviceLinks.sumOf { it.progress }
                    val totalSeries = serviceLinks.size
                    statsByService[service] = ServiceStats(
                        episodesWatched = totalEpisodes,
                        seriesTracked = totalSeries,
                    )
                }
            }.let { /* Attendre la première émission */ }

            // §stats-history : récupérer l'historique de visionnage
            val history = historyDao.observeAll()
            var totalEpisodesWatched = 0
            var totalWatchTimeMs: Long = 0
            val recentActivity = mutableListOf<RecentActivity>()

            history.collect { historyList ->
                totalEpisodesWatched = historyList.size
                totalWatchTimeMs = historyList.sumOf { it.positionMs.coerceAtLeast(0) }
                recentActivity.clear()
                recentActivity.addAll(
                    historyList.sortedByDescending { it.updatedAt }
                        .take(10)
                        .map { entity ->
                            RecentActivity(
                                title = entity.title,
                                episodeNumber = extractEpisodeNumber(entity.title),
                                timestamp = entity.updatedAt,
                            )
                        }
                )
            }.let { /* Attendre la première émission */ }

            // Combiner les résultats
            val totalSeriesTracked = statsByService.values.sumOf { it.seriesTracked }

            _uiState.value = _uiState.value.copy(
                loading = false,
                totalEpisodesWatched = totalEpisodesWatched,
                totalSeriesTracked = totalSeriesTracked,
                totalWatchTimeMs = totalWatchTimeMs,
                statsByService = statsByService,
                recentActivity = recentActivity,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                loading = false,
                error = "Erreur lors du chargement : ${e.message}",
            )
        }
    }

    /** §stats-utils : extraire le numéro d'épisode du titre si possible */
    private fun extractEpisodeNumber(title: String): Int {
        val match = Regex("([Ee]pisode\\s*)?(\\d+)").find(title)
        return match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
    }
}
