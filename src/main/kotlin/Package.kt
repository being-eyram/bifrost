package com.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PackageInfo(
    val name: String? = null,
    val latest: PackageVersion? = null,
    val versions: List<PackageVersion>? = null,
)

@Serializable
data class PackageVersion(
    val version: String? = null,
    @SerialName("archive_url")
    val archiveUrl: String? = null,
    val pubspec: JsonElement? = null,
)
