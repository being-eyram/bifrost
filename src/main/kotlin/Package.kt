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
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val author: String? = null,
    val homepage: String? = null,
    val dependencies: Map<String, Dependency>? = null,
    @SerialName("dev_dependencies")
    val devDependencies: Map<String, String>? = null,
    val environment: Environment,
)

@Serializable
data class Dependency(
    val sdk: String? = null,
    val version: String? = null
)

@Serializable
data class Environment(
    val sdk: String? = null,
    val flutter: String? = null,
)
