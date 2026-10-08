package dev.endlesssea.app.ui.library

/** Stable navigation identifiers, independent of tab order and Compose lifetime. */
enum class LibraryArea(val label: String) {
    COLLECTION("Collection"), DOWNLOADS("Téléchargements"), FOLDERS("Dossiers")
}

@ConsistentCopyVisibility
data class LibraryNavigation private constructor(
    val destination: String,
    val collection: String,
    val deviceOnly: Boolean,
) {
    val area: LibraryArea get() = when {
        destination == "DOWNLOADS" -> LibraryArea.DOWNLOADS
        destination == "LOCAL" || destination.startsWith("CUSTOM:") -> LibraryArea.FOLDERS
        else -> LibraryArea.COLLECTION
    }
    val onDeviceOnly: Boolean get() = area == LibraryArea.COLLECTION && deviceOnly

    fun select(key: String): LibraryNavigation {
        val next = validDestination(key)
        return copy(destination = next, collection = if (next in collections) next else collection)
    }
    fun open(area: LibraryArea): LibraryNavigation = select(when (area) {
        LibraryArea.COLLECTION -> collection
        LibraryArea.DOWNLOADS -> "DOWNLOADS"
        LibraryArea.FOLDERS -> "LOCAL"
    })
    fun filterDeviceOnly(enabled: Boolean): LibraryNavigation = copy(deviceOnly = enabled)

    companion object {
        val collections = setOf("FAV", "ANIME", "MOVIE", "SERIES", "OVA", "ONA")
        private fun validDestination(value: String?): String = value?.takeIf {
            it in collections || it == "DOWNLOADS" || it == "LOCAL" ||
                (it.startsWith("CUSTOM:") && it.removePrefix("CUSTOM:").isNotBlank())
        } ?: "FAV"

        fun restore(destination: String? = null, collection: String? = null, deviceOnly: Boolean = false): LibraryNavigation {
            val dest = validDestination(destination)
            return LibraryNavigation(dest,
                if (dest in collections) dest else collection?.takeIf { it in collections } ?: "FAV",
                deviceOnly)
        }
    }
}
