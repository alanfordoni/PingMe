package com.spbalkan.pingme

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun customLoggerD(tag: String, message: String)