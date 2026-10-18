/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.basic
import io.ktor.server.auth.form
import io.ktor.server.auth.principal
import io.ktor.server.auth.session
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionTransportTransformerMessageAuthentication
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.util.hex
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * The CIO engine's authentication and cookie sessions: form login, signed session cookies,
 * session-backed and HTTP Basic protected routes, and logout.
 *
 * A library lending desk runs on an embedded CIO server. A reader signs in with a form that
 * carries their card number and PIN; the server answers with a signed cookie holding a
 * serializable session that counts the reader's shelf visits. The test checks that the
 * session survives across requests, that a forged or missing cookie is challenged, that
 * the librarians' loan ledger asks for HTTP Basic credentials with a realm, and that
 * logging out drops the session.
 */
class LendingDeskAuthSessionTest {

    @Serializable
    data class ReaderSession(val card: String, val name: String, val visits: Int)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0
    private val readers: Map<String, Pair<String, String>> = mapOf(
        "4471" to ("Ada Byron" to "1815"),
        "9032" to ("Grace Hopper" to "1906"),
    )
    private val loans: MutableMap<String, String> = ConcurrentHashMap()

    @BeforeEach
    fun start() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            install(Sessions) {
                cookie<ReaderSession>("lending_desk") {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    transform(SessionTransportTransformerMessageAuthentication(hex("6c656e64696e672d6465736b2d6b6579")))
                }
            }
            install(Authentication) {
                form("pin-login") {
                    userParamName = "card"
                    passwordParamName = "pin"
                    validate { credentials ->
                        if (readers[credentials.name]?.second == credentials.password) {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                    challenge { call.respondText("wrong card or PIN", status = HttpStatusCode.Unauthorized) }
                }
                session<ReaderSession>("reader") {
                    validate { session: ReaderSession -> session.takeIf { it.card in readers } }
                    challenge { call.respondText("sign in at the desk", status = HttpStatusCode.Unauthorized) }
                }
                basic("librarian") {
                    realm = "Loan Ledger"
                    validate { credentials ->
                        if (credentials.name == "head" && credentials.password == "dewey-decimal") {
                            UserIdPrincipal(credentials.name)
                        } else {
                            null
                        }
                    }
                }
            }
            routing {
                authenticate("pin-login") {
                    post("/login") {
                        val card: String = call.principal<UserIdPrincipal>()!!.name
                        call.sessions.set(ReaderSession(card, readers.getValue(card).first, visits = 0))
                        call.respondText("welcome ${readers.getValue(card).first}")
                    }
                }
                authenticate("reader") {
                    get("/shelf") {
                        val session: ReaderSession = call.principal<ReaderSession>()!!
                        val next: ReaderSession = session.copy(visits = session.visits + 1)
                        call.sessions.set(next)
                        call.respondText("${next.name} visit ${next.visits}")
                    }
                    post("/borrow") {
                        val session: ReaderSession = call.principal<ReaderSession>()!!
                        val title: String = call.request.queryParameters["title"]!!
                        loans[title] = session.card
                        call.respondText("${session.name} borrowed $title")
                    }
                    post("/logout") {
                        call.sessions.clear<ReaderSession>()
                        call.respondText("goodbye")
                    }
                }
                authenticate("librarian") {
                    get("/ledger") {
                        val librarian: String = call.principal<UserIdPrincipal>()!!.name
                        call.respondText("$librarian sees " + loans.toSortedMap().entries.joinToString { "${it.key}:${it.value}" })
                    }
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun readersSignInKeepSignedSessionsAndLibrariansUseBasicAuth() {
        val base = "http://127.0.0.1:$port"
        HttpClient(ClientCIO) { install(HttpCookies) { storage = AcceptAllCookiesStorage() } }.use { client: HttpClient ->
            runBlocking {
                val anonymous: HttpResponse = client.get("$base/shelf")
                assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
                assertEquals("sign in at the desk", anonymous.bodyAsText())

                val badPin: HttpResponse = client.submitForm(
                    url = "$base/login",
                    formParameters = parameters {
                        append("card", "4471")
                        append("pin", "0000")
                    },
                )
                assertEquals(HttpStatusCode.Unauthorized, badPin.status)
                assertEquals("wrong card or PIN", badPin.bodyAsText())
                assertTrue(client.cookies(base).isEmpty())

                val login: HttpResponse = client.submitForm(
                    url = "$base/login",
                    formParameters = parameters {
                        append("card", "4471")
                        append("pin", "1815")
                    },
                )
                assertEquals(HttpStatusCode.OK, login.status)
                assertEquals("welcome Ada Byron", login.bodyAsText())
                val issued: Cookie = client.cookies(base).single { it.name == "lending_desk" }
                assertTrue(issued.httpOnly)
                assertTrue(issued.value.contains('/'), "signed cookie carries a value/signature pair")

                assertEquals("Ada Byron visit 1", client.get("$base/shelf").bodyAsText())
                assertEquals("Ada Byron visit 2", client.get("$base/shelf").bodyAsText())
                assertEquals("Ada Byron borrowed Dune", client.post("$base/borrow?title=Dune").bodyAsText())

                val current: String = client.cookies(base).single { it.name == "lending_desk" }.value
                val forged: String = current.dropLast(1) + if (current.last() == '0') '1' else '0'
                HttpClient(ClientCIO).use { intruder: HttpClient ->
                    val tampered: HttpResponse = intruder.get("$base/shelf") {
                        header(HttpHeaders.Cookie, "lending_desk=$forged")
                    }
                    assertEquals(HttpStatusCode.Unauthorized, tampered.status)
                    assertEquals("sign in at the desk", tampered.bodyAsText())
                }

                val noCredentials: HttpResponse = client.get("$base/ledger")
                assertEquals(HttpStatusCode.Unauthorized, noCredentials.status)
                assertEquals(
                    "Basic realm=\"Loan Ledger\", charset=UTF-8",
                    noCredentials.headers[HttpHeaders.WWWAuthenticate],
                )
                val wrongCredentials: HttpResponse = client.get("$base/ledger") { basicAuth("head", "library") }
                assertEquals(HttpStatusCode.Unauthorized, wrongCredentials.status)
                val ledger: HttpResponse = client.get("$base/ledger") { basicAuth("head", "dewey-decimal") }
                assertEquals(HttpStatusCode.OK, ledger.status)
                assertEquals("head sees Dune:4471", ledger.bodyAsText())

                assertEquals("goodbye", client.post("$base/logout").bodyAsText())
                assertTrue(client.cookies(base).none { it.name == "lending_desk" })
                val afterLogout: HttpResponse = client.get("$base/shelf")
                assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
            }
        }
        assertEquals(mapOf("Dune" to "4471"), loans.toMap())
    }
}
