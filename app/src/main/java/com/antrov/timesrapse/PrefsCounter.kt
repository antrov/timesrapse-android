package com.antrov.timesrapse

import android.content.Context

class PrefsCounter {
    private val sharedPrefsName = "timesrapsePrefs"
    private val sharedPrefsSuccessed = "photos.sucessed"
    private val sharedPrefsFailed = "photos.failed"

    fun resetCounter(context: Context) {
        val prefs = context.getSharedPreferences(
            sharedPrefsName,
            Context.MODE_PRIVATE
        )

        with(prefs.edit()) {
            remove(sharedPrefsSuccessed)
            remove(sharedPrefsFailed)
            apply()
        }
    }

    fun incrementCounter(context: Context, success: Boolean): Pair<Int, Int> {
        val prefs = context.getSharedPreferences(sharedPrefsName,
            Context.MODE_PRIVATE
        )
        var succeeded = prefs.getInt(sharedPrefsSuccessed, 0)
        var failed = prefs.getInt(sharedPrefsFailed, 0)

        when(success) {
            true -> succeeded++
            false -> failed++
        }

        with (prefs.edit()) {
            putInt(sharedPrefsSuccessed, succeeded)
            putInt(sharedPrefsFailed, failed)
            apply()
        }

        return Pair(succeeded, failed)
    }
}