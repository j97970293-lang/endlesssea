package dev.endlesssea.extensions.loader.captcha

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.endlesssea.extensions.api.captcha.CaptchaContract

/**
 * Interactive verification screen (spec §3, docs/en/05 §4).
 *
 * The extension threw SourceException.CaptchaRequired → we open the page in a system
 * WebView, the USER performs the verification manually, and when the page stops showing
 * challenge markers we persist the cookies and finish(RESULT_SOLVED). The original
 * call is then retried by the caller with the extension's cookie jar attached.
 *
 * Nothing is auto-solved: interactive checks are a human-only step (docs/en/05 §4).
 */
class CaptchaActivity : Activity() {

    private var webView: WebView? = null
    private var pageUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageUrl = intent.getStringExtra(CaptchaContract.EXTRA_PAGE_URL).orEmpty()

        CookieManager.getInstance().setAcceptCookie(true)
        val web = WebView(this)
        webView = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Share the extension-facing UA so the challenge and the provider see one identity.
            userAgentString = dev.endlesssea.core.net.HttpClients.USER_AGENT
        }
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "(document.title + ' ' + document.body.innerText.slice(0,2000))"
                ) { text ->
                    if (text != null && !containsChallengeMarkers(text.lowercase())) {
                        // Challenge markers gone → cookies are usable; persist & return.
                        CookieManager.getInstance().flush()
                        setResult(CaptchaContract.RESULT_SOLVED)
                        finish()
                    }
                }
            }

            private fun containsChallengeMarkers(text: String): Boolean =
                CHALLENGE_MARKERS.any { it in text }
        }
        setContentView(web)
        web.loadUrl(pageUrl)
    }

    override fun onBackPressed() {
        webView?.takeIf { it.canGoBack() }?.goBack() ?: run {
            setResult(CaptchaContract.RESULT_CANCELLED)
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    companion object {
        /** Common markers of interactive anti-bot pages (best-effort detection). */
        val CHALLENGE_MARKERS = listOf(
            "verify you are human", "vérification anti-robot", "are you a robot",
            "cf-challenge", "just a moment", "captcha", "vérification de sécurité",
        )
    }
}
