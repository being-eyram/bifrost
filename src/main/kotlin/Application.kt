package com.example

import com.google.firebase.cloud.StorageClient
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val firebaseApp = initializeFirebaseApp()

    install(Compression){
        gzip()
        deflate()
    }
    install(CallLogging) {
        level = Level.TRACE
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    configureRouting(firebaseApp)
}
