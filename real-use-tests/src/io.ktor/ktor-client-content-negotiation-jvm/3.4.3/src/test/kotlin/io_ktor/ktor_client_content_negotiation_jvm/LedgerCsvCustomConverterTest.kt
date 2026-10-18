/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.contentnegotiation.exclude
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.ContentTypeMatcher
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registration of an application-written ContentConverter with a custom ContentTypeMatcher in the
 * client ContentNegotiation plugin, as a bookkeeping application exchanges CSV ledgers with an
 * accounting service and controls which formats each request advertises in its Accept header.
 */
public class LedgerCsvCustomConverterTest {

    data class LedgerEntry(val date: LocalDate, val account: String, val amount: BigDecimal, val memo: String)

    data class Ledger(val entries: List<LedgerEntry>) {
        val total: BigDecimal
            get() = entries.fold(BigDecimal.ZERO) { sum: BigDecimal, entry: LedgerEntry -> sum + entry.amount }
    }

    /** The application's CSV format for ledgers; it declines every other type so other converters can run. */
    class LedgerCsvConverter : ContentConverter {
        var encoded: Int = 0
            private set
        var decoded: Int = 0
            private set

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): OutgoingContent? {
            if (value !is Ledger) {
                return null
            }
            encoded++
            val rows: List<String> = value.entries.map { entry: LedgerEntry ->
                listOf(entry.date.toString(), entry.account, entry.amount.toPlainString(), quote(entry.memo)).joinToString(",")
            }
            return TextContent((listOf(HEADER) + rows).joinToString("\r\n"), contentType.withCharset(charset))
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
            if (typeInfo.type != Ledger::class) {
                return null
            }
            decoded++
            val lines: List<String> = String(content.toByteArray(), charset).lines().filter { line: String -> line.isNotBlank() }
            assertThat(lines.first()).isEqualTo(HEADER)
            return Ledger(lines.drop(1).map { line: String -> parseRow(line) })
        }

        private fun quote(text: String): String = "\"" + text.replace("\"", "\"\"") + "\""

        private fun parseRow(line: String): LedgerEntry {
            val date: String = line.substringBefore(',')
            val afterDate: String = line.substringAfter(',')
            val account: String = afterDate.substringBefore(',')
            val afterAccount: String = afterDate.substringAfter(',')
            val amount: String = afterAccount.substringBefore(',')
            val memo: String = afterAccount.substringAfter(',').removeSurrounding("\"").replace("\"\"", "\"")
            return LedgerEntry(LocalDate.parse(date), account, BigDecimal(amount), memo)
        }

