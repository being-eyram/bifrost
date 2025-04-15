package com.example

import com.google.firebase.FirebaseApp
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

fun Application.configureRouting(firebaseApp: FirebaseApp? = null) {
    val isDevEnvironment = environment.config.propertyOrNull("ktor.development")?.getString()
    val protocol = if (isDevEnvironment == "true") "http://" else "https://"
    val portEnvVar = if (isDevEnvironment == "true") {
        environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: ""
    } else ""

    val port = if (portEnvVar.isNotEmpty()) ":$portEnvVar" else ""


    routing {
        if (firebaseApp == null) {
            throw InitializationFailedException("Firebase App not initialized!")
        }

        val firestore = FirestoreClient.getFirestore(firebaseApp)
        val bucket = StorageClient.getInstance(firebaseApp).bucket()

        get("/api/packages/{packageName}") {
            val packageName = call.request.pathVariables["packageName"]
                ?: throw MissingFieldException("Missing package name")

            val packageInfo = FirebasePackageService.getPackageInfo(firestore, packageName)
            val response = Json.encodeToString(packageInfo)

            call.respondText(
                response,
                contentType = ContentType.Application.Json,
            )
        }


        get("/api/packages/{packageName}/download") {
            val packageName = call.request.pathVariables["packageName"]
                ?: throw MissingFieldException("Missing package name")

            val packageInfo = FirebasePackageService.getPackageInfo(firestore, packageName)
            val packageVersion = packageInfo.latest?.version
            val blobId = "$packageName/$packageVersion/package.tar.gz"

            val signedUrl = bucket.storage.signUrl(
                bucket.get(blobId),
                15, // URL valid for 15 minutes
                TimeUnit.MINUTES,
            )

            call.respondRedirect(signedUrl.toString())
        }


        get("/api/packages/versions/new") {
            val uploadPath = "api/packages/versions/newUpload"
            val baseUrl = URLBuilder.createFromCall(call).apply { encodedPath = "" }.host
            val uploadUrl = "$protocol$baseUrl$port/$uploadPath"

            val response = buildJsonObject {
                put("url", uploadUrl)
                put("fields", buildJsonObject { })
            }

            val jsonResponse = Json.encodeToString(response)

            call.respondText(jsonResponse, ContentType.Application.Json)
        }


        post("/api/packages/versions/newUpload") {
            val host = URLBuilder.createFromCall(call).apply { encodedPath = "" }.host
            var completionUrl: String

            try {
                call.receiveMultipart().forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val packageTarGz = part.provider()
                        val packageBytes = packageTarGz.toByteArray()

                        val pubspec = extractPubspec(packageBytes)
                        val archiveHost = "$protocol$host$port"

                        FirebasePackageService.store(
                            bucket = bucket,
                            firestore = firestore,
                            packageBytes = packageBytes,
                            pubspec = pubspec,
                            archiveHost = archiveHost,
                        )
                    }
                }

                completionUrl = call.url { path("api/packages/versions/newUploadFinish") }
            } catch (e: Exception) {
                completionUrl = call.url {
                    path("api/packages/versions/newUploadFinish")
                    encodedParameters = ParametersBuilder().apply {
                        append("error", e.message.toString())
                    }
                }
            }

            call.response.header(
                name = HttpHeaders.Location,
                value = completionUrl,
            )

            call.respond(HttpStatusCode.NoContent)
        }


        get("/api/packages/versions/newUploadFinish") {
            val error = call.request.queryParameters["error"]
            val response = when (error) {
                null -> {
                    buildJsonObject {
                        put("success", buildJsonObject {
                            put("message", "Upload successful")
                        })
                    }
                }

                else -> {
                    buildJsonObject {
                        put("error", buildJsonObject {
                            put("message", error)
                        })
                    }
                }
            }

            call.respondText(
                response.toString(),
                ContentType.Application.Json,
                status = if(error == null) HttpStatusCode.OK else HttpStatusCode.BadRequest,
            )
        }
    }
}


private fun extractPubspec(packageTarGz: ByteArray): Map<String, Any> {
    val outputStream = ByteArrayOutputStream()
    GZIPInputStream(ByteArrayInputStream(packageTarGz)).use { gzipInputStream ->
        gzipInputStream.copyTo(outputStream)
    }

    val packageTar = outputStream.toByteArray()
    val tarInputStream = TarArchiveInputStream(
        ByteArrayInputStream(packageTar)
    )

    val pubspecContent = Utils.extractFileContentFromTar(
        tarInputStream,
        shouldExtract = { name ->
            name.endsWith("pubspec.yaml") || name.endsWith("pubspec.yml")
        }
    ) ?: throw MissingFieldException("Missing pubspec.yaml")


    return Yaml().load(pubspecContent) as Map<String, Any>
}

class MissingFieldException(message: String) : Exception(message)
class InitializationFailedException(message: String) : Exception(message)
