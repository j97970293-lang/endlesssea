package dev.endlesssea.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MediaEntity::class,
        EpisodeEntity::class,
        LibraryEntity::class,
        WatchHistoryEntity::class,
        DownloadTaskEntity::class,
        DownloadSegmentEntity::class,
        GenreEntity::class,
        CategoryEntity::class,
        RepoEntity::class,
        ExtensionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class EsDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun libraryDao(): LibraryDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadsDao(): DownloadsDao
    abstract fun genreDao(): GenreDao
    abstract fun repoDao(): RepoDao
    abstract fun extensionDao(): ExtensionDao

    companion object {
        const val NAME = "endless-sea.db"

        /** Starter set (spec §9) — all editable by the user. */
        val SEED_GENRES = listOf(
            "Action", "Romance", "Comédie", "Fantasy", "Isekai",
            "Thriller", "Horreur", "Sport", "Science-fiction", "Drame",
        ).mapIndexed { i, name -> GenreEntity(name = name, position = i, builtin = true) }

        val SEED_CATEGORIES = listOf(
            CategoryEntity("ANIME", "Anime", 0),
            CategoryEntity("FILMS", "Films", 1),
            CategoryEntity("SERIES", "Séries", 2),
            CategoryEntity("OVA", "OVA", 3),
            CategoryEntity("ONA", "ONA", 4),
        )
    }
}
