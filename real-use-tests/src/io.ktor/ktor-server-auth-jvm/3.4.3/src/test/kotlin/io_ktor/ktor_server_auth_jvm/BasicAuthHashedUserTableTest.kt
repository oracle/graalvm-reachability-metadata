/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jvm

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserHashedTableAuth
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.principal
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.getDigestFunction
import kotlin.io.encoding.Base64
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * HTTP Basic authentication validated against a `UserHashedTableAuth` of salted SHA-256 digests.
 *
 * An inventory service stores only password digests, protects its stock report behind a Basic
 * realm, and greets the authenticated principal by name. Wrong passwords, unknown users, and
 * missing credentials are all refused with a Basic challenge naming the realm, while the public
 * health endpoint stays open.
 */
class BasicAuthHashedUserTableTest {

    /** Builds the user table the way an application does at startup, keeping only digests. */
    private fun userTable(): UserHashedTableAuth {
        val digester: (String) -> ByteArray = getDigestFunction("SHA-256") { user -> "inventory-salt-$user" }
        return UserHashedTableAuth(
            digester = digester,
            table = mapOf(
                "ada" to digester("lovelace"),
                "grace" to digester("hopper"),
            ),
        )
    }

    private fun Application.inventoryModule(users: UserHashedTableAuth) {
        install(Authentication) {
            basic("warehouse") {
                realm = "Warehouse Staff"
                validate { credentials -> users.authenticate(credentials) }
            }
        }
        routing {
            get("/health") {
                call.respondText("up")
            }
            authenticate("warehouse") {
                get("/stock") {
                    val principal: UserIdPrincipal = call.principal<UserIdPrincipal>()!!
                    call.respondText("stock report for ${principal.name}: 42 crates")
                }
            }
        }
    }

    private fun basicHeader(user: String, password: String): String =
        "Basic " + Base64.encode("$user:$password".encodeToByteArray())

    @Test
    fun hashedUserTableGuardsTheStockReport() = testApplication {
        val users: UserHashedTableAuth = userTable()
        application { inventoryModule(users) }

        assertEquals("up", client.get("/health").bodyAsText(), "the public endpoint needs no credentials")

        val ada: HttpResponse = client.get("/stock") { header(HttpHeaders.Authorization, basicHeader("ada", "lovelace")) }
        assertEquals(HttpStatusCode.OK, ada.status)
        assertEquals("stock report for ada: 42 crates", ada.bodyAsText())

        val grace: HttpResponse = client.get("/stock") {
            header(HttpHeaders.Authorization, basicHeader("grace", "hopper"))
        }
        assertEquals("stock report for grace: 42 crates", grace.bodyAsText())

        val refusals: List<HttpResponse> = listOf(
            client.get("/stock") { header(HttpHeaders.Authorization, basicHeader("ada", "hopper")) },
            client.get("/stock") { header(HttpHeaders.Authorization, basicHeader("linus", "torvalds")) },
            client.get("/stock"),
        )
        for (refused: HttpResponse in refusals) {
            assertEquals(HttpStatusCode.Unauthorized, refused.status)
            val challenge: HttpAuthHeader = parseAuthorizationHeader(refused.headers[HttpHeaders.WWWAuthenticate]!!)!!
            assertTrue(challenge is HttpAuthHeader.Parameterized, "the challenge carries parameters: $challenge")
            assertEquals("Basic", challenge.authScheme)
            assertEquals("Warehouse Staff", (challenge as HttpAuthHeader.Parameterized).parameter("realm"))
            assertEquals("UTF-8", challenge.parameter("charset"))
        }
    }
}
