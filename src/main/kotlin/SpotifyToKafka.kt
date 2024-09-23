
package org.deveshm

import okhttp3.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

val SPOTIFY_CLIENT_ID = System.getenv("SPOTIFY_CLIENT_ID") ?: error("SPOTIFY_CLIENT_ID is not set")
val SPOTIFY_CLIENT_SECRET = System.getenv("SPOTIFY_CLIENT_SECRET") ?: error("SPOTIFY_CLIENT_SECRET is not set")

val client = OkHttpClient()

val kafkaProducerProps = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
}

val kafkaProducer = KafkaProducer<String, String>(kafkaProducerProps)

suspend fun publishToKafka(topic: String, message: String) {
    val record = ProducerRecord<String, String>(topic, message)
    kotlin.runCatching {
        kafkaProducer.send(record).get()
        println("Data published to Kafka topic $topic")
    }.onFailure { e ->
        println("Error publishing to Kafka: ${e.message}")
    }
}

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

fun main() = runBlocking {
    val playlistId = "37i9dQZEVXbMDoHDwVN2tF"

    val accessToken = getSpotifyAccessToken()

    val spotifyData = fetchSpotifyData(accessToken, playlistId)

    val topic = "spotify_playlist_data"
    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
    println("spotify_data_$timestamp: $spotifyData")

    publishToKafka(topic, "spotify_data_$timestamp: $spotifyData")

    kafkaProducer.close()
}
