/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionProvider
import io.ktor.server.sessions.SessionTrackerByValue
import io.ktor.server.sessions.SessionTransport
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.defaultSessionSerializer
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Application-implemented `SessionTransport` registered on the plugin through a `SessionProvider`.
 *
 * A mobile backend does not use cookies: the app sends its device session in the standard
 * `Authorization: Session <token>` request header and picks up refreshed tokens from an
 * `X-Session-Token` response header. The application writes that transport itself, pairs it
 * with a by-value tracker and the default serializer, and registers the provider directly.
 * Requests that authorize with another scheme are treated as anonymous, and logging out asks
 * the app to drop its token through an `X-Session-Revoked` header.
 */
class AuthorizationSchemeTransportSessionTest {

    @Serializable
    data class DeviceSession(val deviceId: String, val locale: String, val pushEnabled: Boolean, val launches: Int)

    class AuthorizationSchemeTransport(private val scheme: String) : SessionTransport {
        override fun receive(call: ApplicationCall): String? {
            val authorization: String = call.request.headers[HttpHeaders.Authorization] ?: return null
            val prefix = "$scheme "
            return if (authorization.startsWith(prefix)) authorization.removePrefix(prefix) else null
        }

        override fun send(call: ApplicationCall, value: String) {
            call.response.header(TOKEN_HEADER, value)
        }

        override fun clear(call: ApplicationCall) {
            call.response.header(REVOKED_HEADER, "true")
        }
    }

    @Test
    fun deviceSessionTravelsInAuthorizationHeader() = testApplication {
        application {
            install(Sessions) {
                register(
                    SessionProvider(
                        name = "DEVICE",
                        type = DeviceSession::class,
                        transport = AuthorizationSchemeTransport("Session"),
                        tracker = SessionTrackerByValue(DeviceSession::class, defaultSessionSerializer<DeviceSession>()),
                    ),
                )
            }
            routing {
                post("/devices/register") {
                    val deviceId: String = call.request.queryParameters["device"]!!
                    val locale: String = call.request.queryParameters["locale"] ?: "en-US"
                    call.sessions.set(DeviceSession(deviceId, locale, pushEnabled = false, launches = 1))
                    call.respondText("registered $deviceId", status = HttpStatusCode.Created)
                }
                post("/me/push") {
                    val session: DeviceSession? = call.sessions.get<DeviceSession>()
                    if (session == null) {
                        call.respondText("anonymous", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.sessions.set(session.copy(pushEnabled = true, launches = session.launches + 1))
                        call.respondText("push on")
                    }
                }
                get("/me") {
                    val session: DeviceSession? = call.sessions.get<DeviceSession>()
                    if (session == null) {
                        call.respondText("anonymous", status = HttpStatusCode.Unauthorized)
                    } else {
                        call.respondText(
                            "${session.deviceId} ${session.locale} push=${session.pushEnabled} launches=${session.launches}",
                        )
                    }
                }
                post("/devices/logout") {
                    val session: DeviceSession? = call.sessions.get<DeviceSession>()
                    call.sessions.clear<DeviceSession>()
                    call.respondText("bye ${session?.deviceId ?: "nobody"}")
                }
            }
        }

        val registered: HttpResponse = client.post("/devices/register?device=pixel-9&locale=de-CH")
        assertEquals(HttpStatusCode.Created, registered.status)
        assertEquals("registered pixel-9", registered.bodyAsText())
        assertNull(registered.headers[HttpHeaders.SetCookie])
        assertNotNull(registered.headers[TOKEN_HEADER])
        val firstToken: String = registered.headers[TOKEN_HEADER]!!

        val profile: HttpResponse = client.get("/me") { header(HttpHeaders.Authorization, "Session $firstToken") }
        assertEquals(HttpStatusCode.OK, profile.status)
        assertEquals("pixel-9 de-CH push=false launches=1", profile.bodyAsText())

        val pushOn: HttpResponse = client.post("/me/push") { header(HttpHeaders.Authorization, "Session $firstToken") }
        assertEquals("push on", pushOn.bodyAsText())
        assertNotNull(pushOn.headers[TOKEN_HEADER])
        val refreshedToken: String = pushOn.headers[TOKEN_HEADER]!!
        assertNotEquals(firstToken, refreshedToken)

        val refreshedProfile: String = client.get("/me") {
            header(HttpHeaders.Authorization, "Session $refreshedToken")
        }.bodyAsText()
        assertEquals("pixel-9 de-CH push=true launches=2", refreshedProfile)

        val staleProfile: String = client.get("/me") { header(HttpHeaders.Authorization, "Session $firstToken") }.bodyAsText()
        assertEquals("pixel-9 de-CH push=false launches=1", staleProfile)

        val otherScheme: HttpResponse = client.get("/me") { header(HttpHeaders.Authorization, "Bearer $refreshedToken") }
        assertEquals(HttpStatusCode.Unauthorized, otherScheme.status)
        assertEquals("anonymous", otherScheme.bodyAsText())

        val noHeader: HttpResponse = client.get("/me")
        assertEquals(HttpStatusCode.Unauthorized, noHeader.status)

        val logout: HttpResponse = client.post("/devices/logout") {
            header(HttpHeaders.Authorization, "Session $refreshedToken")
        }
        assertEquals("bye pixel-9", logout.bodyAsText())
        assertEquals("true", logout.headers[REVOKED_HEADER])
        assertNull(logout.headers[TOKEN_HEADER])
    }

    companion object {
        const val TOKEN_HEADER: String = "X-Session-Token"
        const val REVOKED_HEADER: String = "X-Session-Revoked"
    }
}
