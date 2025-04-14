package com.example

import io.ktor.server.application.*
import io.ktor.http.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun Application.installErrorHandler() {
    install(StatusPages) {
        exception<MissingFieldException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.BadRequest,
                message = cause.message ?: "Missing field",
                code = "MISSING_FIELD",
            )
        }

        exception<InitializationFailedException> { call, cause ->
            call.respondError(
                status = HttpStatusCode.InternalServerError,
                message = cause.message ?: "Init failure",
                code = "INIT_ERROR",
            )
        }

        exception<Throwable> { call, cause ->
            call.respondError(
                status = HttpStatusCode.InternalServerError,
                message = cause.message ?: "Unexpected server error",
                code = "INTERNAL_ERROR",
            )
        }
    }
}


suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    message: String,
    code: String? = null
) {
    val errorJson = buildJsonObject {
        put("error", buildJsonObject {
            put("message", message)
            code?.let { put("code", it) }
        })
    }

    respondText(
        Json.encodeToString(errorJson),
        ContentType.Application.Json,
        status
    )
}
