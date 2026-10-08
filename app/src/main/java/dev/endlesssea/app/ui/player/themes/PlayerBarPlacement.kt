package dev.endlesssea.app.ui.player.themes

internal data class PlayerBarPlacement(val progressOnTop: Boolean, val toolsOnTop: Boolean) {
    fun showsProgress(top: Boolean) = progressOnTop == top
    fun showsTools(top: Boolean) = toolsOnTop == top
}
