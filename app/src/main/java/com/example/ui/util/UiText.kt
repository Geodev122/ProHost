package com.example.ui.util

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * String resources from places that aren't composable (remember blocks, string builders, view models, data built for
 * the UI). Resolves through the application's resources, which follow the app language (Android recreates the
 * activity when it changes, so screens re-read). Inside a composable body prefer stringResource().
 */
object UiText {
    @Volatile
    internal var resources: Resources? = null

    /** English resources, for stable analytics keys that must not change with the app language. */
    @Volatile
    private var englishResources: Resources? = null

    fun init(resources: Resources, english: Resources? = null) {
        this.resources = resources
        this.englishResources = english
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val res = resources ?: return ""
        return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
    }

    /** A plural resource for [count] (Arabic has six forms: zero, one, two, few, many, other). */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any?): String {
        val res = resources ?: return ""
        return if (args.isEmpty()) res.getQuantityString(id, count) else res.getQuantityString(id, count, *args)
    }

    /** The English text of [id] whatever the app language (falls back to the current language before init). */
    fun english(@StringRes id: Int): String = (englishResources ?: resources)?.getString(id).orEmpty()
}

/** Shorthand for [UiText.get]. */
fun uiText(@StringRes id: Int, vararg args: Any?): String = UiText.get(id, *args)

/** Shorthand for [UiText.plural]. */
fun uiPlural(@PluralsRes id: Int, count: Int, vararg args: Any?): String = UiText.plural(id, count, *args)
