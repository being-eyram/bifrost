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



//@Serializable
//data class Pubspec(
//    val name: String,
//    val version: String,
//    val description: String,
//    val repository: String,
//    val environment: Environment,
//    val dependencies: Map<String, String>,
//    val dev_dependencies: Map<String, String>
//)
//
//@Serializable
//data class Environment(
//    val sdk: String
//)

