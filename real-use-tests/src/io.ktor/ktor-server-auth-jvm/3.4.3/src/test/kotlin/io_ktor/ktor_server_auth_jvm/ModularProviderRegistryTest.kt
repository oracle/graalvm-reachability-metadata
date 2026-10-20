/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.basicAuth
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.authentication
import io.ktor.server.auth.basic
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The `Authentication` provider registry extended by separate application modules through `authentication { }`.
 *
 * A newsroom CMS is assembled from modules. The core module installs `Authentication` with an
 * unnamed default Basic provider for editors; the syndication module, loaded later, registers a
 * named Bearer provider for wire partners from its own `authentication { }` block and mounts its
 * feed beside a wire endpoint that accepts either credential. A mirror module that tries to claim
 * the same provider name is turned away with the registry's conflict error and records it, while
 * the provider registered first keeps serving partners.
 */
class ModularProviderRegistryTest {

    /** A news agency that receives the syndication feed. */
    data class WirePartner(val agency: String)

    /** Conflicts the module loader collected instead of aborting the start-up. */
    class ModuleLoadReport {
        val conflicts: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    }

    private val editors: Map<String, String> = mapOf("grace" to "press-room-42")
    private val partnerTokens: Map<String, WirePartner> = mapOf("wire-reuters-5d1e" to WirePartner("Reuters"))

    private fun Application.coreModule() {
        install(Authentication) {
            basic {
                realm = "Newsroom"
                validate { credentials ->
                    if (editors[credentials.name] == credentials.password) UserIdPrincipal(credentials.name) else null
                }
            }
        }
        routing {
            authenticate {
                get("/drafts") {
                    call.respondText("drafts for ${call.principal<UserIdPrincipal>()!!.name}: 3")
                }
            }
        }
    }

    private fun Application.syndicationModule() {
        authentication {
            bearer("syndication") {
                realm = "Syndication"
                authenticate { credential -> partnerTokens[credential.token] }
            }
        }
        routing {
            authenticate("syndication") {
                get("/feed") {
                    call.respondText("feed for ${call.principal<WirePartner>()!!.agency}: 12 stories")
                }
            }
            authenticate(null, "syndication") {
                get("/wire") {
                    val reader: String = call.principal<UserIdPrincipal>()?.let { "editor ${it.name}" }
                        ?: "partner ${call.principal<WirePartner>()!!.agency}"
                    call.respondText("wire opened by $reader")
                }
            }
        }
    }

    private fun Application.mirrorModule(report: ModuleLoadReport) {
        try {
            authentication {
                bearer("syndication") {
                    authenticate { credential -> WirePartner("mirror of ${credential.token}") }
                }
            }
        } catch (conflict: IllegalArgumentException) {
            report.conflicts.add(conflict.message!!)
        }
    }

    private fun challengeScheme(response: HttpResponse): String {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge: HttpAuthHeader = parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!)!!
        return challenge.authScheme
    }

    @Test
    fun modulesRegisterTheirOwnProviders() = testApplication {
        val report: ModuleLoadReport = ModuleLoadReport()
        application {
            coreModule()
            syndicationModule()
            mirrorModule(report)
        }

        val drafts: HttpResponse = client.get("/drafts") { basicAuth("grace", "press-room-42") }
        assertEquals(HttpStatusCode.OK, drafts.status)
        assertEquals("drafts for grace: 3", drafts.bodyAsText())
        assertEquals(
            "Basic",
            challengeScheme(client.get("/drafts") { bearerAuth("wire-reuters-5d1e") }),
            "the default provider only knows editors",
        )

        val feed: HttpResponse = client.get("/feed") { bearerAuth("wire-reuters-5d1e") }
        assertEquals(HttpStatusCode.OK, feed.status)
        assertEquals("feed for Reuters: 12 stories", feed.bodyAsText(), "the first registration still serves the feed")
        assertEquals(
            "Bearer",
            challengeScheme(client.get("/feed") { basicAuth("grace", "press-room-42") }),
            "the syndication provider only knows partners",
        )
        assertEquals("Bearer", challengeScheme(client.get("/feed") { bearerAuth("wire-unknown-0000") }))

        val editorWire: HttpResponse = client.get("/wire") { basicAuth("grace", "press-room-42") }
        assertEquals("wire opened by editor grace", editorWire.bodyAsText())
        val partnerWire: HttpResponse = client.get("/wire") { bearerAuth("wire-reuters-5d1e") }
        assertEquals("wire opened by partner Reuters", partnerWire.bodyAsText())
        val anonymousWire: HttpResponse = client.get("/wire")
        assertEquals(HttpStatusCode.Unauthorized, anonymousWire.status)
        assertNotNull(anonymousWire.headers[HttpHeaders.WWWAuthenticate], "the wire still challenges anonymous readers")

        assertEquals(listOf("Provider with the name syndication is already registered"), report.conflicts.toList())
    }
}
