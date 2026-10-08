package dev.endlesssea.app.ui.player.themes

/** The reference theme never places megaskip inside the header or the scrolling tools dock. */
internal object ReferencePlayerPlacement {
    fun showBottomSections(top: Boolean): Boolean = !top
}
