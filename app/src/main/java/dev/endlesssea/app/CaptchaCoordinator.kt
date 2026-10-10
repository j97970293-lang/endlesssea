package dev.endlesssea.app

import android.content.Intent
import dev.endlesssea.extensions.api.captcha.CaptchaContract
import dev.endlesssea.extensions.api.error.SourceException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Coordonne les défis anti-bot levés depuis n'importe quel ViewModel.
 *
 * Un seul WebView est ouvert à la fois. Après RESULT_SOLVED, l'appelant rejoue
 * exactement l'opération qui avait échoué ; le CookieManager Android est déjà
 * partagé avec OkHttp et Coil par HttpClients.WebViewCookieJar.
 */
object CaptchaCoordinator {
    private val gate = Mutex()

    @Volatile
    private var starter: ((Intent) -> Unit)? = null
    private var pending: CompletableDeferred<Boolean>? = null

    fun attach(start: (Intent) -> Unit) {
        starter = start
    }

    fun detach() {
        starter = null
        pending?.complete(false)
        pending = null
    }

    fun onResult(solved: Boolean) {
        pending?.complete(solved)
    }

    suspend fun solve(challenge: SourceException.CaptchaRequired): Boolean = gate.withLock {
        val launch = starter ?: return@withLock false
        val result = CompletableDeferred<Boolean>()
        pending = result
        withContext(Dispatchers.Main.immediate) {
            launch(
                Intent().apply {
                    setClassName(
                        "dev.endlesssea.app",
                        "dev.endlesssea.extensions.loader.captcha.CaptchaActivity",
                    )
                    putExtra(CaptchaContract.EXTRA_PAGE_URL, challenge.pageUrl)
                    putExtra(CaptchaContract.EXTRA_HEADERS_JSON, JSONObject(challenge.headers).toString())
                }
            )
        }
        val solved = withTimeoutOrNull(5 * 60_000L) { result.await() } == true
        if (pending === result) pending = null
        solved
    }
}

/** Exécute une opération d'extension et la rejoue après au plus deux vérifications humaines. */
suspend fun <T> withCaptchaRetry(block: suspend () -> T): T {
    var attempts = 0
    while (true) {
        try {
            return block()
        } catch (challenge: SourceException.CaptchaRequired) {
            attempts++
            if (attempts > 2 || !CaptchaCoordinator.solve(challenge)) throw challenge
        }
    }
}
