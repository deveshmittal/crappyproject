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
import java.util.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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

suspend fun getTopSongsForYear(accessToken: String, year: Int): List<Triple<String, String, String>> {
    println("Fetching data for year $year")
    val searchResult = searchSpotifyPlaylists(accessToken, year)
    val searchJson = Json.parseToJsonElement(searchResult).jsonObject
    val playlistId = searchJson["playlists"]?.jsonObject?.get("items")?.jsonArray?.getOrNull(0)?.jsonObject?.get("id")?.jsonPrimitive?.content
        ?: throw IllegalStateException("No playlist found for year $year")

    val tracksResult = fetchPlaylistTracks(accessToken, playlistId)
    val tracksJson = Json.parseToJsonElement(tracksResult).jsonObject
    val localFile = File("songs${i++}.json")
    localFile.writeText(tracksJson.toString())
    //println("Devesh $tracksJson")
    return tracksJson["items"]?.jsonArray?.mapNotNull { item ->
        val track = item.jsonObject["track"]?.jsonObject
        val name = track?.get("name")?.jsonPrimitive?.content
        val duration = track?.get("duration_ms")?.jsonPrimitive?.content
        val artists = track?.get("artists")?.jsonArray?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
        if (name != null && artists != null && duration != null) {
            Triple(name,  artists.joinToString(", "), duration)
        } else null
    } ?: emptyList()
}

suspend fun getAllYearsTopSongs() {
    val accessToken = getSpotifyAccessToken()
    val allYearsData = mutableMapOf<Int, List<Triple<String, String, String>>>()

    while (true){
        for (year in 2014 downTo 1961) {
            try {
                val topSongs = getTopSongsForYear(accessToken, year)
                allYearsData[year] = topSongs

                val yearData = buildJsonObject {
                    put(year.toString(), buildJsonArray {
                        topSongs.forEach { (name, artists, duration) ->
                            add(buildJsonObject {
                                put("name", name)
                                put("artists", artists)
                                put("duration", duration)
                            })
                        }
                    })
                }

                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val fileName = "top_songs_${year}_$timestamp.json"
                sendDataToServer(yearData.toString(), fileName)
            }catch(e: Exception){
                println("Error sending the data for year: $year")
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