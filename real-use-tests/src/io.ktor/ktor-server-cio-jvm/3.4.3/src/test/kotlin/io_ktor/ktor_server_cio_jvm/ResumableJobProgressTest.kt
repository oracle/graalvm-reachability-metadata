/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises CIO server-sent event streaming through a reconnecting background-job progress dashboard.
 */
class ResumableJobProgressTest {
    @Test
    @Timeout(60)
    fun `resumes progress after the last displayed event without replaying earlier updates`(): Unit = runBlocking {
        val progress: List<Progress> = listOf(
            Progress(1, "progress", "job:catalog-export\nstate:queued"),
            Progress(2, "progress", "job:catalog-export\nrows:250"),
            Progress(3, "complete", "job:catalog-export\nfile:catalog.csv"),
        )
        val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            install(SSE)
            routing {
                sse("/jobs/catalog-export/events") {
                    val lastSeen: Int = call.request.headers["Last-Event-ID"]?.toInt() ?: 0
                    for (update: Progress in progress.filter { it.id > lastSeen }) {
                        send(ServerSentEvent(data = update.data, event = update.event, id = update.id.toString()))
                    }
                }
            }
        }
        try {
            withTimeout(40_000) {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(ClientCIO) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        requestTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    val url: String = "http://127.0.0.1:$port/jobs/catalog-export/events"
                    var lastDisplayed: Int = 0
                    client.prepareGet(url) {
                        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                    }.execute { response: HttpResponse ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(
                            "text/event-stream",
                            response.headers[HttpHeaders.ContentType]?.substringBefore(';'),
                        )
                        val stream: ByteReadChannel = response.bodyAsChannel()
                        assertEquals(progress[0], readProgress(stream))
                        val displayed: Progress = readProgress(stream)
                        assertEquals(progress[1], displayed)
                        lastDisplayed = displayed.id
                        // The dashboard leaves the stream before displaying the completion event.
                    }
                    client.prepareGet(url) {
                        header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
                        header("Last-Event-ID", lastDisplayed.toString())
                    }.execute { response: HttpResponse ->
                        assertEquals(HttpStatusCode.OK, response.status)
                        val stream: ByteReadChannel = response.bodyAsChannel()
                        assertEquals(progress[2], readProgress(stream))
                        assertNull(stream.readUTF8Line(), "The completed job has no further updates")
                    }
                }
            }
        } finally {
            server.stop(0, 10_000)
        }
    }

    private suspend fun readProgress(stream: ByteReadChannel): Progress {
        var id: Int? = null
        var event: String = "message"
        val data: MutableList<String> = mutableListOf()
        while (true) {
            val line: String = checkNotNull(stream.readUTF8Line()) { "Progress stream ended inside an event" }
            if (line.isEmpty()) {
                if (data.isEmpty()) continue
                return Progress(checkNotNull(id) { "Missing progress event ID" }, event, data.joinToString("\n"))
            }
            if (line.startsWith(':')) continue
            val field: String = line.substringBefore(':')
            val value: String = line.substringAfter(':', "").removePrefix(" ")
            when (field) {
                "id" -> id = value.toInt()
                "event" -> event = value
                "data" -> data.add(value)
            }
        }
    }

    private data class Progress(val id: Int, val event: String, val data: String)
}
