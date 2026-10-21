/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_logging_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.plugins.logging.MessageLengthLimitingLogger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.TimeUnit
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * Long-message chunking through `MessageLengthLimitingLogger` in the `Logging` client plugin.
 *
 * A report client downloads a daily CSV export and a long signed download token from an
 * embedded CIO server. Its log sink only accepts short records, so the client wraps the sink
 * in a `MessageLengthLimitingLogger`: every record the sink receives stays within the limit,
 * the CSV export is split on row boundaries so each row stays readable, and the token, which
 * has no line breaks, is cut into fixed-size pieces that still add up to the whole token.
 */
class ReportExportMessageLengthLimitTest {

    /** A log sink that keeps every record it is handed, as a size-limited log shipper would. */
    class RecordSink : Logger {
        private val entries: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun log(message: String) {
            entries += message
        }

        fun records(): List<String> = entries.toList()

        /** Polls until [count] records start with [prefix], bounded by [timeoutMillis]. */
        fun awaitRecords(prefix: String, count: Int, timeoutMillis: Long): List<String> {
            val deadline: Long = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
            var current: List<String> = records()
            while (current.count { record -> record.lines().any { it.startsWith(prefix) } } < count &&
                System.nanoTime() < deadline
            ) {
                Thread.onSpinWait()
                current = records()
            }
            return current
        }
    }

    private val maxLength: Int = 512
    private val minLength: Int = 256
    private val csvRows: List<String> = listOf("sku,product,units") +
        (1..80).map { "row-%03d,widget-%03d,%04d".format(it, it, it * 7) }
    private val csvReport: String = csvRows.joinToString("\n")
    private val downloadToken: String = "abcdefghij".repeat(130)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/reports/daily.csv") {
                    call.respondText(csvReport, ContentType.Text.CSV, HttpStatusCode.OK)
                }
                get("/reports/daily.csv/token") {
                    call.respondText(downloadToken, ContentType.Text.Plain, HttpStatusCode.OK)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun longLogMessagesReachTheSinkInBoundedChunks() {
        val sink = RecordSink()
        val client = HttpClient(ClientCIO) {
            install(Logging) {
                logger = MessageLengthLimitingLogger(maxLength = maxLength, minLength = minLength, delegate = sink)
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }

        val reportUrl = "http://127.0.0.1:$port/reports/daily.csv"
        client.use {
            runBlocking {
                val report = it.get(reportUrl)
                assertEquals(HttpStatusCode.OK, report.status)
                assertEquals(csvReport, report.bodyAsText())
            }
        }
        val reportRecords: List<String> = sink.awaitRecords("<-- END HTTP", count = 1, timeoutMillis = 10_000)

        val tokenSink = RecordSink()
        val tokenClient = HttpClient(ClientCIO) {
            install(Logging) {
                logger = MessageLengthLimitingLogger(maxLength = maxLength, minLength = minLength, delegate = tokenSink)
                level = LogLevel.BODY
                format = LoggingFormat.OkHttp
            }
        }
        tokenClient.use {
            runBlocking {
                val token = it.get("$reportUrl/token")
                assertEquals(HttpStatusCode.OK, token.status)
                assertEquals(downloadToken, token.bodyAsText())
            }
        }
        val tokenRecords: List<String> = tokenSink.awaitRecords("<-- END HTTP", count = 1, timeoutMillis = 10_000)

        val reportLog: String = reportRecords.joinToString("\n----\n")
        assertTrue(reportRecords.all { it.length <= maxLength }, "a record exceeds $maxLength chars:\n$reportLog")
        assertEquals("--> GET $reportUrl", reportRecords.first().lines().first(), reportLog)
        assertEquals("--> END GET", reportRecords.first().lines().last(), "the short request is one record:\n$reportLog")
        assertTrue(reportRecords.size >= 1 + csvReport.length / maxLength, "the export is split:\n$reportLog")
        assertTrue(
            reportRecords.joinToString("\n").contains(csvReport),
            "the export is split on row boundaries, so rejoined records hold it whole:\n$reportLog",
        )
        val loggedLines: Set<String> = reportRecords.flatMap { it.lines() }.toSet()
        assertTrue(loggedLines.containsAll(csvRows), "every CSV row stays on one line:\n$reportLog")
        assertTrue(
            Regex("""<-- END HTTP \(\d+ms, ${csvReport.length}-byte body\)""").matches(reportRecords.last().lines().last()),
            reportLog,
        )

        val tokenLog: String = tokenRecords.joinToString("\n----\n")
        assertTrue(tokenRecords.all { it.length <= maxLength }, "a record exceeds $maxLength chars:\n$tokenLog")
        assertTrue(
            tokenRecords.count { it.length == maxLength && !it.contains('\n') } >= 1,
            "a line without breaks is cut at exactly $maxLength chars:\n$tokenLog",
        )
        assertTrue(
            tokenRecords.joinToString("").contains(downloadToken),
            "the token pieces add up to the whole token:\n$tokenLog",
        )
        assertTrue(tokenRecords.none { it.contains(downloadToken) }, "no single record holds the token:\n$tokenLog")
    }
}
