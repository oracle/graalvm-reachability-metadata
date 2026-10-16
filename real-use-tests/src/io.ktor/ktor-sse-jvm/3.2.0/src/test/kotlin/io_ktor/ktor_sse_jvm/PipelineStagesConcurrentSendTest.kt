/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.sse.SSE as ClientSSE

/**
 * Sends from concurrent producers launched in a server SSE session's scope and closes the stream from the server.
 *
 * A CI server streams one pipeline run: each stage runs as a child coroutine
 * of the `ServerSSESession` and sends its multi-line step logs on the shared
 * session at the same time as the others. Once every stage has finished, the
 * handler sends a summary and calls `close()` to end the stream for the
 * client, then keeps running to archive the run only after the client has
 * seen the stream end. The client also reads the headers the SSE route put
 * on the response, which keep proxies and caches from holding the stream.
 */
public class PipelineStagesConcurrentSendTest {

    /** What the server archived once the stream it closed had been read to the end. */
    private data class ArchiveRecord(
        val run: String,
        val eventsSeenByClient: Int,
    )

    /** One step-log event as the client saw it, its data split back into log lines. */
    private data class StepLog(
        val id: String?,
        val lines: List<String>,
    )

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private var port: Int = 0
    private val clientReachedEnd: CompletableDeferred<Int> = CompletableDeferred()
    private val archived: CompletableDeferred<ArchiveRecord> = CompletableDeferred()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(SSE)
            routing {
                sse("/pipelines/{run}") {
                    val run: String = call.parameters["run"]!!
                    val stages: List<Job> = STAGES.map { stage: String ->
                        launch(Dispatchers.Default) {
                            for (step: Int in 1..STEPS_PER_STAGE) {
                                send(
                                    ServerSentEvent(
                                        data = stepLog(stage, step),
                                        event = "step",
                                        id = "$stage-$step",
                                    ),
                                )
                                yield()
                            }
                        }
                    }
                    stages.joinAll()
                    val steps: Int = STAGES.size * STEPS_PER_STAGE
                    send(ServerSentEvent(data = "$run: $steps steps passed", event = "summary"))
                    close()

                    val eventsSeen: Int = withTimeout(10_000L) { clientReachedEnd.await() }
                    archived.complete(ArchiveRecord(run = run, eventsSeenByClient = eventsSeen))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        client = HttpClient(ClientCIO) {
            install(ClientSSE)
        }
    }

    @AfterEach
    fun stopServer() {
        client.close()
        server.stop(gracePeriodMillis = 0L, timeoutMillis = 10_000L)
    }

    @Test
    fun `concurrent stage logs arrive whole and in stage order before the server closes`() = runBlocking {
        val received: MutableList<ServerSentEvent> = mutableListOf()
        var status: HttpStatusCode? = null
        var headers: Headers = Headers.Empty

        withTimeout(10_000L) {
            client.sse(host = "127.0.0.1", port = port, path = "/pipelines/run-42") {
                status = call.response.status
                headers = call.response.headers
                incoming.collect { event: ServerSentEvent -> received.add(event) }
            }
        }
        clientReachedEnd.complete(received.size)

        assertEquals(HttpStatusCode.OK, status)
        val contentType: ContentType = ContentType.parse(headers[HttpHeaders.ContentType]!!)
        assertEquals(ContentType.Text.EventStream, contentType.withoutParameters())
        assertEquals("no-store", headers[HttpHeaders.CacheControl])
        assertEquals("no", headers["X-Accel-Buffering"])

        assertEquals(STAGES.size * STEPS_PER_STAGE + 1, received.size)
        val summary: ServerSentEvent = received.last()
        assertEquals("summary", summary.event)
        assertEquals("run-42: 12 steps passed", summary.data)

        val logsByStage: Map<String, List<StepLog>> = received
            .dropLast(1)
            .onEach { event: ServerSentEvent -> assertEquals("step", event.event) }
            .groupBy(
                keySelector = { event: ServerSentEvent -> event.id!!.substringBefore('-') },
                valueTransform = { event: ServerSentEvent -> StepLog(id = event.id, lines = event.data!!.lines()) },
            )
        assertEquals(STAGES.toSet(), logsByStage.keys)
        for (stage: String in STAGES) {
            assertEquals(
                (1..STEPS_PER_STAGE).map { step: Int ->
                    StepLog(id = "$stage-$step", lines = stepLog(stage, step).lines())
                },
                logsByStage[stage],
            )
        }

        val record: ArchiveRecord = withTimeout(10_000L) { archived.await() }
        assertEquals(ArchiveRecord(run = "run-42", eventsSeenByClient = 13), record)
    }

    @Test
    fun `concurrent sends never interleave inside one event on the wire`() = runBlocking {
        val body: String = withTimeout(10_000L) {
            HttpClient(ClientCIO).use { plainClient: HttpClient ->
                plainClient.get("http://127.0.0.1:$port/pipelines/run-7").bodyAsText()
            }
        }
        val blocks: List<List<String>> = body
            .split("\r\n\r\n")
            .filter { block: String -> block.isNotBlank() }
            .map { block: String -> block.split("\r\n") }
        clientReachedEnd.complete(blocks.size)

        assertEquals(STAGES.size * STEPS_PER_STAGE + 1, blocks.size)
        val stepBlocks: Set<Set<String>> = blocks.dropLast(1).map { lines: List<String> -> lines.toSet() }.toSet()
        val expectedStepBlocks: Set<Set<String>> = STAGES
            .flatMap { stage: String ->
                (1..STEPS_PER_STAGE).map { step: Int ->
                    setOf(
                        "event: step",
                        "id: $stage-$step",
                        "data: $stage step $step started",
                        "data: $stage step $step exit 0",
                    )
                }
            }
            .toSet()
        assertEquals(expectedStepBlocks, stepBlocks)
        assertEquals(setOf("event: summary", "data: run-7: 12 steps passed"), blocks.last().toSet())

        val record: ArchiveRecord = withTimeout(10_000L) { archived.await() }
        assertEquals(ArchiveRecord(run = "run-7", eventsSeenByClient = 13), record)
    }

    private companion object {
        val STAGES: List<String> = listOf("compile", "test", "lint")
        const val STEPS_PER_STAGE: Int = 4

        fun stepLog(stage: String, step: Int): String = "$stage step $step started\n$stage step $step exit 0"
    }
}
