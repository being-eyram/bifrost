package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import net.mamoe.yamlkt.YamlBuilder
import org.slf4j.event.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json { ignoreUnknownKeys = true }
        )
    }
}
