package dev.endlesssea.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.endlesssea.core.net.HttpClients
import dev.endlesssea.data.db.EsDatabase
import dev.endlesssea.data.db.DownloadsDao
import dev.endlesssea.data.db.ExtensionDao
import dev.endlesssea.data.db.GenreDao
import dev.endlesssea.data.db.LibraryDao
import dev.endlesssea.data.db.MediaDao
import dev.endlesssea.data.db.RepoDao
import dev.endlesssea.data.db.WatchHistoryDao
import dev.endlesssea.downloader.DownloadEngine
import dev.endlesssea.downloader.DownloadManager
import dev.endlesssea.extensions.loader.ExtensionLoader
import dev.endlesssea.extensions.loader.RepoManager
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideOkHttp(prefs: AppPrefs): OkHttpClient =
        HttpClients.baseBuilder().build() // EsNet.dnsMode déjà synchronisé par AppPrefs

    /** v1 → v2 : colonne watchlist `status` (aucune donnée perdue, migration additive). */
    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE library ADD COLUMN status TEXT NOT NULL DEFAULT 'NONE'")
        }
    }

    /** v2 → v3 : icône d'extension persistée (§4). */
    private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE extensions ADD COLUMN iconUrl TEXT")
        }
    }

    /** v3 → v4 : métadonnées éditées sur les sources (§métadonnées-éditées). */
    private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE media ADD COLUMN customTitle TEXT")
            db.execSQL("ALTER TABLE media ADD COLUMN customCoverUri TEXT")
        }
    }

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EsDatabase =
        Room.databaseBuilder(context, EsDatabase::class.java, EsDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration() // garde-fou uniquement (jamais emprunté pour 1→2)
            .build()

    @Provides fun provideMediaDao(db: EsDatabase): MediaDao = db.mediaDao()
    @Provides fun provideEpisodeDao(db: EsDatabase): dev.endlesssea.data.db.EpisodeDao = db.episodeDao()
    @Provides fun provideLibraryDao(db: EsDatabase): LibraryDao = db.libraryDao()
    @Provides fun provideHistoryDao(db: EsDatabase): WatchHistoryDao = db.watchHistoryDao()
    @Provides fun provideDownloadsDao(db: EsDatabase): DownloadsDao = db.downloadsDao()
    @Provides fun provideGenreDao(db: EsDatabase): GenreDao = db.genreDao()
    @Provides fun provideRepoDao(db: EsDatabase): RepoDao = db.repoDao()
    @Provides fun provideExtensionDao(db: EsDatabase): ExtensionDao = db.extensionDao()

    @Provides @Singleton
    fun provideDownloadEngine(
        dao: DownloadsDao,
        http: OkHttpClient,
        @ApplicationContext context: Context,
        prefs: dev.endlesssea.app.di.AppPrefs,
    ): DownloadEngine =
        DownloadManager(
            dao = dao, http = http, tempDirProvider = { context.filesDir },
            // §stockage-public : arborescence Aniyomi dans le dossier choisi
            // (downloads/<Source>/<Série>/<Épisode>/fichier) — jamais en privé.
            publisher = { task, file ->
                val dirs = task.displayPath.substringBeforeLast('/', "")
                    .split('/').filter { it.isNotBlank() }
                    .ifEmpty { listOf(dev.endlesssea.app.local.DownloadStorage.DOWNLOADS_DIR) }
                dev.endlesssea.app.local.DownloadStorage.publish(
                    context = context,
                    rootUri = prefs.storageRoot.value,
                    relativeDirs = dirs,
                    fileName = task.fileName.removeSuffix(".part"),
                    source = file,
                )
            },
        )

    @Provides @Singleton
    fun provideExtensionLoader(
        @ApplicationContext context: Context,
        http: OkHttpClient,
        store: dev.endlesssea.app.data.ExtensionSettingsStore,
    ): ExtensionLoader = ExtensionLoader(context, http, settingsProvider = store.provider)

    @Provides @Singleton
    fun provideRepoManager(dao: RepoDao, http: OkHttpClient): RepoManager = RepoManager(dao, http)

    @Provides @Singleton
    fun provideExtensionRegistry(
        @ApplicationContext context: Context,
        loader: ExtensionLoader,
        extensionDao: ExtensionDao,
    ): dev.endlesssea.extensions.loader.ExtensionRegistry =
        dev.endlesssea.extensions.loader.ExtensionRegistry(context, loader, extensionDao)
}
