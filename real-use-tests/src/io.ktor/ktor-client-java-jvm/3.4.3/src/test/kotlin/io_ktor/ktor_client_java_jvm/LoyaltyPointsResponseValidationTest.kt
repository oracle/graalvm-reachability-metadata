/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_java_jvm

import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.RedirectResponseException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Response validation and failure mapping through expectSuccess and the HttpCallValidator plugin
 * over the Java engine, as a retail application calls a loyalty-points service and turns its
 * error statuses, frozen-account replies, and outages into domain exceptions.
 */
public class LoyaltyPointsResponseValidationTest {

    /** Base type of the failures the loyalty module reports to the rest of the application. */
    open class LoyaltyException(message: String, cause: Throwable?) : RuntimeException(message, cause)

    class MemberNotFoundException(val memberId: String, cause: Throwable) :
        LoyaltyException("No loyalty member $memberId", cause)

    class InsufficientPointsException(val detail: String, cause: Throwable) :
        LoyaltyException("Redemption refused: $detail", cause)

    class AccountFrozenException(val reason: String) : LoyaltyException("Account frozen: $reason", null)

    class LoyaltyUnavailableException(val url: String, cause: Throwable) :
        LoyaltyException("Loyalty service unreachable at $url", cause)

    /** The loyalty-points service, with balances held in memory. */
    class LoyaltyService : AutoCloseable {
        private val balances: ConcurrentHashMap<String, Int> = ConcurrentHashMap(mapOf("M-100" to 250, "M-200" to 40))

        private val server: EmbeddedServer<*, *> = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            routing {
                get("/members/{id}/points") {
                    val id: String = call.parameters["id"]!!
                    val points: Int? = balances[id]
                    when {
                        points == null -> call.respondText("member $id is not enrolled", status = HttpStatusCode.NotFound)
                        id == "M-200" -> {
                            call.response.header("X-Account-State", "frozen; chargeback review")
                            call.respondText(points.toString())
                        }
                        else -> call.respondText(points.toString())
                    }
                }
                post("/members/{id}/redemptions") {
                    val id: String = call.parameters["id"]!!
                    val requested: Int = call.receiveText().trim().toInt()
                    val points: Int = balances[id] ?: 0
                    if (requested > points) {
                        call.respondText("needs $requested, has $points", status = HttpStatusCode.Conflict)
                    } else {
                        balances[id] = points - requested
                        call.respondText((points - requested).toString())
                    }
                }
                get("/rewards") {
                    call.respondText("reward catalog is being rebuilt", status = HttpStatusCode.ServiceUnavailable)
                }
                get("/legacy/points") {
                    call.response.header(HttpHeaders.Location, "/members/M-100/points")
                    call.respondText("moved", status = HttpStatusCode.Found)
                }
            }
        }

        val baseUrl: String

        init {
            server.start(wait = false)
            val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
            baseUrl = "http://127.0.0.1:$port"
        }

