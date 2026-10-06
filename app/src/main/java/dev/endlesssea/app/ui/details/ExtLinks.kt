package dev.endlesssea.app.ui.details

import dev.endlesssea.extensions.api.EsExtension
import dev.endlesssea.extensions.api.model.LinkRequest
import dev.endlesssea.extensions.api.model.VideoLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * §compat-extensions : appel tolérant de `loadLinksFlow`.
 *
 * Les extensions compilées AVANT l'ajout de `loadLinksFlow` n'embarquent pas la
 * classe `DefaultImpls` correspondante : l'appel direct lève alors
 * `AbstractMethodError` (« abstract method loadLinksFlow ») et la lecture
 * échoue. On retombe automatiquement sur l'ancien `loadLinks`.
 */
fun EsExtension.linksFlowCompat(request: LinkRequest): Flow<VideoLink> = flow {
    val ext = this@linksFlowCompat
    val streamed = try {
        ext.loadLinksFlow(request)
    } catch (e: Throwable) {
        if (e is AbstractMethodError || e is NoSuchMethodError || e is LinkageError) null else throw e
    }
    if (streamed != null) {
        try {
            streamed.collect { emit(it) }
            return@flow
        } catch (e: Throwable) {
            if (!(e is AbstractMethodError || e is NoSuchMethodError || e is LinkageError)) throw e
        }
    }
    ext.loadLinks(request).forEach { emit(it) }
}
