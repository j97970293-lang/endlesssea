package org.example.mysource

import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.extensions.api.ExtensionContext
import dev.endlesssea.extensions.api.model.API_VERSION
import dev.endlesssea.extensions.api.model.ExtensionInfo
import dev.endlesssea.extensions.api.model.FilterSet
import dev.endlesssea.extensions.api.model.LinkRequest
import dev.endlesssea.extensions.api.model.MediaDetails
import dev.endlesssea.extensions.api.model.MediaType
import dev.endlesssea.extensions.api.model.PagedResult
import dev.endlesssea.extensions.api.model.SearchItem
import dev.endlesssea.extensions.api.model.VideoLink
import dev.endlesssea.extensions.api.permission.ExtensionPermission

/** Replace the bodies. Keep the constructor: the loader passes ExtensionContext. */
class MyProvider(private val context: ExtensionContext) : EsExtension {
    override val info = ExtensionInfo(
        id = "org.example.mysource",
        name = "My Source",
        version = 1,
        apiVersion = API_VERSION,
        languages = listOf("en"),
        types = setOf(MediaType.MOVIE),
        permissions = setOf(ExtensionPermission.INTERNET),
    )

    override suspend fun search(query: String, page: Int, filters: FilterSet): PagedResult<SearchItem> =
        PagedResult(emptyList(), page, hasNextPage = false)

    override suspend fun load(url: String): MediaDetails = MediaDetails(
        id = url, url = url, title = url, type = MediaType.MOVIE,
    )

    override suspend fun loadLinks(data: LinkRequest): List<VideoLink> = emptyList()
}
