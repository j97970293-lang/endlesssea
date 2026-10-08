package dev.endlesssea.app.branding

import androidx.annotation.DrawableRes
import dev.endlesssea.app.R

/** Stable IDs are preferences. Alias class names stay stable across updates and debug/release. */
data class AppLogo(val id: String, val label: String, @DrawableRes val drawable: Int, val aliasClass: String)

object AppLogos {
    const val DEFAULT_ID = "blue_white"
    val all = listOf(
        AppLogo("blue_white", "Bleu et blanc", R.drawable.logo_blue_white, "dev.endlesssea.app.launcher.BlueWhite"),
        AppLogo("glacier", "Glacier", R.drawable.logo_glacier, "dev.endlesssea.app.launcher.Glacier"),
        AppLogo("cyan", "Cyan", R.drawable.logo_cyan, "dev.endlesssea.app.launcher.Cyan"),
        AppLogo("ocean", "Océan", R.drawable.logo_ocean, "dev.endlesssea.app.launcher.Ocean"),
        AppLogo("silver", "Argent", R.drawable.logo_silver, "dev.endlesssea.app.launcher.Silver"),
        AppLogo("ember", "Flamme", R.drawable.logo_ember, "dev.endlesssea.app.launcher.Ember"),
        AppLogo("adventure", "Aventure", R.drawable.logo_adventure, "dev.endlesssea.app.launcher.Adventure"),
        AppLogo("classic", "Classique", R.drawable.logo_classic, "dev.endlesssea.app.launcher.Classic"),
    )
    fun resolve(id: String?): AppLogo = all.firstOrNull { it.id == id } ?: all.first()
}
