package com.example

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import io.ktor.server.application.*
import java.io.ByteArrayInputStream

fun Application.initializeFirebaseApp(): FirebaseApp? {
    val projectId = environment.config.propertyOrNull("firebase.projectId")?.getString()
        ?: throw IllegalStateException("Firebase Project ID not found")

    val privateKey = (environment.config.propertyOrNull("firebase.privateKey")?.getString()
        ?: throw IllegalStateException("Firebase Private Key not found"))
        .replace("\\n", "\n") // Handle escaped newlines if necessary

    val clientEmail = environment.config.propertyOrNull("firebase.clientEmail")?.getString()
        ?: throw IllegalStateException("Firebase Client Email not found")


    val serviceAccountJson = """   
        {
          "type": "service_account",
          "project_id": "$projectId",
          "private_key_id": "b95f9fc005b099e928d9ac81bcc18de9973a779d",
          "private_key": "$privateKey",
          "client_email": "$clientEmail",
          "client_id": "102207301430821419558",
          "auth_uri": "https://accounts.google.com/o/oauth2/auth",
          "token_uri": "https://oauth2.googleapis.com/token",
          "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
          "client_x509_cert_url": "https://www.googleapis.com/robot/v1/metadata/x509/${
        clientEmail.replace("@", "%40")
    }", 
          "universe_domain": "googleapis.com"
        }
        """.trimIndent()

    val credentials = GoogleCredentials.fromStream(
        ByteArrayInputStream(serviceAccountJson.toByteArray())
    )

    val options = FirebaseOptions.builder()
        .setCredentials(credentials)
        .setStorageBucket("bifrost-e0b03.firebasestorage.app")
        .build()

    return FirebaseApp.initializeApp(options)
}
