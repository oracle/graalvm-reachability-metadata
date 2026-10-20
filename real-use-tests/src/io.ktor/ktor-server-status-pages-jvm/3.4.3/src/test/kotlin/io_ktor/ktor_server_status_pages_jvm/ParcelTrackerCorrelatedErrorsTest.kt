/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_status_pages_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.util.AttributeKey
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Error responses that carry the failing call's request context, where a parcel
 * tracker stamps every error document with the trace and operation of the call.
 *
 * A tracing plugin stores a trace id in the call attributes and echoes it as a
 * response header before routing; routes record the operation they run. Exception,
 * status, and fallback handlers read those attributes, the request path, method,
 * query, and headers back from the same call, and the trace header survives into
 * their responses.
 */
public class ParcelTrackerCorrelatedErrorsTest {
    /** A parcel lookup for a tracking number the tracker has never seen. */
    private class UnknownParcelException(val trackingNumber: String) : RuntimeException("unknown parcel")

    /** A carrier refusing the call; the tracker reports which carrier refused. */
    private class CarrierRefusedException(val carrier: String, reason: String) : RuntimeException(reason)

    /** One error document the tracker answered, as its incident log keeps it. */
    private data class Incident(val traceId: String, val operation: String?, val status: Int, val endpoint: String)

    /** The parcel tracker, with every error document it sends appended to [incidents]. */
    private fun ApplicationTestBuilder.parcelTracker(incidents: MutableList<Incident>) {
        application {
            val generatedTraces: AtomicInteger = AtomicInteger()
            val requestTracing = createApplicationPlugin("RequestTracing") {
                onCall { call ->
                    val traceId: String = call.request.header(TRACE_HEADER)
                        ?: "trace-${generatedTraces.incrementAndGet()}"
                    call.attributes.put(TRACE_ID, traceId)
                    call.response.headers.append(TRACE_HEADER, traceId)
                }
            }
            install(requestTracing)
            install(StatusPages) {
                exception<UnknownParcelException> { call, cause ->
                    call.respondProblem(
                        incidents,
                        HttpStatusCode.NotFound,
                        "unknown-parcel",
                        "No parcel with tracking number ${cause.trackingNumber}",
                    )
                }
                exception<CarrierRefusedException> { call, cause ->
                    val tenant: String = call.request.header(TENANT_HEADER) ?: "anonymous"
                    call.respondProblem(
                        incidents,
                        HttpStatusCode.BadGateway,
                        "carrier-refused",
                        "${cause.carrier} refused $tenant: ${cause.message}",
                    )
                }
                status(HttpStatusCode.Forbidden) { call, status ->
                    call.respondProblem(incidents, status, "forbidden", "Parcel belongs to another account")
                }
                unhandled { call ->
                    call.respondProblem(
                        incidents,
                        HttpStatusCode.NotFound,
                        "no-such-endpoint",
                        "Nothing serves this path",
                    )
                }
            }
            routing {
                route("/parcels/{trackingNumber}") {
                    get {
                        call.attributes.put(OPERATION, "track-parcel")
                        val trackingNumber: String = call.parameters["trackingNumber"]!!
                        val status: String = PARCELS[trackingNumber] ?: throw UnknownParcelException(trackingNumber)
                        call.respondText("$trackingNumber: $status")
                    }
                    post("/reroute") {
                        call.attributes.put(OPERATION, "reroute-parcel")
                        val carrier: String = call.request.queryParameters["carrier"] ?: "PostNord"
                        throw CarrierRefusedException(carrier, "reroute window closed")
                    }
                    get("/signature") {
                        call.attributes.put(OPERATION, "fetch-signature")
                        call.respond(HttpStatusCode.Forbidden)
                    }
                }
            }
        }
    }

    @Test
    fun successfulCallsCarryTheTraceHeaderOnly() = testApplication {
        val incidents: MutableList<Incident> = mutableListOf()
        parcelTracker(incidents)

        val response: HttpResponse = client.get("/parcels/PX100") { header(TRACE_HEADER, "edge-7f3a") }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("PX100: out for delivery", response.bodyAsText())
        assertEquals("edge-7f3a", response.headers[TRACE_HEADER])
        assertEquals(emptyList<Incident>(), incidents)
    }

