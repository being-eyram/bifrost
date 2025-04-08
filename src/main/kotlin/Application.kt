package com.example

import io.ktor.server.application.*
import io.ktor.server.plugins.compression.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    FirebaseAdmin.init()
    install(Compression){
        gzip()
        deflate()
    }
    configureMonitoring()
    configureSerialization()
    configureRouting()
}
