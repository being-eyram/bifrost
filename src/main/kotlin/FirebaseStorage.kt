package com.example

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.google.cloud.storage.Bucket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.*

class FirebasePackageService(
    private val firestore: Firestore,
    private val bucket: Bucket,
) {

    /**
     * Saves a package file to cloud storage and returns its public URL.
     *
     * @param packageBytes - The package contents as a byte array
     * @param pubspec - The package metadata from pubspec.yaml
     * @throws Exception if storage operations fail
     */
    private fun saveFileToBucket(
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ): String {
        val packageName = pubspec["name"] as String
        val packageVersion = pubspec["version"] as String
        val fileName = "$packageName/$packageVersion/package.tar.gz"

        try {

            // Upload the file
            bucket.create(
                fileName,
                packageBytes,
                "application/gzip"
            )

            // Get the public URL
            val publicUrl = "https://storage.googleapis.com/${bucket.name}/$fileName"

            return publicUrl
        } catch (error: Exception) {

            throw error
        }
    }


    private fun saveMetadata(
        packageUrl: String,
        pubspec: Map<String, Any>
    ) {
        try {
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

        } catch (_: Exception) {
        }
    }

    fun store(
        packageBytes: ByteArray,
        pubspec: Map<String, Any>
    ) {
        val packageUrl = saveFileToBucket(packageBytes, pubspec)
        saveMetadata(packageUrl, pubspec)
    }

//    fun getPackageInfo(packageName: String): Map<String, Any?> {
//        val packageRef = firestore.collection("packages").document(packageName)
//        val packageSnapshot = packageRef.get()
//
//        val packageData = packageSnapshot.get().data ?: emptyMap<String, Any?>()
//
//        // Get versions
//        val versionsSnapshot = firestore.collection("versions")
//            .whereEqualTo("package_name", packageName).get()
//            .get()
//
//        val versions = versionsSnapshot.documents.mapNotNull { doc ->
//            val versionData = doc.data
//
//            mutableMapOf<String, Any?>().apply {
//                this["version"] = versionData["version"]
//                versionData["retracted"]?.let { this["retracted"] = it }
//                this["archive_url"] = versionData["archive_url"]
//                versionData["archive_sha256"]?.let { this["archive_sha256"] = it }
//                this["pubspec"] = versionData["pubspec"]
//            }
//        }
//
//        // Build response
//        return mutableMapOf<String, Any?>().apply {
//            this["name"] = packageData["name"]
//            packageData["isDiscontinued"]?.let { this["isDiscontinued"] = it }
//            packageData["replacedBy"]?.let { this["replacedBy"] = it }
//            packageData["advisoriesUpdated"]?.let { this["advisoriesUpdated"] = it }
//
//            val latest = packageData["latest"] as? Map<*, *>
//            latest?.let {
//                val latestMap = mutableMapOf<String, Any?>()
//                latestMap["version"] = it["version"]
//                (it["retracted"] as? Boolean)?.let { retracted -> latestMap["retracted"] = retracted }
//                latestMap["archive_url"] = it["archive_url"]
//                (it["archive_sha256"] as? String)?.let { sha -> latestMap["archive_sha256"] = sha }
//                latestMap["pubspec"] = it["pubspec"]
//
//                this["latest"] = latestMap
//            }
//
//            this["versions"] = versions
//        }
//    }

    fun getPackageInfo(packageName: String): PackageInfo {
        val packageRef = firestore
            .collection("packages")
            .document(packageName)
            .get()

        val packageSnapshot = packageRef.get()

        val packageData = packageSnapshot.data ?: emptyMap()

        val versionsSnapshot = firestore.collection("versions")
            .whereEqualTo("package_name", packageName).get()
            .get()

        val versions = versionsSnapshot.documents.mapNotNull { doc ->
            val versionData = doc.data


            PackageVersion(
                version = versionData["version"] as String?,
                archiveUrl = versionData["archive_url"] as String?,
                pubspec =   Json.encodeToJsonElement(versionData["pubspec"].toString())
            )
        }

        val latestMap = packageData["latest"] as? Map<*, *>
        val latest = latestMap?.let {
            PackageVersion(
                version = it["version"] as? String,
                archiveUrl = it["archive_url"] as? String,
                pubspec = Json.encodeToJsonElement(it["pubspec"].toString())
            )
        }

        return PackageInfo(
            name = packageData["name"] as? String,
            latest = latest,
            versions = versions
        )
    }

}

