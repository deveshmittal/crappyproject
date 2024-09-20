@file:DependsOn("com.squareup.okhttp3:okhttp:4.10.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val SPOTIFY_CLIENT_ID = System.getenv("SPOTIFY_CLIENT_ID") ?: "67acaa8156c34bd887b0d08fd7a09fc5"
val SPOTIFY_CLIENT_SECRET = System.getenv("SPOTIFY_CLIENT_SECRET") ?: "4f31ca5f0d4b4a1bb9f5e5a4f2561191"

val client = OkHttpClient()

suspend fun getSpotifyAccessToken(): String = withContext(Dispatchers.IO) {
    val credentials = "$SPOTIFY_CLIENT_ID:$SPOTIFY_CLIENT_SECRET"
    val encodedCredentials = Base64.getEncoder().encodeToString(credentials.toByteArray())

    val requestBody = FormBody.Builder()
        .add("grant_type", "client_credentials")
        .build()

    val request = Request.Builder()
        .url("https://accounts.spotify.com/api/token")
        .post(requestBody)
        .header("Authorization", "Basic $encodedCredentials")
        .build()

    kotlin.runCatching {
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw IllegalStateException("Failed to get access token")
        val json = Json.parseToJsonElement(responseBody).jsonObject
        json["access_token"]?.jsonPrimitive?.content ?: throw IllegalStateException("Access token not found in response")
    }.getOrElse { throw it }
}

suspend fun fetchSpotifyData(accessToken: String, playlistId: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.spotify.com/v1/playlists/$playlistId")
        .header("Authorization", "Bearer $accessToken")
        .build()

    kotlin.runCatching {
        val response = client.newCall(request).execute()
        response.body?.string() ?: throw IllegalStateException("Failed to fetch Spotify data")
    }.getOrElse { throw it }
}

suspend fun call() {
    println("Test")
    val plId = "3cEYpjA9oz9GiPac4AsH4n"
    val playlistId = "37i9dQZEVXbMDoHDwVN2tF" // Spotify's "Global Top 50" playlist
    val accessToken = getSpotifyAccessToken()
    val spotifyData = fetchSpotifyData(accessToken, plId)

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    println("Spotify data fetched at $timestamp:")
    //println(spotifyData)
    val fileName = "spotify_data_$timestamp.json"
    sendDataToServer(spotifyData, fileName)
}

fun main() {
    runBlocking {
        call()
    }
}

suspend fun sendDataToServer(data: String, fileName: String) = withContext(Dispatchers.IO) {
    val json = """
        {
            "file": "$fileName",
            "content": $data
        }
    """.trimIndent()


    println(json)

    val requestBody = json.toRequestBody("application/json".toMediaType())

    val request = Request.Builder()
        .url("http://68.183.128.14:3000/")
        .post(requestBody)
        .build()

    kotlin.runCatching {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            println("Data sent successfully to server")
            println("Response: ${response.body?.string()}")
        } else {
            println("Failed to send data to server. Status code: ${response.code}")
        }
    }.getOrElse {
        println("Error sending data to server: ${it.message}")
        throw it
    }
}

main()