    @Test
    fun exceptionHandlerStampsTheCallersTraceAndOperation() = testApplication {
        val incidents: MutableList<Incident> = mutableListOf()
        parcelTracker(incidents)

        val response: HttpResponse = client.get("/parcels/ZZ999") { header(TRACE_HEADER, "edge-91bc") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("edge-91bc", response.headers[TRACE_HEADER])
        assertEquals(
            """{"error":"unknown-parcel","detail":"No parcel with tracking number ZZ999",""" +
                """"traceId":"edge-91bc","operation":"track-parcel","endpoint":"GET /parcels/ZZ999"}""",
            response.bodyAsText(),
        )
        assertEquals(listOf(Incident("edge-91bc", "track-parcel", 404, "GET /parcels/ZZ999")), incidents)
    }

    @Test
    fun exceptionHandlerReadsQueryAndRequestHeadersOfTheFailedCall() = testApplication {
        val incidents: MutableList<Incident> = mutableListOf()
        parcelTracker(incidents)

        val refused: HttpResponse = client.post("/parcels/PX100/reroute?carrier=DHL") {
            header(TENANT_HEADER, "acme-retail")
        }
        val refusedAnonymously: HttpResponse = client.post("/parcels/PX204/reroute")

        assertEquals(HttpStatusCode.BadGateway, refused.status)
        assertEquals("trace-1", refused.headers[TRACE_HEADER])
        assertEquals(
            """{"error":"carrier-refused","detail":"DHL refused acme-retail: reroute window closed",""" +
                """"traceId":"trace-1","operation":"reroute-parcel","endpoint":"POST /parcels/PX100/reroute"}""",
            refused.bodyAsText(),
        )
        assertEquals(HttpStatusCode.BadGateway, refusedAnonymously.status)
        assertEquals("trace-2", refusedAnonymously.headers[TRACE_HEADER])
        assertEquals(
            """{"error":"carrier-refused","detail":"PostNord refused anonymous: reroute window closed",""" +
                """"traceId":"trace-2","operation":"reroute-parcel","endpoint":"POST /parcels/PX204/reroute"}""",
            refusedAnonymously.bodyAsText(),
        )
        assertEquals(
            listOf(
                Incident("trace-1", "reroute-parcel", 502, "POST /parcels/PX100/reroute"),
                Incident("trace-2", "reroute-parcel", 502, "POST /parcels/PX204/reroute"),
            ),
            incidents,
        )
    }

    @Test
    fun statusHandlerSeesTheContextOfTheCallThatRespondedWithTheStatus() = testApplication {
        val incidents: MutableList<Incident> = mutableListOf()
        parcelTracker(incidents)

        val response: HttpResponse = client.get("/parcels/PX100/signature") { header(TRACE_HEADER, "edge-0042") }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("edge-0042", response.headers[TRACE_HEADER])
        assertEquals(
            """{"error":"forbidden","detail":"Parcel belongs to another account",""" +
                """"traceId":"edge-0042","operation":"fetch-signature","endpoint":"GET /parcels/PX100/signature"}""",
            response.bodyAsText(),
        )
        assertEquals(listOf(Incident("edge-0042", "fetch-signature", 403, "GET /parcels/PX100/signature")), incidents)
    }

    @Test
    fun unhandledCallsKeepTheTraceButReportNoOperation() = testApplication {
        val incidents: MutableList<Incident> = mutableListOf()
        parcelTracker(incidents)

        // No route matches, so no route records an operation before the fallback answers.
        val response: HttpResponse = client.get("/warehouses/oslo") { header(TRACE_HEADER, "edge-dead") }

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("edge-dead", response.headers[TRACE_HEADER])
        assertEquals(
            """{"error":"no-such-endpoint","detail":"Nothing serves this path",""" +
                """"traceId":"edge-dead","operation":"none","endpoint":"GET /warehouses/oslo"}""",
            response.bodyAsText(),
        )
        assertEquals(listOf(Incident("edge-dead", null, 404, "GET /warehouses/oslo")), incidents)
    }

    private companion object {
        const val TRACE_HEADER: String = "X-Request-Id"

        const val TENANT_HEADER: String = "X-Tenant"

        val TRACE_ID: AttributeKey<String> = AttributeKey("TraceId")

        val OPERATION: AttributeKey<String> = AttributeKey("Operation")

        val PARCELS: Map<String, String> = mapOf("PX100" to "out for delivery", "PX204" to "in transit")

        /** Answers [status] with a problem document stamped with the call's trace and operation. */
        suspend fun ApplicationCall.respondProblem(
            incidents: MutableList<Incident>,
            status: HttpStatusCode,
            error: String,
            detail: String,
        ) {
            val traceId: String = attributes[TRACE_ID]
            val operation: String? = attributes.getOrNull(OPERATION)
            val endpoint: String = "${request.httpMethod.value} ${request.path()}"
            incidents += Incident(traceId, operation, status.value, endpoint)
            respondText(
                """{"error":"$error","detail":"$detail","traceId":"$traceId",""" +
                    """"operation":"${operation ?: "none"}","endpoint":"$endpoint"}""",
                ContentType.Application.Json,
                status,
            )
        }
    }
}
