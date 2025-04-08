package com.example

import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.yaml.snakeyaml.Yaml
import java.io.ByteArrayInputStream


fun Application.configureRouting() {
    val firebasePackageService = FirebasePackageService(
        firestore = FirestoreClient.getFirestore(),
        bucket = StorageClient.getInstance().bucket(),
    )

    routing {
        get("/api/packages/{packageName}") {

            val packageName = call.request.pathVariables["packageName"] ?: return@get call.respond(HttpStatusCode.OK)

            val packageInfo = firebasePackageService.getPackageInfo(packageName)

            val response = Json.encodeToString(packageInfo)
            call.respondText(
                response,
                contentType = ContentType.Application.Json,
            )
        }


        get("/api/packages/versions/new") {
            val response = UploadUrl(
                url = "http://localhost:8080/api/packages/versions/newUpload",
                fields = mapOf()
            )

            val jsonResponse = Json.encodeToString(response)
            call.respondText(jsonResponse, ContentType.Application.Json)
        }


        post("/api/packages/versions/newUpload") {
            call.request.headers["Content-Type"] ?: return@post call.respond(HttpStatusCode.BadRequest)

            call.receiveMultipart().forEachPart { part ->
                if (part is PartData.FileItem) {
                    val packageTarGz = part.provider()
                    val packageTar = GZip.decode(packageTarGz).toByteArray()
                    val tarInputStream = TarArchiveInputStream(
                        ByteArrayInputStream(packageTar)
                    )

                    val pubspecContent = TarUtils.extractFileContentFromTar(
                        tarInputStream,
                        shouldExtract = { name ->
                            name.endsWith("pubspec.yaml") || name.endsWith("pubspec.yml")
                        }
                    ) ?: return@forEachPart call.respond(HttpStatusCode.BadRequest, "Missing pubspec.yaml")


                    val pubspec = Yaml().load<Map<String, Any>>(pubspecContent) ?: return@forEachPart call.respond(
                        HttpStatusCode.BadRequest
                    )

                    firebasePackageService.store(
                        packageBytes = packageTarGz.toByteArray(),
                        pubspec = pubspec
                    )
                }
            }


            call.response.header(
                name = HttpHeaders.Location,
                value = "http://localhost:8080/api/packages/versions/newUploadFinish",
            )

            call.respond(HttpStatusCode.NoContent)
        }

        get("/api/packages/versions/newUploadFinish") {

            val response = buildJsonObject {
                put("success", buildJsonObject {
                    put("message", "Upload successful")
                })
            }

            call.respondText(response.toString(), ContentType.Application.Json)
        }
    }
}

@Serializable
data class UploadUrl(
    val url: String,
    val fields: Map<String, String>,
)
