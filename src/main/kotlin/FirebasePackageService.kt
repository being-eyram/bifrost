package com.example

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.storage.Bucket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

object FirebasePackageService {

    private fun saveFileToBucket(
        bucket: Bucket,
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ): String {
        val packageName = pubspec["name"] as String
        val packageVersion = pubspec["version"] as String
        val fileName = "$packageName/$packageVersion/package.tar.gz"

        bucket.create(
            fileName,
            packageBytes,
            "application/gzip"
        )

        return "https://storage.googleapis.com/${bucket.name}/$fileName"
    }

    private fun saveMetadata(
        firestore: Firestore,
        packageUrl: String,
        pubspec: Map<String, Any>
    ) {
        val packageRef = firestore
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
        val versionRef = firestore.collection("versions")
            .document("${pubspec["name"]}@${pubspec["version"]}")

        val versionData = mapOf(
            "package_name" to pubspec["name"],
            "version" to pubspec["version"],
            "archive_url" to packageUrl,
            "pubspec" to pubspec,
            "created_at" to Date()
        )

        versionRef.set(versionData)
    }

    fun store(
        bucket: Bucket,
        firestore: Firestore,
        packageBytes: ByteArray,
        pubspec: Map<String, Any>,
    ) {
        require(pubspec.containsKey("name")) { "Package name is required" }
        require(pubspec.containsKey("version")) { "Package version is required" }

        val packageUrl = saveFileToBucket(bucket, packageBytes, pubspec)
        saveMetadata(firestore, packageUrl, pubspec)
    }

    fun getPackageInfo(
        firestore: Firestore,
        packageName: String,
    ): PackageInfo {
        val packageSnapshot = firestore
            .collection("packages")
            .document(packageName)
            .get()
            .get()

        val packageData = packageSnapshot?.data ?: emptyMap()

        val versionsSnapshot = firestore.collection("versions")
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

        return PackageInfo(
            name = packageData["name"] as? String,
            latest = latest,
            versions = versions
        )
    }
}


