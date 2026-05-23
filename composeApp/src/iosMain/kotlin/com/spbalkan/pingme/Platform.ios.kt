package com.spbalkan.pingme

import platform.Foundation.NSLog
import platform.UIKit.UIDevice

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun customLoggerD(tag: String, message: String) {
    NSLog("%s: %s",tag, message)
}