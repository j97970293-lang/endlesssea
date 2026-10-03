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
    fun provideOkHttp(): OkHttpClient = HttpClients.baseBuilder().build()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EsDatabase =
        Room.databaseBuilder(context, EsDatabase::class.java, EsDatabase.NAME)
            .fallbackToDestructiveMigration() // v1 scaffold; real migrations ship with v2+
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
    fun provideDownloadEngine(dao: DownloadsDao, http: OkHttpClient, @ApplicationContext context: Context): DownloadEngine =
        DownloadManager(dao = dao, http = http, tempDirProvider = { context.filesDir })

    @Provides @Singleton
    fun provideExtensionLoader(@ApplicationContext context: Context, http: OkHttpClient): ExtensionLoader =
        ExtensionLoader(context, http)

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
