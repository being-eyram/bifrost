package com.example

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.storage.Bucket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.*

object FirebasePackageService {
    private val json = Json { ignoreUnknownKeys = true }

    private fun saveFileToBucket(
        bucket: Bucket,
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ): String {
        val packageName = pubspec["name"] as String
        val packageVersion = pubspec["version"] as String
        val fileName = "$packageName/$packageVersion/package.tar.gz"

        val blob = bucket.create(
            fileName,
            packageBytes,
            "application/gzip"
        )

        return blob.mediaLink
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
        assertVersionDoesNotExist(pubspec, firestore)

        saveFileToBucket(bucket, packageBytes, pubspec).also { url ->
            saveMetadata(firestore, url, pubspec)
        }
    }

    private fun assertVersionDoesNotExist(
        pubspec: Map<String, Any>,
        firestore: Firestore
    ) {
        val packageName = pubspec["name"] as? String
            ?: throw IllegalArgumentException("Package name is required")

        val packageVersion = pubspec["version"] as? String
            ?: throw IllegalArgumentException("Package version is required")

        val versionDocId = "$packageName@$packageVersion"
        val versionDoc = firestore.collection("versions").document(versionDocId).get().get()

        if (versionDoc.exists()) {
            throw IllegalStateException("Version $packageVersion of package $packageName already exists.")
        }
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

        val versions = versionsSnapshot
            ?.documents
            ?.mapNotNull { doc -> doc.data.toDataClass<PackageVersion>() }

        val latestMap = packageData["latest"]
        val latest = latestMap?.let { ltst ->
            json.decodeFromJsonElement<PackageVersion>(ltst.toJsonElement())
        }

        return PackageInfo(
            name = packageData["name"] as? String,
            latest = latest,
            versions = versions
        )
    }
}
