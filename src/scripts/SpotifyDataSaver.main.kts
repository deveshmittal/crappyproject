@file:DependsOn("com.squareup.okhttp3:okhttp:4.10.0")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.time.Duration
import java.util.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Song(
    val name: String,
    val artists: String,
    val duration: String,
    val album: String,
    val releaseDate: String,   // Added field for release date
    val previewUrl: String?,   // Added field for preview URL
    val popularity: Int,       // Added field for popularity
    val explicit: Boolean      // Added field for explicit content
)

val SPOTIFY_CLIENT_ID = System.getenv("SPOTIFY_CLIENT_ID") ?: "67acaa8156c34bd887b0d08fd7a09fc5"
val SPOTIFY_CLIENT_SECRET = System.getenv("SPOTIFY_CLIENT_SECRET") ?: "4f31ca5f0d4b4a1bb9f5e5a4f2561191"

val client = OkHttpClient()

var i = 1

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

suspend fun searchSpotifyPlaylists(accessToken: String, year: Int): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.spotify.com/v1/search?q=year:$year&type=playlist&limit=1")
        .header("Authorization", "Bearer $accessToken")
        .build()

    kotlin.runCatching {
        val response = client.newCall(request).execute()
        response.body?.string() ?: throw IllegalStateException("Failed to fetch Spotify data")
    }.getOrElse { throw it }
}

suspend fun fetchPlaylistTracks(accessToken: String, playlistId: String): String = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100")
        .header("Authorization", "Bearer $accessToken")
        .build()

    kotlin.runCatching {
        val response = client.newCall(request).execute()
        response.body?.string() ?: throw IllegalStateException("Failed to fetch playlist tracks")
    }.getOrElse { throw it }
}

suspend fun getTopSongsForYear(accessToken: String, year: Int): List<Song> {
    println("Fetching data for year $year")
    val searchResult = searchSpotifyPlaylists(accessToken, year)
    val searchJson = Json.parseToJsonElement(searchResult).jsonObject
    val playlistId = searchJson["playlists"]?.jsonObject?.get("items")?.jsonArray?.getOrNull(0)?.jsonObject?.get("id")?.jsonPrimitive?.content
        ?: throw IllegalStateException("No playlist found for year $year")

    val tracksResult = fetchPlaylistTracks(accessToken, playlistId)
    val tracksJson = Json.parseToJsonElement(tracksResult).jsonObject

    return tracksJson["items"]?.jsonArray?.mapNotNull { item ->
        val track = item.jsonObject["track"]?.jsonObject
        val name = track?.get("name")?.jsonPrimitive?.content
        val duration = track?.get("duration_ms")?.jsonPrimitive?.content
        val album = track?.get("album")?.jsonObject?.get("name")?.jsonPrimitive?.content ?: ""
        val releaseDate = track?.get("album")?.jsonObject?.get("release_date")?.jsonPrimitive?.content ?: ""
        val artists = track?.get("artists")?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        val previewUrl = track?.get("preview_url")?.jsonPrimitive?.content
        val popularity = track?.get("popularity")?.jsonPrimitive?.int ?: 0
        val explicit = track?.get("explicit")?.jsonPrimitive?.boolean ?: false

        if (name != null && artists != null && duration != null) {
            Song(
                name,
                artists.joinToString(", "),
                duration,
                album,
                releaseDate,
                previewUrl,
                popularity,
                explicit
            )
        } else null
    } ?: emptyList()
}

suspend fun sendInBatches(songs: List<Song>, year: Int) {
    val batchSize = 10
    val batches = songs.chunked(batchSize)

    for ((index, batch) in batches.withIndex()) {
        val batchData = buildJsonArray {
            batch.forEach { (name, artists, duration, album, releaseDate, previewUrl, popularity, explicit) ->
                add(buildJsonObject {
                    put("name", name)
                    put("artists", artists)
                    put("duration", duration)
                    put("album", album)
                    put("releaseDate", releaseDate)
                    put("previewUrl", previewUrl ?: "N/A")
                    put("popularity", popularity)
                    put("explicit", explicit)
                })
            }
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val fileName = "top_songs_${year}_batch_${index}_$timestamp.json"
        sendDataToServer(batchData.toString(), fileName)

        println("Wait 500 milliseconds before sending the next batch..")
        // Wait 500 milliseconds before sending the next batch
        delay(500)
    }
}

suspend fun getAllYearsTopSongs() {
    val accessToken = getSpotifyAccessToken()
    while (true) {
        for (year in 2014 downTo 1961) {
            try {
                val topSongs = getTopSongsForYear(accessToken, year)
                println("Sending data for year $year in batches...")
                sendInBatches(topSongs, year)
            } catch (e: Exception) {
                println(e.message)
                println("Error processing the data for year: $year")
            }

            if (year < 2023) {
                println("Waiting 5 seconds before fetching next year's data...")
                delay(5000) // Wait for 5 seconds
            }
        }
    }
}

suspend fun sendDataToServer(data: String, fileName: String) = withContext(Dispatchers.IO) {
    val localFile = File(fileName)
    localFile.writeText(data)
    println("File saved locally at : ${localFile.absolutePath}")
    val json = """
        {
            "file": "$fileName",
            "content": ${Json.encodeToString(JsonPrimitive(data))}
        }
    """.trimIndent()

    println("Sending data to server: $json")

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

fun main() {
    runBlocking {
        getAllYearsTopSongs()
    }
}

main()
