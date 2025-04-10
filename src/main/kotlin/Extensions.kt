package com.example

import kotlinx.serialization.json.*

fun Any?.toJsonElement(): JsonElement = when (this) {
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *>  -> {
        val content = this.mapNotNull { (k, v) ->
            if (k is String) k to v.toJsonElement() else null
        }.toMap()
        JsonObject(content)
    }
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonNull
}


inline fun <reified T> Map<String, Any?>.toDataClass(): T {
    val jsonElement = this.toJsonElement()
    return Json{ignoreUnknownKeys = true }.decodeFromJsonElement(jsonElement)
}
