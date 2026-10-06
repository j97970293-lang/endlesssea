package dev.endlesssea.app.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * §diagnostic : enregistre la pile d'appel de TOUT plantage non rattrapé.
 *
 * Android tue l'application sans rien montrer ; sans journal, impossible de
 * savoir pourquoi « ça se ferme tout seul ». On écrit donc la trace dans
 * `filesDir/last_crash.txt`, lisible ensuite dans Réglages → Diagnostic.
 */
object CrashReporter {

    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())
        val version = runCatching {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            "${p.versionName} (${p.versionCode})"
        }.getOrDefault("?")
        val text = buildString {
            appendLine("EndlessSea $version — $stamp")
            appendLine("Appareil : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} — Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Thread : ${thread.name}")
            appendLine()
            appendLine(android.util.Log.getStackTraceString(error))
        }
        File(context.filesDir, FILE).writeText(text)
    }

    /** Dernier plantage enregistré (null si aucun). */
    fun lastCrash(context: Context): String? =
        File(context.applicationContext.filesDir, FILE)
            .takeIf { it.exists() && it.length() > 0 }
            ?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE).delete() }
    }
}
