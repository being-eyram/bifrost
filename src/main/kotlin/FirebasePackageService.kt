package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.storage.Bucket
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.cloud.StorageClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.InputStream
import java.util.*

object FirebasePackageService {
    private const val FIREBASE_ADMIN_RES = "firebase-admin-sdk.json"
    private const val STORAGE_BUCKET = "bifrost-e0b03.firebasestorage.app"

    private var firestore: Firestore? = null
    private var bucket: Bucket? = null

    private val serviceAccount: InputStream? =
        this::class.java.classLoader.getResourceAsStream(FIREBASE_ADMIN_RES)

    private val options: FirebaseOptions = FirebaseOptions.builder()
        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
        .setStorageBucket(STORAGE_BUCKET)
        .build()

    private val firebaseApp by lazy {
        FirebaseApp.initializeApp(options)
    }

    fun init() {
        firestore = FirestoreClient.getFirestore(firebaseApp)
        bucket = StorageClient.getInstance(firebaseApp).bucket()
    }

    private fun saveFileToBucket(
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ): String {
        val packageName = pubspec["name"] as String
        val packageVersion = pubspec["version"] as String
        val fileName = "$packageName/$packageVersion/package.tar.gz"

        return bucket?.let {
            it.create(
                fileName,
                packageBytes,
                "application/gzip"
            )
            "https://storage.googleapis.com/${bucket!!.name}/$fileName"
        } ?: throw EnsureInitializationException("Storage bucket is initialized.")
    }

    private fun saveMetadata(
        packageUrl: String,
        pubspec: Map<String, Any>
    ) {
        firestore?.let {
            val packageRef = it
                .collection("packages")
                .document(pubspec["name"] as String)

            // Update or create package document
            val packageData = mapOf(
                "name" to pubspec["name"],
                "latest" to mapOf(
                    "version" to pubspec["version"],
                    "archive_url" to packageUrl,
                    "pubspec" to pubspec
                ),
                "updated_at" to Date()
            )

            packageRef.set(packageData, SetOptions.merge())

            // Add version document
            val versionRef = it.collection("versions")
                .document("${pubspec["name"]}@${pubspec["version"]}")

            val versionData = mapOf(
                "package_name" to pubspec["name"],
                "version" to pubspec["version"],
                "archive_url" to packageUrl,
                "pubspec" to pubspec,
                "created_at" to Date()
            )

            versionRef.set(versionData)
        } ?: throw EnsureInitializationException("Ensure firestore is initialized.")
    }

    fun store(
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ) {
        require(pubspec.containsKey("name")) { "Package name is required" }
        require(pubspec.containsKey("version")) { "Package version is required" }

        val packageUrl = saveFileToBucket(packageBytes, pubspec)
        saveMetadata(packageUrl, pubspec)
    }

    fun getPackageInfo(packageName: String): PackageInfo {
        return firestore?.let {
            val packageSnapshot = it
                .collection("packages")
                .document(packageName)
                .get()
                .get()

            val packageData = packageSnapshot?.data ?: emptyMap()

            val versionsSnapshot = it.collection("versions")
                .whereEqualTo("package_name", packageName)
                .get()
                .get()

            val versions = versionsSnapshot?.documents?.mapNotNull { doc ->
                val versionData = doc.data

                PackageVersion(
                    version = versionData["version"] as String?,
                    archiveUrl = versionData["archive_url"] as String?,
                    pubspec = Json.encodeToJsonElement(versionData["pubspec"].toString())
                )
            }

            val latestMap = packageData["latest"] as? Map<*, *>
            val latest = latestMap?.let { ltst ->
                PackageVersion(
                    version = ltst["version"] as? String,
                    archiveUrl = ltst["archive_url"] as? String,
                    pubspec = Json.encodeToJsonElement(ltst["pubspec"].toString())
                )
            }

            PackageInfo(
                name = packageData["name"] as? String,
                latest = latest,
                versions = versions
            )
        } ?: throw EnsureInitializationException("Ensure firestore is initialized.")

    }
}

class EnsureInitializationException(msg: String) : Exception(msg)
