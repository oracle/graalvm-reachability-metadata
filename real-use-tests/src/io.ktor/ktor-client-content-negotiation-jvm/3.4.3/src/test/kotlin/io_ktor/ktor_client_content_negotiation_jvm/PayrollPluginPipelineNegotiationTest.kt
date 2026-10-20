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
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Composition of the client ContentNegotiation plugin with the other plugins of the request
 * pipeline, as a payroll application submits payslip batches: a default content type from
 * defaultRequest drives the converter, HttpRequestRetry resends the converted body, an HttpSend
 * interceptor observes the serialized body, a response validator decodes rejection bodies, and a
 * saved response is decoded both as a typed object and as text.
 */
public class PayrollPluginPipelineNegotiationTest {

    @Serializable
    data class Payslip(val employeeId: String, val grossCents: Long, val deductions: Map<String, Long>)

    @Serializable
    data class PayslipBatch(val period: String, val payslips: List<Payslip>)

    @Serializable
    data class BatchReceipt(val batchId: String, val accepted: Int)

    @Serializable
    data class Rejection(val code: String, val employeeIds: List<String>)

    /** Raised by the application's response validator with the decoded rejection body. */
    class PayrollRejectedException(val rejection: Rejection) : RuntimeException("payroll rejected: ${rejection.code}")

    /** What the payroll service saw of one incoming request. */
    data class ReceivedRequest(val attempt: String?, val accept: String?, val contentType: String, val body: String)

    /** The payroll service; it fails a configurable number of submissions before accepting one. */
    class PayrollService(private val failuresBeforeSuccess: Int) : AutoCloseable {
        val received: MutableList<ReceivedRequest> = CopyOnWriteArrayList()
        private val submissions: AtomicInteger = AtomicInteger()

        private val server: EmbeddedServer<*, *> = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/batches") {
                    val body: String = call.receiveText()
                    received += ReceivedRequest(
                        call.request.header("X-Attempt"),
                        call.request.header(HttpHeaders.Accept),
                        call.request.contentType().toString(),
                        body,
                    )
                    if (submissions.incrementAndGet() <= failuresBeforeSuccess) {
                        call.respondText("ledger busy", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
                    } else if (body.contains("\"grossCents\":-")) {
                        call.respondText(
                            """{"code":"NEGATIVE_GROSS","employeeIds":["E-204"]}""",
                            ContentType.Application.Json,
                            HttpStatusCode.UnprocessableEntity,
                        )
                    } else {
                        call.respondText(
                            """{"batchId":"B-2026-09","accepted":2}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Created,
                        )
                    }
                }
                get("/payslips/E-101") {
                    call.respondText(
                        """{"employeeId":"E-101","grossCents":512000,"deductions":{"tax":98000,"pension":25600}}""",
                        ContentType.Application.Json,
                    )
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

    private val september: PayslipBatch = PayslipBatch(
        "2026-09",
        listOf(
            Payslip("E-101", 512_000, mapOf("tax" to 98_000, "pension" to 25_600)),
            Payslip("E-204", 388_500, mapOf("tax" to 61_200)),
        ),
    )

    /** The payroll client: JSON by default, retried on server errors, validated on rejections. */
    private fun payrollClient(baseUrl: String): HttpClient = HttpClient(ClientCIO) {
        install(ContentNegotiation) {
            json(Json { encodeDefaults = true })
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            constantDelay(millis = 1, randomizationMs = 0)
            modifyRequest { request -> request.headers["X-Attempt"] = (retryCount + 1).toString() }
        }
        HttpResponseValidator {
            validateResponse { response: HttpResponse ->
                if (response.status == HttpStatusCode.UnprocessableEntity) {
                    throw PayrollRejectedException(response.body())
                }
            }
        }
        defaultRequest {
            url("$baseUrl/")
            contentType(ContentType.Application.Json)
        }
    }

    @Test
    fun aRetriedSubmissionResendsTheSameConvertedBody(): Unit = runBlocking {
        PayrollService(failuresBeforeSuccess = 2).use { service: PayrollService ->
            payrollClient(service.baseUrl).use { client: HttpClient ->
                val receipt: BatchReceipt = client.post("batches") {
                    header("X-Attempt", "1")
                    setBody(september)
                }.body()

                assertThat(receipt).isEqualTo(BatchReceipt("B-2026-09", 2))
                assertThat(service.received.map { request: ReceivedRequest -> request.attempt })
                    .containsExactly("1", "2", "3")
                assertThat(service.received.map { request: ReceivedRequest -> request.body }.distinct()).hasSize(1)
                service.received.forEach { request: ReceivedRequest ->
                    assertThat(request.contentType).startsWith("application/json")
                    assertThat(request.accept).contains("application/json")
                    assertThat(Json.decodeFromString(PayslipBatch.serializer(), request.body)).isEqualTo(september)
                }
            }
        }
    }

    @Test
    fun aSendInterceptorObservesTheSerializedBody(): Unit = runBlocking {
        PayrollService(failuresBeforeSuccess = 0).use { service: PayrollService ->
            payrollClient(service.baseUrl).use { client: HttpClient ->
                val observed: MutableList<OutgoingContent> = CopyOnWriteArrayList()
                client.plugin(HttpSend).intercept { request ->
                    observed += request.body as OutgoingContent
                    request.headers["X-Attempt"] = "audited"
                    execute(request)
                }

                val receipt: BatchReceipt = client.post("batches") { setBody(september) }.body()

                assertThat(receipt.accepted).isEqualTo(2)
                val content: TextContent = observed.single() as TextContent
                assertThat(content.contentType.match(ContentType.Application.Json)).isTrue()
                assertThat(Json.decodeFromString(PayslipBatch.serializer(), content.text)).isEqualTo(september)
                assertThat(service.received.single().attempt).isEqualTo("audited")
                assertThat(service.received.single().body).isEqualTo(content.text)
            }
        }
    }

    @Test
    fun aRejectionBodyIsDecodedByTheResponseValidator(): Unit = runBlocking {
        PayrollService(failuresBeforeSuccess = 0).use { service: PayrollService ->
            payrollClient(service.baseUrl).use { client: HttpClient ->
                val invalid: PayslipBatch = september.copy(
                    payslips = listOf(Payslip("E-204", -1_000, emptyMap())),
                )

                assertThatThrownBy {
                    runBlocking { client.post("batches") { setBody(invalid) } }
                }
                    .isInstanceOf(PayrollRejectedException::class.java)
                    .satisfies({ error: Throwable ->
                        assertThat((error as PayrollRejectedException).rejection)
                            .isEqualTo(Rejection("NEGATIVE_GROSS", listOf("E-204")))
                    })
                assertThat(service.received.single().body).contains("\"deductions\":{}")
            }
        }
    }

    @Test
    fun aSavedResponseIsDecodedAsATypedObjectAndAsText(): Unit = runBlocking {
        PayrollService(failuresBeforeSuccess = 0).use { service: PayrollService ->
            payrollClient(service.baseUrl).use { client: HttpClient ->
                val response: HttpResponse = client.get("payslips/E-101")

                val payslip: Payslip = response.body()
                val tree: JsonObject = response.body()
                val raw: String = response.bodyAsText()

                assertThat(payslip).isEqualTo(september.payslips.first())
                assertThat(tree.getValue("employeeId").jsonPrimitive.content).isEqualTo("E-101")
                assertThat(raw).isEqualTo(
                    """{"employeeId":"E-101","grossCents":512000,"deductions":{"tax":98000,"pension":25600}}""",
                )
            }
        }
    }
}
