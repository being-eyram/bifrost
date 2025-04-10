package com.example

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val pubspec: Pubspec? = null,
)

@Serializable
data class Pubspec(
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val homepage: String,
    val dependencies: Map<String, Dependency>,
    @SerialName("dev_dependencies")
    val devDependencies: Map<String, String>,
    val environment: Environment,
)

@Serializable
data class Dependency(
    val sdk: String? = null,
    val version: String? = null
)

@Serializable
data class Environment(
    val sdk: String,
    val flutter: String
)
