/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.basicAuth
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
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.AuthenticationStrategy
import io.ktor.server.auth.ForbiddenResponse
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Lambda-defined dynamic authentication providers registered with `provider { authenticate { } }` and gated by `skipWhen`.
 *
 * A document store shares files through expiring links signed with HMAC-SHA256. The link check
 * is an inline `provider` whose `authenticate` block verifies the signature and expiry, and its
 * `skipWhen` predicate steps aside for requests that carry no signature so that staff fall back
 * to HTTP Basic. A tampered or expired link is refused with `403 Forbidden` from the link
 * provider's own challenge instead of the Basic login prompt.
 */
class SignedLinkDynamicProviderTest {

    /** The single file a verified link grants access to. */
    data class SharedLink(val path: String, val expiresAt: Long)

    /** Signs and verifies download links against an application clock fixed by the caller. */
    class LinkSigner(secret: String, private val now: () -> Long) {
        private val key: SecretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

        fun sign(path: String, expiresAt: Long): String {
            val mac: Mac = Mac.getInstance("HmacSHA256")
            mac.init(key)
            return HexFormat.of().formatHex(mac.doFinal("$path\n$expiresAt".toByteArray()))
        }

        fun link(path: String, expiresAt: Long): String = "$path?expires=$expiresAt&signature=${sign(path, expiresAt)}"

        fun isExpired(expiresAt: Long): Boolean = now() >= expiresAt
    }

    private fun Application.documentStoreModule(signer: LinkSigner) {
        install(Authentication) {
            provider("signed-link") {
                skipWhen { call -> call.request.queryParameters["signature"] == null }
                authenticate { context ->
                    val path: String = context.call.request.path()
                    val expiresAt: Long? = context.call.request.queryParameters["expires"]?.toLongOrNull()
                    val signature: String = context.call.request.queryParameters["signature"]!!
                    val valid: Boolean = expiresAt != null &&
                        signer.sign(path, expiresAt) == signature &&
                        !signer.isExpired(expiresAt)
                    if (valid) {
                        context.principal(SharedLink(path, expiresAt))
                    } else {
                        context.challenge("SignedLink", AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                            call.respond(ForbiddenResponse())
                            challenge.complete()
                        }
                    }
                }
            }
            basic("staff") {
                realm = "Document store"
                validate { credentials ->
                    if (credentials.name == "noor" && credentials.password == "arch1ve") UserIdPrincipal(credentials.name) else null
                }
            }
        }
        routing {
            authenticate("signed-link", "staff", strategy = AuthenticationStrategy.FirstSuccessful) {
                get("/files/{name}") {
                    val name: String = call.parameters["name"]!!
                    val link: SharedLink? = call.principal<SharedLink>()
                    val staff: UserIdPrincipal? = call.principal<UserIdPrincipal>()
                    val via: String = if (link != null) "shared link until ${link.expiresAt}" else "staff ${staff!!.name}"
                    call.respondText("$name via $via")
                }
            }
        }
    }

    private fun assertBasicChallenge(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenge: HttpAuthHeader.Parameterized =
            parseAuthorizationHeader(response.headers[HttpHeaders.WWWAuthenticate]!!) as HttpAuthHeader.Parameterized
        assertEquals("Basic", challenge.authScheme)
        assertEquals("Document store", challenge.parameter("realm"))
    }

    @Test
    fun signedLinksAndStaffLoginShareOneRoute() = testApplication {
        val clock: Long = 1_760_000_000L
        val signer: LinkSigner = LinkSigner("store-signing-key-7731") { clock }
        application { documentStoreModule(signer) }

        val shared: HttpResponse = client.get(signer.link("/files/q3-report.pdf", clock + 600))
        assertEquals(HttpStatusCode.OK, shared.status)
        assertEquals("q3-report.pdf via shared link until ${clock + 600}", shared.bodyAsText())

        val staff: HttpResponse = client.get("/files/q3-report.pdf") { basicAuth("noor", "arch1ve") }
        assertEquals(HttpStatusCode.OK, staff.status, "skipWhen hands unsigned requests to the Basic provider")
        assertEquals("q3-report.pdf via staff noor", staff.bodyAsText())

        assertBasicChallenge(client.get("/files/q3-report.pdf"))
        assertBasicChallenge(client.get("/files/q3-report.pdf") { basicAuth("noor", "guess") })

        val otherFile: String = signer.link("/files/q3-report.pdf", clock + 600).replace("q3-report", "payroll")
        val retargeted: HttpResponse = client.get(otherFile)
        assertEquals(HttpStatusCode.Forbidden, retargeted.status, "a link signed for one file never opens another")
        assertEquals(null, retargeted.headers[HttpHeaders.WWWAuthenticate])

        val extended: String = signer.link("/files/q3-report.pdf", clock + 600).replace("${clock + 600}", "${clock + 86_400}")
        assertEquals(HttpStatusCode.Forbidden, client.get(extended).status, "the expiry is covered by the signature")

        val expired: HttpResponse = client.get(signer.link("/files/q3-report.pdf", clock - 1))
        assertEquals(HttpStatusCode.Forbidden, expired.status, "an expired link is refused even when signed")

        val malformed: HttpResponse = client.get("/files/q3-report.pdf?signature=deadbeef")
        assertEquals(HttpStatusCode.Forbidden, malformed.status, "a signature without an expiry is refused")
    }
}
