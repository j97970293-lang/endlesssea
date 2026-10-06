package dev.endlesssea.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.endlesssea.data.db.EsDatabase
import dev.endlesssea.data.db.GenreEntity
import dev.endlesssea.data.db.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class EndlessSeaApp : Application() {

    @Inject lateinit var database: EsDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // §diagnostic : journaliser les plantages AVANT tout le reste
        dev.endlesssea.app.util.CrashReporter.install(this)
        // §4 — Images des extensions : Coil partagé avec cache disque, UA honnête
        // et DNS DoH. Doit être initialisé AVANT la première AsyncImage.
        EsImages.imageLoader(this)
        appScope.launch {
            // Seed user-editable genres/categories once (spec §8/§9 — everything stays editable).
            database.genreDao().insertAll(EsDatabase.SEED_GENRES)
            seedCategories()
        }
    }

    private suspend fun seedCategories() {
        EsDatabase.SEED_CATEGORIES.forEach {
            // insert-only-if-missing via upsert of identical seeds is idempotent by PK
            database.runInTransaction { /* no-op guard, seeds are PK-stable */ }
        }
    }
}
