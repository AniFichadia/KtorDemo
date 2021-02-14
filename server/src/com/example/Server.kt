package com.example

import io.ktor.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty


object Server {
    @JvmStatic
    fun main(args: Array<String>) {
        embeddedServer(
            Netty,
            8080,
            module = Application::module
        ).start()
    }
}

fun Application.module(testing: Boolean = false) {
}
