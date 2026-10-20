/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.json.json
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Negotiation boundaries of the client ContentNegotiation plugin: `+json` suffix media types matched by the
 * JSON converter, the ignored types that bypass conversion (and removing `String` from them), and the
 * failures an application sees when a response has no matching converter or carries malformed JSON, as a
 * banking front end talks to an account API that answers with vendor JSON and RFC 7807 problem documents.
 */
public class AccountApiNegotiationBoundariesTest {

    @Serializable
    data class Account(val id: String, val owner: String, val balanceCents: Long)

    /** An RFC 7807 problem document, as the account API sends for failed requests. */
    @Serializable
    data class Problem(val type: String, val title: String, val status: Int, val detail: String? = null)

    /** What the account API saw of one incoming request. */
    data class ReceivedRequest(val path: String, val accept: String?, val contentType: String?, val body: String)

    /** The account API; it answers with fixed payloads so the client's negotiation is what is checked. */
    class AccountApi : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/accounts/{id}") {
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), null, "")
                    if (call.parameters["id"] == "acc-1") {
                        call.respondText(
                            """{"id":"acc-1","owner":"Ana Petrovic","balanceCents":125050,"branch":"Novi Sad"}""",
                            ContentType("application", "vnd.ledger.account+json"),
                        )
                    } else {
                        call.respondText(
                            """
                            {"type":"https://ledger.example/problems/unknown-account","title":"Account not found",
                             "status":404,"detail":"No account ${call.parameters["id"]}"}
                            """.trimIndent(),
                            ContentType("application", "problem+json"),
                            HttpStatusCode.NotFound,
                        )
                    }
                }
                get("/accounts/acc-1/statement") {
                    call.respondText("acc-1;2026-10-01;+125050", ContentType.Text.Plain)
                }
                get("/accounts/acc-2") {
                    call.respondText("""{"id":"acc-2","owner":""", ContentType.Application.Json)
                }
                get("/motd") {
                    call.respondText("\"Ledger closes at 18:00\"", ContentType.Application.Json)
                }
                post("/notes") {
                    val body: String = call.receiveText()
                    record(call.request.local.uri, call.request.header(HttpHeaders.Accept), call.request.contentType().toString(), body)
                    call.respondText("{}", ContentType.Application.Json, HttpStatusCode.Accepted)
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

    private val ledgerJson: Json = Json { ignoreUnknownKeys = true }

    private fun accountClient(baseUrl: String, configure: ContentNegotiationConfig.() -> Unit = {}): HttpClient =
        HttpClient(ClientCIO) {
            install(ContentNegotiation) {
                json(ledgerJson)
                configure()
            }
            defaultRequest { url("$baseUrl/") }
        }

    @Test
    fun vendorJsonAccountIsDecodedThroughTheJsonSuffixMatcher(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("accounts/acc-1")

                assertThat(response.headers[HttpHeaders.ContentType]).startsWith("application/vnd.ledger.account+json")
                assertThat(response.body<Account>()).isEqualTo(Account("acc-1", "Ana Petrovic", 125050))
                assertThat(api.received.single().accept).contains("application/json")
            }
        }
    }

    @Test
    fun problemDocumentOfAFailedLookupIsDecodedIntoATypedProblem(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("accounts/acc-9")

                assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
                val problem: Problem = response.body()
                assertThat(problem).isEqualTo(
                    Problem(
                        type = "https://ledger.example/problems/unknown-account",
                        title = "Account not found",
                        status = 404,
                        detail = "No account acc-9",
                    ),
                )
            }
        }
    }

    @Test
    fun ignoredTypesReceiveTheRawPayloadOfAJsonResponse(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val text: String = client.get("accounts/acc-1").body()
                val bytes: ByteArray = client.get("accounts/acc-1").body()
                val motd: String = client.get("motd").body()

                assertThat(text).isEqualTo("""{"id":"acc-1","owner":"Ana Petrovic","balanceCents":125050,"branch":"Novi Sad"}""")
                assertThat(bytes.decodeToString()).isEqualTo(text)
                assertThat(motd).isEqualTo("\"Ledger closes at 18:00\"")
            }
        }
    }

    @Test
    fun aStringSetAsJsonBodyIsSentVerbatimByDefault(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.post("notes") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"account":"acc-1","note":"VIP"}""")
                }

                assertThat(response.status).isEqualTo(HttpStatusCode.Accepted)
                val seen: ReceivedRequest = api.received.single()
                assertThat(seen.contentType).startsWith("application/json")
                assertThat(seen.body).isEqualTo("""{"account":"acc-1","note":"VIP"}""")
            }
        }
    }

    @Test
    fun removingStringFromIgnoredTypesMakesStringsJsonLiterals(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl) { removeIgnoredType<String>() }.use { client: HttpClient ->
                client.post("notes") {
                    contentType(ContentType.Application.Json)
                    setBody("Call back about \"overdraft\"")
                }
                val motd: String = client.get("motd").body()

                assertThat(api.received.single().body).isEqualTo("\"Call back about \\\"overdraft\\\"\"")
                assertThat(motd).isEqualTo("Ledger closes at 18:00")
            }
        }
    }

    @Test
    fun aResponseWithoutAMatchingConverterCannotBecomeATypedBody(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val typed: HttpResponse = client.get("accounts/acc-1/statement")
                assertThatThrownBy { runBlocking { typed.body<Account>() } }
                    .isInstanceOf(NoTransformationFoundException::class.java)

                val statement: String = client.get("accounts/acc-1/statement").body()
                assertThat(statement).isEqualTo("acc-1;2026-10-01;+125050")
            }
        }
    }

    @Test
    fun malformedJsonSurfacesAsAJsonConvertException(): Unit = runBlocking {
        AccountApi().use { api: AccountApi ->
            accountClient(api.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("accounts/acc-2")

                assertThat(response.status).isEqualTo(HttpStatusCode.OK)
                assertThatThrownBy { runBlocking { response.body<Account>() } }
                    .isInstanceOf(JsonConvertException::class.java)
            }
        }
    }
}
