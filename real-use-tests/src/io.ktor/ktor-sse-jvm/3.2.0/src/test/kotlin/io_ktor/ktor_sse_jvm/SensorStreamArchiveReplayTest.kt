/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.LocalPathContent
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.sse.ServerSentEvent
import io.ktor.sse.TypedServerSentEvent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Records a live stream into a `text/event-stream` archive with `ServerSentEvent.toString()` and replays it.
 *
 * A greenhouse controller records the sensor events it publishes into a
 * `text/event-stream` archive file, encoding each payload as JSON and letting
 * the event render itself, so a support engineer can later replay a session: a
 * plain route serves the archive from disk as an event stream, and the replay
 * tool reads it back through the client SSE plugin and decodes every payload
 * into the events that were recorded.
 */
public class SensorStreamArchiveReplayTest {

    /** One measurement a greenhouse sensor reports. */
    @Serializable
    private data class Reading(
        val sensor: String,
        val celsius: Double,
        val note: String,
    )

    /** Compact encoding the live feed and the archive both use. */
    private val compactJson: Json = Json

    /** Pretty encoding an engineer asks for, which spreads one payload over several lines. */
    private val prettyJson: Json = Json { prettyPrint = true }

    /** The session the controller recorded, in publication order. */
    private val session: List<TypedServerSentEvent<Reading>> = listOf(
        TypedServerSentEvent(
            data = Reading(sensor = "bed-1", celsius = 21.5, note = "steady"),
            event = "reading",
            id = "r-1",
            retry = 3_000L,
        ),
        TypedServerSentEvent(
            data = Reading(sensor = "bed-2", celsius = 27.25, note = "vent opened"),
            event = "reading",
            id = "r-2",
            comments = "operator override",
        ),
        TypedServerSentEvent(
            data = Reading(sensor = "bed-1", celsius = 19.0, note = "night mode"),
            event = "reading",
            id = "r-3",
        ),
    )

    @TempDir
    lateinit var archiveDir: Path

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0

    /** Writes the session as an event-stream archive, one blank line after every event. */
    private fun export(name: String, json: Json): Path {
        val archive: Path = archiveDir.resolve("$name.sse")
        val text: String = session.joinToString(separator = "") { event: TypedServerSentEvent<Reading> ->
            val frame: ServerSentEvent = ServerSentEvent(
                data = json.encodeToString(Reading.serializer(), event.data!!),
                event = event.event,
                id = event.id,
                retry = event.retry,
                comments = event.comments,
            )
            frame.toString() + "\r\n"
        }
        archive.writeText(text)
        return archive
    }

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/archives/{name}") {
                    val name: String = call.parameters["name"]!!
                    call.respond(LocalPathContent(archiveDir.resolve("$name.sse"), ContentType.Text.EventStream))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO) {
            install(ClientSSE) {
                showCommentEvents()
                showRetryEvents()
            }
        }
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    /** Replays an archive over HTTP and decodes each event back into a typed one. */
    private suspend fun replay(name: String): List<TypedServerSentEvent<Reading>> {
        val replayed: MutableList<TypedServerSentEvent<Reading>> = mutableListOf()
        client.sse(host = "127.0.0.1", port = port, path = "/archives/$name") {
            incoming.collect { event: ServerSentEvent ->
                replayed.add(
                    TypedServerSentEvent(
                        data = compactJson.decodeFromString(Reading.serializer(), event.data!!),
                        event = event.event,
                        id = event.id,
                        retry = event.retry,
                        comments = event.comments,
                    ),
                )
            }
        }
        return replayed
    }

    @Test
    fun `archive holds every recorded event in text event-stream form`() {
        val archive: Path = export(name = "compact", json = compactJson)
        val events: List<String> = archive.readText().split("\r\n\r\n").filter { it.isNotEmpty() }

        assertEquals(3, events.size)
        assertEquals(
            listOf(
                "data: {\"sensor\":\"bed-1\",\"celsius\":21.5,\"note\":\"steady\"}",
                "event: reading",
                "id: r-1",
                "retry: 3000",
            ).sorted(),
            events[0].split("\r\n").sorted(),
        )
        assertEquals(
            listOf(
                "data: {\"sensor\":\"bed-2\",\"celsius\":27.25,\"note\":\"vent opened\"}",
                "event: reading",
                "id: r-2",
                ": operator override",
            ).sorted(),
            events[1].split("\r\n").sorted(),
        )
    }

    @Test
    fun `replay tool restores the recorded session from the archive`() = runBlocking {
        export(name = "compact", json = compactJson)

        assertEquals(session, replay("compact"))
    }

    @Test
    fun `pretty printed payloads span several data lines and still replay intact`() = runBlocking {
        val archive: Path = export(name = "pretty", json = prettyJson)
        val dataLines: Int = archive.readText().split("\r\n").count { it.startsWith("data: ") }

        assertEquals(session.size * 5, dataLines)
        assertEquals(session, replay("pretty"))
    }
}
