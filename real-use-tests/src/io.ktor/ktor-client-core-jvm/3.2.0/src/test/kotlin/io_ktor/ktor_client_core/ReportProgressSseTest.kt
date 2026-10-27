/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_core

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.sse.SSE as ServerSSE

/** Exercises client Server-Sent Events by following a report job's progress through completion. */
class ReportProgressSseTest {
    @Test
    @Timeout(60)
    fun consumesReportProgressAndPreservesMultilineCompletionDetails(): Unit = runBlocking {
        withTimeout(45_000) {
            val server = embeddedServer(ServerCIO, host = "127.0.0.1", port = 0) {
                install(ServerSSE)
                routing {
                    sse("/reports/report-42/events") {
                        send(ServerSentEvent(comments = "Report worker connected"))
                        send(ServerSentEvent(data = "Queued", event = "progress", id = "1"))
                        send(ServerSentEvent(data = "Processed 120 rows", event = "progress", id = "2"))
                        send(ServerSentEvent(
                            data = "Report ready\nDownload: /reports/report-42.csv",
                            event = "complete",
                            id = "3"
                        ))
                    }
                }
            }
            try {
                server.start(wait = false)
                val port: Int = server.engine.resolvedConnectors().single().port
                HttpClient(CIO) {
                    install(SSE) {
                        // This job endpoint closes after completion; it is not a persistent feed.
                        maxReconnectionAttempts = 0
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = 15_000
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 15_000
                    }
                }.use { client: HttpClient ->
                    client.sse(urlString = "http://127.0.0.1:$port/reports/report-42/events") {
                        val updates: List<ServerSentEvent> = incoming.toList()
                        // Connection comments must not appear as user-facing progress updates.
                        assertEquals(listOf("1", "2", "3"), updates.map { it.id })
                        assertEquals(listOf("progress", "progress", "complete"), updates.map { it.event })
                        // The report UI displays each data line independently of its line separator.
                        assertEquals(
                            listOf(
                                listOf("Queued"),
                                listOf("Processed 120 rows"),
                                listOf("Report ready", "Download: /reports/report-42.csv")
                            ),
                            updates.map { it.data?.lines() }
                        )
                    }
                }
            } finally {
                server.stop(0, 10_000)
            }
        }
    }
}
