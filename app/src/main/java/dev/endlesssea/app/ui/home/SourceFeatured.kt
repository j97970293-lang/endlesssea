package dev.endlesssea.app.ui.home

import dev.endlesssea.app.ui.search.SearchItemUi

/** A selected source NEVER inherits another source's cached carousel. */
internal fun sourceFeatured(rows: List<HomeRowUi>, source: String, recent: List<SearchItemUi>): List<SearchItemUi> {
    val items = rows.filter { source == "ALL" || it.sourcePkg == source }.flatMap { it.items }.distinctBy { it.id }
    return (if (items.isEmpty() && source == "ALL") recent else items).take(6)
}
