/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_serialization_kotlinx_json_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondNullable
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Converts nullable top-level bodies, where JSON `null` is a meaningful value, through the
 * kotlinx converter that `json()` registers on server and client content negotiation.
 *
 * A parcel locker bank answers "who occupies this compartment" with an occupant object or a
 * literal `null` for an empty one, and the courier app clears a compartment by sending `null`
 * as the whole request body. The server reads those bodies with `receiveNullable` and answers
 * with `respondNullable`; the app reads them with `body<T?>()`. An endpoint that requires an
 * occupant answers 400 when it is handed `null`.
 */
public class ParcelLockerNullableBodyTest {

    /** Who holds a compartment and the pickup code they were given. */
    @Serializable
    private data class Occupant(val parcelId: String, val recipient: String, val pickupCode: Int)

    /** The bank's answer after a compartment changed hands. */
    @Serializable
    private data class Assignment(val compartment: String, val occupant: Occupant?)

    private val occupants: MutableMap<String, Occupant> = ConcurrentHashMap()

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var courierApp: HttpClient
    private lateinit var raw: HttpClient
    private var port: Int = 0

    @BeforeEach
    fun startServer() {
        occupants["A1"] = Occupant(parcelId = "PX-1001", recipient = "Mila", pickupCode = 482913)
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
            install(ServerContentNegotiation) {
                json()
            }
            routing {
                get("/compartments/{id}/occupant") {
                    val occupant: Occupant? = occupants[call.parameters["id"]!!]
                    call.respondNullable(occupant)
                }
                put("/compartments/{id}/occupant") {
                    val id: String = call.parameters["id"]!!
                    val incoming: Occupant? = call.receiveNullable<Occupant?>()
                    if (incoming == null) occupants.remove(id) else occupants[id] = incoming
                    call.respond(Assignment(compartment = id, occupant = incoming))
                }
                put("/compartments/{id}/reservation") {
                    val id: String = call.parameters["id"]!!
                    val reserved: Occupant = call.receive<Occupant>()
                    occupants[id] = reserved
                    call.respond(HttpStatusCode.Created, Assignment(compartment = id, occupant = reserved))
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
        courierApp = HttpClient(ClientCIO) {
            install(ClientContentNegotiation) {
                json()
            }
        }
        raw = HttpClient(ClientCIO)
    }

    @AfterEach
    fun stopServer() {
        courierApp.close()
        raw.close()
        server.stop(0, 0)
    }

    private fun url(path: String): String = "http://127.0.0.1:$port$path"

    @Test
    fun occupiedCompartmentAnswersWithItsOccupant(): Unit = runBlocking {
        val response: HttpResponse = courierApp.get(url("/compartments/A1/occupant"))

        assertEquals(HttpStatusCode.OK, response.status)
        val occupant: Occupant? = response.body<Occupant?>()
        assertEquals(Occupant(parcelId = "PX-1001", recipient = "Mila", pickupCode = 482913), occupant)
    }

    @Test
    fun emptyCompartmentAnswersWithJsonNull(): Unit = runBlocking {
        val wire: HttpResponse = raw.get(url("/compartments/B7/occupant"))
        assertEquals(HttpStatusCode.OK, wire.status)
        assertEquals("null", wire.bodyAsText().trim())

        val occupant: Occupant? = courierApp.get(url("/compartments/B7/occupant")).body<Occupant?>()
        assertNull(occupant)
    }

    @Test
    fun courierFillsAndThenClearsCompartmentWithNullBody(): Unit = runBlocking {
        val parcel = Occupant(parcelId = "PX-2002", recipient = "Ivo", pickupCode = 105577)

        val filled: Assignment = courierApp.put(url("/compartments/C3/occupant")) {
            contentType(ContentType.Application.Json)
            setBody<Occupant?>(parcel)
        }.body()
        assertEquals(Assignment(compartment = "C3", occupant = parcel), filled)
        assertEquals(parcel, occupants["C3"])

        val cleared: HttpResponse = courierApp.put(url("/compartments/C3/occupant")) {
            contentType(ContentType.Application.Json)
            setBody<Occupant?>(null)
        }
        assertEquals(HttpStatusCode.OK, cleared.status)
        val wire = Json.parseToJsonElement(cleared.bodyAsText()).jsonObject
        assertEquals("C3", wire["compartment"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, wire["occupant"])
        assertEquals(Assignment(compartment = "C3", occupant = null), cleared.body<Assignment>())
        assertNull(occupants["C3"])

        val after: Occupant? = courierApp.get(url("/compartments/C3/occupant")).body<Occupant?>()
        assertNull(after)
    }

    @Test
    fun rawNullLiteralClearsCompartment(): Unit = runBlocking {
        val response: HttpResponse = raw.put(url("/compartments/A1/occupant")) {
            contentType(ContentType.Application.Json)
            setBody("null")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(occupants["A1"])
    }

    @Test
    fun reservationRequiresAnOccupant(): Unit = runBlocking {
        val rejected: HttpResponse = raw.put(url("/compartments/D4/reservation")) {
            contentType(ContentType.Application.Json)
            setBody("null")
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertNull(occupants["D4"])

        val parcel = Occupant(parcelId = "PX-3003", recipient = "Nada", pickupCode = 667120)
        val accepted: HttpResponse = courierApp.put(url("/compartments/D4/reservation")) {
            contentType(ContentType.Application.Json)
            setBody(parcel)
        }
        assertEquals(HttpStatusCode.Created, accepted.status)
        assertEquals(Assignment(compartment = "D4", occupant = parcel), accepted.body<Assignment>())
    }
}
