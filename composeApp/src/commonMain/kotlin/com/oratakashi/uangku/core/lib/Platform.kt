package com.oratakashi.uangku.core.lib

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform