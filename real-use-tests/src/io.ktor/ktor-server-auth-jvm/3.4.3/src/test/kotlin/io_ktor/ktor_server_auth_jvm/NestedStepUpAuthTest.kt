/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationRouteSelector
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.HttpMethodRouteSelector
import io.ktor.server.routing.PathSegmentConstantRouteSelector
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.server.routing.get
import io.ktor.server.routing.getAllRoutes
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Nested `authenticate` blocks that layer an HTTP Basic step-up login over device bearer tokens.
 *
 * A smart-home hub lets paired devices read their status with a token sent in a custom header,
 * while a factory reset sits in a nested `Required` block that also demands the owner's password.
 * Nested blocks with the default strategy merge into alternatives instead, so the heating schedule
 * is readable by either a device or the owner. The hub also walks its route tree through the
 * authentication route selectors to publish which providers guard each endpoint, the way an
 * application documents its security surface.
 */
class NestedStepUpAuthTest {

    /** A paired device the hub issued a token to. */
    data class Device(val id: String, val room: String)

    /** Pairing tokens and owner passwords an application would normally keep in a database. */
    class HubRegistry(private val tokens: Map<String, Device>, private val owners: Map<String, String>) {
        fun device(token: String): Device? = tokens[token]

        fun isOwner(name: String, password: String): Boolean = owners[name] == password
    }

    private lateinit var routes: RoutingRoot

    private fun Application.hubModule(registry: HubRegistry) {
        install(Authentication) {
            bearer("device-token") {
                realm = "Hub devices"
                authHeader { call ->
                    call.request.headers["X-Device-Token"]?.let { token -> HttpAuthHeader.Single("Bearer", token) }
                }
                authenticate { credential -> registry.device(credential.token) }
            }
            basic("owner-step-up") {
                realm = "Hub owner"
                validate { credentials ->
                    if (registry.isOwner(credentials.name, credentials.password)) UserIdPrincipal(credentials.name) else null
                }
            }
        }
        routes = routing {
            get("/health") {
                call.respondText("ok")
            }
            authenticate("device-token", strategy = AuthenticationStrategy.Required) {
                route("/devices") {
                    get("/status") {
                        val device: Device = call.principal<Device>()!!
                        call.respondText("${device.id} in ${device.room}: online")
                    }
                    authenticate("owner-step-up", strategy = AuthenticationStrategy.Required) {
                        post("/reset") {
                            val device: Device = call.principal<Device>("device-token")!!
                            val owner: UserIdPrincipal = call.principal<UserIdPrincipal>("owner-step-up")!!
                            call.respondText("${device.id} reset by ${owner.name}")
                        }
                    }
                }
            }
            authenticate("device-token") {
                authenticate("owner-step-up") {
                    get("/schedule") {
                        val reader: String = call.principal<Device>()?.id ?: call.principal<UserIdPrincipal>()!!.name
                        call.respondText("schedule for $reader: 21C from 06:30")
                    }
                }
            }
        }
    }

    /** Lists every endpoint with the providers of all `authenticate` blocks it is nested in. */
    private fun securityInventory(root: RoutingRoot): Map<String, List<String>> =
        root.getAllRoutes().associate { endpoint ->
            val chain: List<RoutingNode> = generateSequence(endpoint) { node -> node.parent }.toList().reversed()
            val method: String = chain.firstNotNullOf { node -> (node.selector as? HttpMethodRouteSelector)?.method?.value }
            val path: String = chain.mapNotNull { node -> (node.selector as? PathSegmentConstantRouteSelector)?.value }
                .joinToString(separator = "/", prefix = "/")
            val providers: List<String> = chain.flatMap { node ->
                (node.selector as? AuthenticationRouteSelector)?.names.orEmpty().filterNotNull()
            }
            "$method $path" to providers
        }

    private fun assertChallenge(response: HttpResponse, scheme: String, realm: String) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge: HttpAuthHeader = parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!)!!
        assertEquals(scheme, challenge.authScheme)
        assertEquals(realm, (challenge as HttpAuthHeader.Parameterized).parameter("realm"))
    }

    @Test
    fun resetNeedsDeviceTokenAndOwnerStepUp() = testApplication {
        val registry: HubRegistry = HubRegistry(
            tokens = mapOf("tok-thermo-7" to Device("thermostat-7", "hallway")),
            owners = mapOf("margaret" to "lighthouse-42"),
        )
        application { hubModule(registry) }

        assertEquals("ok", client.get("/health").bodyAsText())

        assertChallenge(client.get("/devices/status"), "Bearer", "Hub devices")
        assertChallenge(client.get("/devices/status") { header("X-Device-Token", "tok-unknown") }, "Bearer", "Hub devices")
        val status: HttpResponse = client.get("/devices/status") { header("X-Device-Token", "tok-thermo-7") }
        assertEquals(HttpStatusCode.OK, status.status)
        assertEquals("thermostat-7 in hallway: online", status.bodyAsText())

        val tokenOnly: HttpResponse = client.post("/devices/reset") { header("X-Device-Token", "tok-thermo-7") }
        assertChallenge(tokenOnly, "Basic", "Hub owner")

        val wrongPassword: HttpResponse = client.post("/devices/reset") {
            header("X-Device-Token", "tok-thermo-7")
            basicAuth("margaret", "harbour-13")
        }
        assertChallenge(wrongPassword, "Basic", "Hub owner")

        val ownerOnly: HttpResponse = client.post("/devices/reset") { basicAuth("margaret", "lighthouse-42") }
        assertChallenge(ownerOnly, "Bearer", "Hub devices")

        val reset: HttpResponse = client.post("/devices/reset") {
            header("X-Device-Token", "tok-thermo-7")
            basicAuth("margaret", "lighthouse-42")
        }
        assertEquals(HttpStatusCode.OK, reset.status)
        assertEquals("thermostat-7 reset by margaret", reset.bodyAsText())

        assertChallenge(client.get("/schedule"), "Bearer", "Hub devices")
        val deviceSchedule: HttpResponse = client.get("/schedule") { header("X-Device-Token", "tok-thermo-7") }
        assertEquals("schedule for thermostat-7: 21C from 06:30", deviceSchedule.bodyAsText())
        val ownerSchedule: HttpResponse = client.get("/schedule") { basicAuth("margaret", "lighthouse-42") }
        assertEquals("schedule for margaret: 21C from 06:30", ownerSchedule.bodyAsText())

        assertEquals(
            mapOf(
                "GET /health" to emptyList(),
                "GET /devices/status" to listOf("device-token"),
                "POST /devices/reset" to listOf("device-token", "owner-step-up"),
                "GET /schedule" to listOf("device-token", "owner-step-up"),
            ),
            securityInventory(routes),
        )
    }
}
