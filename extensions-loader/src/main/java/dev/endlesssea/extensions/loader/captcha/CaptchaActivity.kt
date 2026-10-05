package dev.endlesssea.extensions.loader.captcha

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.net.http.SslError
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import dev.endlesssea.extensions.api.captcha.CaptchaContract

/**
 * Écran de vérification interactive (spec §3, docs/en/05 §4).
 *
 * L'extension a levé SourceException.CaptchaRequired → on ouvre la page dans une
 * WebView système, l'UTILISATEUR passe la vérification à la main, puis on persiste les
 * cookies et on termine avec RESULT_SOLVED ; l'appel initial est re-joué par l'appelant.
 *
 * Rien n'est résolu automatiquement : les contrôles interactifs sont réservés aux
 * humains (docs/en/05 §4). Durcissements §3 :
 *  - barre de progression du chargement ;
 *  - page d'erreur FR avec bouton « Réessayer » (au lieu d'un écran vide) ;
 *  - erreurs SSL : jamais ignorées d'office — dialogue explicite Annuler (défaut) / Continuer.
 */
class CaptchaActivity : Activity() {

    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var errorPanel: View? = null
    private var errorText: TextView? = null
    private var pageUrl: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageUrl = intent.getStringExtra(CaptchaContract.EXTRA_PAGE_URL).orEmpty()

        CookieManager.getInstance().setAcceptCookie(true)

        // ---- Layout natif : barre de progression + WebView + panneau d'erreur
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = true
        }
        progressBar = progress
        val web = WebView(this)
        webView = web
        val errText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        errorText = errText
        val retryBtn = Button(this).apply {
            text = "Réessayer"
            setOnClickListener {
                errorPanel?.visibility = View.GONE
                web.visibility = View.VISIBLE
                web.loadUrl(pageUrl)
            }
        }
        // Bouton flottant « J'ai terminé » : si la vérification est passée mais la
        // détection automatique n'a pas suivi, l'utilisateur conclut lui-même.
        val doneBtn = Button(this).apply {
            text = "✔ Terminé — retour à l'app"
            textSize = 14f
            setOnClickListener {
                CookieManager.getInstance().flush()
                setResult(CaptchaContract.RESULT_SOLVED)
                finish()
            }
        }
        val errPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            visibility = View.GONE
            addView(errText)
            addView(retryBtn)
        }
        errorPanel = errPanel
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(progress)
            addView(web, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(errPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(doneBtn)
        }

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            // Même UA que les requêtes extension : le défi et le provider voient une seule identité.
            userAgentString = dev.endlesssea.core.net.HttpClients.USER_AGENT
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                val pb = progressBar ?: return
                if (newProgress in 1..99) {
                    pb.isIndeterminate = false
                    pb.progress = newProgress
                    pb.visibility = View.VISIBLE
                } else {
                    pb.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }
            }
        }
        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    "(document.title + ' ' + document.body.innerText.slice(0,2000))"
                ) { text ->
                    if (text != null && !containsChallengeMarkers(text.lowercase())) {
                        // Marqueurs de défi absents → les cookies sont exploitables.
                        CookieManager.getInstance().flush()
                        setResult(CaptchaContract.RESULT_SOLVED)
                        finish()
                    }
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                // Ne masquer la page que si le document principal a échoué.
                if (request?.isForMainFrame == true) {
                    dev.endlesssea.core.diag.EsLog.e(
                        "WebView", "Captcha",
                        "Chargement échoué : ${error?.description ?: "?"}",
                        details = "url=${request.url}",
                    )
                    showError(
                        "La page n'a pas pu être chargée.\n\n${error?.description ?: "Erreur réseau inconnue"}\n\n" +
                            "Vérifie ta connexion puis réessaie — ou annule et choisis une autre source."
                    )
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler?, error: SslError?) {
                // §3 : une erreur SSL n'est JAMAIS acceptée silencieusement.
                handler ?: return
                dev.endlesssea.core.diag.EsLog.e(
                    "WebView", "Captcha", "Erreur SSL : ${error?.primaryError ?: "?"}",
                    details = "url=${error?.url}",
                )
                AlertDialog.Builder(this@CaptchaActivity)
                    .setTitle("Certificat non fiable")
                    .setMessage(
                        "La connexion vers cette source ne peut pas être vérifiée " +
                            "(${error?.primaryError?.let { sslErrorName(it) } ?: "erreur SSL"}).\n\n" +
                            "Continuer uniquement si tu comprends le risque."
                    )
                    .setNegativeButton("Annuler") { _, _ ->
                        handler.cancel() // défaut prudent
                        showError("Connexion interrompue : le certificat du site n'est pas fiable.")
                        errorPanel?.findViewWithTag<Button?>("retry")?.visibility = View.GONE
                    }
                    .setPositiveButton("Continuer") { _, _ -> handler.proceed() }
                    .setCancelable(false)
                    .show()
            }

            private fun containsChallengeMarkers(text: String): Boolean =
                CHALLENGE_MARKERS.any { it in text }
        }

        setContentView(root)
        web.loadUrl(pageUrl)
    }

    private fun showError(message: String) {
        errorText?.text = message
        webView?.visibility = View.GONE
        progressBar?.visibility = View.GONE
        errorPanel?.visibility = View.VISIBLE
    }

    override fun onBackPressed() {
        webView?.takeIf { it.visibility == View.VISIBLE && it.canGoBack() }?.goBack() ?: run {
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
        /** Marqueurs courants des pages anti-bot (détection best-effort). */
        val CHALLENGE_MARKERS = listOf(
            "verify you are human", "vérification anti-robot", "are you a robot",
            "cf-challenge", "just a moment", "captcha", "vérification de sécurité",
        )

        private fun sslErrorName(code: Int) = when (code) {
            android.net.http.SslError.SSL_EXPIRED -> "certificat expiré"
            android.net.http.SslError.SSL_IDMISMATCH -> "nom de domaine incorrect"
            android.net.http.SslError.SSL_UNTRUSTED -> "autorité inconnue"
            android.net.http.SslError.SSL_NOTYETVALID -> "certificat pas encore valide"
            else -> "code $code"
        }
    }
}
