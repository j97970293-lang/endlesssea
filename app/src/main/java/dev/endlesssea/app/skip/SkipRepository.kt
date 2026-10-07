package dev.endlesssea.app.skip

import dev.endlesssea.data.db.SkipButtonEntity
import dev.endlesssea.data.db.SkipCacheEntity
import dev.endlesssea.data.db.SkipDao
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §megaskip — dépôt **hors-ligne d'abord** :
 *
 *  1. lecture du cache Room (fonctionne sans réseau, sans compte, sans télémétrie) ;
 *  2. sinon interrogation **parallèle** des fournisseurs activés ;
 *  3. fusion par type (priorité TheIntroDB → IntroDB → AniSkip) puis mise en cache.
 *
 * Les boutons personnalisés vivent dans une table dédiée et restent toujours
 * disponibles, même totalement hors-ligne.
 */
@Singleton
class SkipRepository @Inject constructor(
    private val dao: SkipDao,
    private val providers: SkipProviders,
) {

    companion object {
        /** Durée de fraîcheur du cache : 30 jours (les timestamps bougent peu). */
        private const val TTL_MS = 30L * 24L * 60L * 60L * 1000L
    }

    fun observeButtons(): Flow<List<CustomSkipButton>> =
        dao.observeButtons().map { list -> list.map { it.toDomain() } }

    suspend fun buttons(): List<CustomSkipButton> =
        dao.enabledButtons().map { it.toDomain() }

    /**
     * Segments du média courant. [forceRefresh] ignore le cache (geste manuel
     * « Rafraîchir » depuis le lecteur).
     */
    suspend fun load(
        target: SkipTarget,
        settings: SkipSettings,
        forceRefresh: Boolean = false,
    ): List<SkipSegment> {
        if (target.isAnonymous) return emptyList()
        val now = System.currentTimeMillis()

        val cached = dao.cachedFor(target.mediaKey, target.season, target.episode)
            .filter { now - it.fetchedAt < TTL_MS }
        if (!forceRefresh && cached.isNotEmpty()) return merge(cached)

        val fresh = coroutineScope {
            val jobs = buildList {
                if (settings.providerTheIntroDb) {
                    add("theintrodb" to async { providers.theIntroDb(target) })
                }
                if (settings.providerIntroDb) {
                    add("introdb" to async { providers.introDb(target) })
                }
                if (settings.providerAniSkip) {
                    add("aniskip" to async { providers.aniSkip(target) })
                }
            }
            jobs.map { (provider, job) ->
                val segments = runCatching { job.await() }.getOrDefault(emptyList())
                if (segments.isNotEmpty()) {
                    dao.put(
                        SkipCacheEntity(
                            cacheKey = target.cacheKey(provider),
                            provider = provider,
                            mediaKey = target.mediaKey,
                            season = target.season,
                            episode = target.episode,
                            json = encode(segments),
                            fetchedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                provider to segments
            }
        }

        // Relecture du cache : on y ajoute ce qui vient d'être trouvé + l'ancien.
        val merged = dao.cachedFor(target.mediaKey, target.season, target.episode)
        if (merged.isEmpty()) return merge(fresh.map { (p, s) -> SkipCacheEntity(p, p, target.mediaKey, target.season, target.episode, encode(s)) })
        return merge(merged)
    }

    /** Fusion : un seul segment par type, le fournisseur le plus prioritaire gagne. */
    private fun merge(entries: List<SkipCacheEntity>): List<SkipSegment> {
        val priority = listOf("theintrodb", "introdb", "aniskip", "local")
        val decoded = entries
            .sortedBy { priority.indexOf(it.provider).let { i -> if (i < 0) priority.size else i } }
            .flatMap { decode(it.json, it.provider) }
        return decoded
            .groupBy { it.type }
            .map { (_, list) -> list.minByOrNull { it.durationMs } ?: list.first() }
            .sortedBy { it.startMs }
    }

    // ------------------------------------------------------------------ boutons

    suspend fun addButton(label: String, seconds: Int) {
        val clean = label.trim().ifBlank { "Saut personnalisé" }
        val next = (dao.enabledButtons().maxOfOrNull { it.position } ?: 0) + 1
        dao.upsertButton(
            SkipButtonEntity(
                label = clean.take(24),
                seconds = seconds.coerceIn(1, 3600),
                position = next,
            ),
        )
    }

    suspend fun updateButton(button: CustomSkipButton) = dao.upsertButton(
        SkipButtonEntity(
            id = button.id,
            label = button.label.trim().ifBlank { "Saut" }.take(24),
            seconds = button.seconds.coerceIn(1, 3600),
            position = button.position,
            enabled = button.enabled,
        ),
    )

    suspend fun deleteButton(id: Long) = dao.deleteButton(id)

    suspend fun clearCache() = dao.clear()

    // ------------------------------------------------------------------ sérialisation

    private fun encode(segments: List<SkipSegment>): String {
        val array = JSONArray()
        segments.forEach { segment ->
            array.put(
                JSONObject()
                    .put("type", segment.type.key)
                    .put("start", segment.startMs)
                    .put("end", segment.endMs),
            )
        }
        return array.toString()
    }

    private fun decode(json: String, provider: String): List<SkipSegment> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val type = when (o.optString("type")) {
                "op", "intro" -> SkipType.INTRO
                "recap" -> SkipType.RECAP
                "ed", "credits" -> SkipType.CREDITS
                "preview" -> SkipType.PREVIEW
                else -> SkipType.CUSTOM
            }
            SkipSegment(type, o.optLong("start"), o.optLong("end"), provider)
        }
    }.getOrDefault(emptyList())

    private fun SkipButtonEntity.toDomain() = CustomSkipButton(
        id = id, label = label, seconds = seconds, position = position, enabled = enabled,
    )
}