        companion object {
            const val HEADER: String = "date,account,amount,memo"
        }
    }

    /** Accepts plain CSV and every vendor `+csv` subtype the accounting service uses. */
    object CsvFamilyMatcher : ContentTypeMatcher {
        override fun contains(contentType: ContentType): Boolean {
            val bare: ContentType = contentType.withoutParameters()
            return bare.match(ContentType.Text.CSV) || bare.contentSubtype.endsWith("+csv")
        }
    }

    /** What the accounting service saw of one incoming request. */
    data class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /** The accounting service; it answers with fixed CSV so the client's decoding is what is checked. */
    class AccountingService : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/ledgers/2026-05") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(
                        "date,account,amount,memo\r\n" +
                            "2026-05-02,4000-sales,1250.00,\"Invoice 1041, paid\"\r\n" +
                            "2026-05-09,6100-rent,-900.00,\"May rent\"\r\n" +
                            "2026-05-15,6200-travel,-87.40,\"Train to \"\"HQ\"\"\"\r\n",
                        ContentType.Text.CSV.withCharset(Charsets.UTF_8),
                    )
                }
                get("/ledgers/2026-05/adjustments") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText(
                        "date,account,amount,memo\n2026-05-31,6900-fx,-3.15,\"Currency rounding\"\n",
                        LEDGER_CSV,
                    )
                }
                post("/ledgers/2026-06") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText("stored ${body.lines().size - 1} entries", ContentType.Text.Plain, HttpStatusCode.Created)
                }
                get("/ledgers/2026-05/summary") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    call.respondText("3 entries, net 262.60", ContentType.Text.Plain)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        private fun record(path: String, accept: String?, contentType: String?, body: String) {
            received += ReceivedRequest(path, accept, contentType, body)
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    private fun accountingClient(baseUrl: String, converter: LedgerCsvConverter): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            defaultAcceptHeaderQValue = 0.8
            register(ContentType.Text.CSV, converter, CsvFamilyMatcher) {}
        }
        defaultRequest { url("$baseUrl/") }
    }

    @Test
    fun aCsvLedgerIsDecodedByTheApplicationConverter(): Unit = runBlocking {
        val converter = LedgerCsvConverter()
        AccountingService().use { service: AccountingService ->
            accountingClient(service.baseUrl, converter).use { client: HttpClient ->
                val ledger: Ledger = client.get("ledgers/2026-05").body()

                assertThat(ledger.entries).containsExactly(
                    LedgerEntry(LocalDate.of(2026, 5, 2), "4000-sales", BigDecimal("1250.00"), "Invoice 1041, paid"),
                    LedgerEntry(LocalDate.of(2026, 5, 9), "6100-rent", BigDecimal("-900.00"), "May rent"),
                    LedgerEntry(LocalDate.of(2026, 5, 15), "6200-travel", BigDecimal("-87.40"), "Train to \"HQ\""),
                )
                assertThat(ledger.total).isEqualByComparingTo("262.60")
                assertThat(converter.decoded).isEqualTo(1)

                val accept: String? = service.received.single().accept
                assertThat(accept).contains("text/csv").contains("q=0.8")
            }
        }
    }

    @Test
    fun aVendorCsvReplyIsRoutedToTheConverterByTheMatcher(): Unit = runBlocking {
        val converter = LedgerCsvConverter()
        AccountingService().use { service: AccountingService ->
            accountingClient(service.baseUrl, converter).use { client: HttpClient ->
                val response: HttpResponse = client.get("ledgers/2026-05/adjustments")
                assertThat(response.contentType()?.withoutParameters()).isEqualTo(LEDGER_CSV)

                val adjustments: Ledger = response.body()
                assertThat(adjustments.entries.single())
                    .isEqualTo(LedgerEntry(LocalDate.of(2026, 5, 31), "6900-fx", BigDecimal("-3.15"), "Currency rounding"))
                assertThat(converter.decoded).isEqualTo(1)
            }
        }
    }

    @Test
    fun aLedgerBodyIsEncodedAsCsvWithTheRequestedCharset(): Unit = runBlocking {
        val converter = LedgerCsvConverter()
        AccountingService().use { service: AccountingService ->
            accountingClient(service.baseUrl, converter).use { client: HttpClient ->
                val june = Ledger(
                    listOf(
                        LedgerEntry(LocalDate.of(2026, 6, 1), "4000-sales", BigDecimal("410.25"), "Caf\u00e9 \"Nord\" order"),
                        LedgerEntry(LocalDate.of(2026, 6, 3), "6100-rent", BigDecimal("-900.00"), "June rent"),
                    ),
                )
                val response: HttpResponse = client.post("ledgers/2026-06") {
                    contentType(ContentType.Text.CSV.withCharset(Charsets.UTF_8))
                    setBody(june)
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Created)
                assertThat(response.body<String>()).isEqualTo("stored 2 entries")
                assertThat(converter.encoded).isEqualTo(1)

                val seen: ReceivedRequest = service.received.single()
                assertThat(seen.contentType).startsWith("text/csv").contains("charset=UTF-8")
                assertThat(seen.body.split("\r\n")).containsExactly(
                    LedgerCsvConverter.HEADER,
                    "2026-06-01,4000-sales,410.25,\"Caf\u00e9 \"\"Nord\"\" order\"",
                    "2026-06-03,6100-rent,-900.00,\"June rent\"",
                )
            }
        }
    }

    @Test
    fun anExcludedFormatIsNotAdvertisedAndPlainTextIsLeftAlone(): Unit = runBlocking {
        val converter = LedgerCsvConverter()
        AccountingService().use { service: AccountingService ->
            accountingClient(service.baseUrl, converter).use { client: HttpClient ->
                val summary: String = client.get("ledgers/2026-05/summary") {
                    exclude(ContentType.Text.CSV)
                }.body()

                assertThat(summary).isEqualTo("3 entries, net 262.60")
                assertThat(service.received.single().accept.orEmpty()).doesNotContain("text/csv")
                assertThat(converter.decoded).isZero()
            }
        }
    }

    private companion object {
        val LEDGER_CSV: ContentType = ContentType("application", "vnd.ledger+csv")
    }
}
