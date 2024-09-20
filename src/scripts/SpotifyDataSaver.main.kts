@file:DependsOn("com.squareup.okhttp3:okhttp:4.10.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")

import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.util.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val SPOTIFY_CLIENT_ID = System.getenv("SPOTIFY_CLIENT_ID") ?: "67acaa8156c34bd887b0d08fd7a09fc5"
val SPOTIFY_CLIENT_SECRET = System.getenv("SPOTIFY_CLIENT_SECRET") ?: "4f31ca5f0d4b4a1bb9f5e5a4f2561191"

val client = OkHttpClient()

suspend fun getSpotifyAccessToken(): String {
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

    val response = client.newCall(request).execute()
    val responseBody = response.body?.string() ?: error("Failed to get access token")
    val json = Json.parseToJsonElement(responseBody).jsonObject
    return json["access_token"]?.jsonPrimitive?.content ?: error("Access token not found in response")
}

suspend fun fetchSpotifyData(accessToken: String, playlistId: String): String {
    val request = Request.Builder()
        .url("https://api.spotify.com/v1/playlists/$playlistId")
        .header("Authorization", "Bearer $accessToken")
        .build()

    val response = client.newCall(request).execute()
    return response.body?.string() ?: error("Failed to fetch Spotify data")
}

suspend fun call() {
    val playlistId = "37i9dQZEVXbMDoHDwVN2tF" // Spotify's "Global Top 50" playlist
    println("Test")

    val accessToken = getSpotifyAccessToken()
    val spotifyData = fetchSpotifyData(accessToken, playlistId)

    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    println("Spotify data fetched at $timestamp:")
    println(spotifyData)
}

suspend fun main() {
    runBlocking {
        call()
    }
}