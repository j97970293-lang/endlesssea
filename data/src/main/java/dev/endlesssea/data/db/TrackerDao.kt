package dev.endlesssea.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** §suivi (conversation 11) — comptes et rattachements des services de suivi. */
@Dao
interface TrackerDao {

    // ---------------------------------------------------------------- comptes

    @Query("SELECT * FROM tracker_accounts ORDER BY service ASC")
    fun observeAccounts(): Flow<List<TrackerAccountEntity>>

    @Query("SELECT * FROM tracker_accounts WHERE service = :service")
    suspend fun account(service: String): TrackerAccountEntity?

    @Upsert suspend fun upsertAccount(account: TrackerAccountEntity)

    @Query("UPDATE tracker_accounts SET enabled = :enabled WHERE service = :service")
    suspend fun setEnabled(service: String, enabled: Boolean)

    @Query("UPDATE tracker_accounts SET lastError = :error WHERE service = :service")
    suspend fun setLastError(service: String, error: String?)

    @Query("UPDATE tracker_accounts SET accessToken = :token, refreshToken = :refresh, expiresAt = :expiresAt WHERE service = :service")
    suspend fun updateTokens(service: String, token: String, refresh: String?, expiresAt: Long)

    @Query("DELETE FROM tracker_accounts WHERE service = :service")
    suspend fun deleteAccount(service: String)

    // ---------------------------------------------------------------- rattachements

    @Query("SELECT * FROM tracker_links")
    fun observeLinks(): Flow<List<TrackerLinkEntity>>

    @Query("SELECT * FROM tracker_links WHERE mediaId = :mediaId")
    suspend fun link(mediaId: String): TrackerLinkEntity?

    @Upsert suspend fun upsertLink(link: TrackerLinkEntity)

    @Query("DELETE FROM tracker_links WHERE mediaId = :mediaId")
    suspend fun deleteLink(mediaId: String)

    /** Ne pas effacer une mise à jour plus récente après une réponse réseau tardive. */
    @Query("UPDATE tracker_links SET pendingSync = 0 WHERE mediaId = :mediaId AND remoteId = :remoteId AND service = :service AND progress = :progress AND status = :status AND updatedAt = :updatedAt")
    suspend fun acknowledge(mediaId: String, remoteId: String, service: String, progress: Int, status: String, updatedAt: Long)

    /** Rattachements dont la mise à jour distante a échoué (à rejouer). */
    @Query("SELECT * FROM tracker_links WHERE pendingSync = 1")
    suspend fun pendingLinks(): List<TrackerLinkEntity>

    /** Nombre de fiches suivies, par service (affiché dans les Paramètres). */
    @Query("SELECT COUNT(*) FROM tracker_links WHERE service = :service")
    fun observeLinkCount(service: String): Flow<Int>
}
