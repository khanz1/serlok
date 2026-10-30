package com.khanz1.serlok.debug

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings

object DeveloperOptions {
    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
            0,
        ) == 1

    fun observe(context: Context, onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChange()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED),
            false,
            observer,
        )
        return observer
    }

    fun unregister(context: Context, observer: ContentObserver) {
        context.contentResolver.unregisterContentObserver(observer)
    }
}
