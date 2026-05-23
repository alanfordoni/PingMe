package com.spbalkan.pingme

import android.os.Build
import android.util.Log

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun customLoggerD(tag: String, message: String) {
    Log.d(tag, message)
}