        override fun close() {
            server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
        }
    }

    /** A client that fails on every non-2xx status and does not follow redirects. */
    private fun strictClient(baseUrl: String): HttpClient = HttpClient(Java) {
        expectSuccess = true
        followRedirects = false
        defaultRequest { url("$baseUrl/") }
    }

    /**
     * The client the loyalty module ships: it rejects frozen accounts on otherwise successful
     * replies and maps library and transport failures to the module's own exceptions.
     */
    private fun loyaltyClient(baseUrl: String, seenFailures: MutableList<String>): HttpClient = HttpClient(Java) {
        expectSuccess = true
        defaultRequest { url("$baseUrl/") }
        HttpResponseValidator {
            validateResponse { response: HttpResponse ->
                val state: String? = response.headers["X-Account-State"]
                if (response.status.value in 200..299 && state != null && state.startsWith("frozen")) {
                    throw AccountFrozenException(state.substringAfter(';').trim())
                }
            }
            handleResponseExceptionWithRequest { cause: Throwable, request: HttpRequest ->
                seenFailures += "${request.method.value} ${request.url.encodedPath}"
                val status: HttpStatusCode? = (cause as? ResponseException)?.response?.status
                when {
                    status == HttpStatusCode.NotFound ->
                        throw MemberNotFoundException(request.url.segments[1], cause)
                    status == HttpStatusCode.Conflict -> {
                        val detail: String = (cause as ResponseException).response.bodyAsText()
                        throw InsufficientPointsException(detail, cause)
                    }
                    cause !is LoyaltyException && cause !is ResponseException ->
                        throw LoyaltyUnavailableException(request.url.toString(), cause)
                }
            }
        }
    }

    @Test
    fun expectSuccessRaisesTheExceptionMatchingEachStatusClass(): Unit = runBlocking {
        LoyaltyService().use { service: LoyaltyService ->
            strictClient(service.baseUrl).use { client: HttpClient ->
                assertThat(client.get("members/M-100/points").bodyAsText()).isEqualTo("250")

                val notFound: ClientRequestException = expectFailure { client.get("members/M-404/points") }
                assertThat(notFound.response.status).isEqualTo(HttpStatusCode.NotFound)
                assertThat(notFound.message).contains("404 Not Found").contains("member M-404 is not enrolled")
                assertThat(notFound.response.bodyAsText()).isEqualTo("member M-404 is not enrolled")

                val outage: ServerResponseException = expectFailure { client.get("rewards") }
                assertThat(outage.response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)
                assertThat(outage.response.bodyAsText()).isEqualTo("reward catalog is being rebuilt")

                val moved: RedirectResponseException = expectFailure { client.get("legacy/points") }
                assertThat(moved.response.status).isEqualTo(HttpStatusCode.Found)
                assertThat(moved.response.headers[HttpHeaders.Location]).isEqualTo("/members/M-100/points")
            }
        }
    }

    @Test
    fun aRequestCanOptOutOfStatusValidation(): Unit = runBlocking {
        LoyaltyService().use { service: LoyaltyService ->
            strictClient(service.baseUrl).use { client: HttpClient ->
                val lookup: HttpResponse = client.get("members/M-404/points") { expectSuccess = false }
                assertThat(lookup.status).isEqualTo(HttpStatusCode.NotFound)
                assertThat(lookup.bodyAsText()).isEqualTo("member M-404 is not enrolled")
            }
        }
    }

    @Test
    fun theLoyaltyClientMapsFailuresToDomainExceptions(): Unit = runBlocking {
        LoyaltyService().use { service: LoyaltyService ->
            val seenFailures: MutableList<String> = CopyOnWriteArrayList()
            loyaltyClient(service.baseUrl, seenFailures).use { client: HttpClient ->
                val remaining: String = client.post("members/M-100/redemptions") { setBody("100") }.bodyAsText()
                assertThat(remaining).isEqualTo("150")

                val missing: MemberNotFoundException = expectFailure { client.get("members/M-404/points") }
                assertThat(missing.memberId).isEqualTo("M-404")
                assertThat(missing.cause).isInstanceOf(ClientRequestException::class.java)

                val refused: InsufficientPointsException = expectFailure {
                    client.post("members/M-100/redemptions") { setBody("500") }
                }
                assertThat(refused.detail).isEqualTo("needs 500, has 150")

                val frozen: AccountFrozenException = expectFailure { client.get("members/M-200/points") }
                assertThat(frozen.reason).isEqualTo("chargeback review")

                val outage: ServerResponseException = expectFailure { client.get("rewards") }
                assertThat(outage.response.status).isEqualTo(HttpStatusCode.ServiceUnavailable)

                assertThat(seenFailures).containsExactly(
                    "GET /members/M-404/points",
                    "POST /members/M-100/redemptions",
                    "GET /members/M-200/points",
                    "GET /rewards",
                )
            }
        }
    }

    @Test
    fun anUnreachableServiceSurfacesAsAnOutage(): Unit = runBlocking {
        val closedPort: Int = ServerSocket(0).use { socket: ServerSocket -> socket.localPort }
        val seenFailures: MutableList<String> = CopyOnWriteArrayList()
        loyaltyClient("http://127.0.0.1:$closedPort", seenFailures).use { client: HttpClient ->
            val outage: LoyaltyUnavailableException = expectFailure { client.get("members/M-100/points") }
            assertThat(outage.url).isEqualTo("http://127.0.0.1:$closedPort/members/M-100/points")
            assertThat(outage.cause).isNotInstanceOf(ResponseException::class.java)
            assertThat(seenFailures).containsExactly("GET /members/M-100/points")
        }
    }

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (failure: Throwable) {
            assertThat(failure).isInstanceOf(T::class.java)
            return failure as T
        }
        throw AssertionError("Expected ${T::class.simpleName} but the call succeeded")
    }
}
