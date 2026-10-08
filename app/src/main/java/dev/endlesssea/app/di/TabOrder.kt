package dev.endlesssea.app.di

internal fun normalizeTabOrder(saved: List<String>, available: List<String>): List<String> =
    saved.filter { it in available }.distinct() + available.filter { it !in saved }
