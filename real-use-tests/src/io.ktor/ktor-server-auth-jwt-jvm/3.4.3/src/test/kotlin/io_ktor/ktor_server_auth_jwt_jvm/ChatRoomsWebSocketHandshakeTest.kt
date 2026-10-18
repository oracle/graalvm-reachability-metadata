/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_auth_jwt_jvm

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * JWT authentication of WebSocket upgrade handshakes, where a chat service admits a
 * browser to a room's live channel only with a token its sign-in service issued.
 *
 * Browsers cannot set headers on a WebSocket connection, so `authHeader` reads the
 * token from the `access_token` query parameter before the `Authorization` header.
 * The provider runs on the upgrade request: a missing or rejected token refuses the
 * handshake with the bearer challenge, while an accepted one leaves the [JWTPrincipal]
 * on the call for the whole session, which the room handler reads to admit the member,
 * name their messages, or close the channel when the token does not list the room.
 */
public class ChatRoomsWebSocketHandshakeTest {
    /** Raised by the client when the chat refuses a handshake, carrying the challenge it answered with. */
    private class SignInRequired(val challenge: String?) : Exception("chat asked to sign in: $challenge")

    private lateinit var server: EmbeddedServer<*, *>
    private lateinit var client: HttpClient
    private lateinit var baseUrl: String

    /** Starts the chat on a local CIO server and a browser-like client that asks to sign in on a refused handshake. */
    @BeforeEach
    fun startChat() {
        server = embeddedServer(CIO, port = 0, host = "127.0.0.1") { chat() }.start(wait = false)
        val port: Int = runBlocking { server.engine.resolvedConnectors().first().port }
        baseUrl = "ws://127.0.0.1:$port"
        client = HttpClient(ClientCIO) {
            install(ClientWebSockets)
            HttpResponseValidator {
                validateResponse { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        throw SignInRequired(response.headers[HttpHeaders.WWWAuthenticate])
                    }
                }
            }
        }
    }

    @AfterEach
    fun stopChat() {
        client.close()
        server.stop(0, 0)
    }

    /** The chat application: the `chat-jwt` provider and the room channels it guards. */
    private fun Application.chat() {
        install(ServerWebSockets)
        install(Authentication) {
            jwt("chat-jwt") {
                realm = REALM
                authHeader { call ->
                    val queryToken: String? = call.request.queryParameters[TOKEN_PARAMETER]
                    if (queryToken != null) {
                        HttpAuthHeader.Single("Bearer", queryToken)
                    } else {
                        call.request.parseAuthorizationHeader()
                    }
                }
                verifier(
                    JWT.require(Algorithm.HMAC256(SECRET))
                        .withIssuer(ISSUER)
                        .withAudience(AUDIENCE)
                        .build(),
                )
                validate { credential ->
                    if (credential.payload.getClaim("display").asString().isNullOrBlank()) {
                        null
                    } else {
                        JWTPrincipal(credential.payload)
                    }
                }
                challenge { defaultScheme, realm ->
                    call.response.header(HttpHeaders.WWWAuthenticate, "$defaultScheme realm=\"$realm\"")
                    call.respondText("sign in to chat first", status = HttpStatusCode.Unauthorized)
                }
            }
        }
        routing {
            authenticate("chat-jwt") {
                webSocket("/rooms/{room}") {
                    val principal: JWTPrincipal = call.principal<JWTPrincipal>()!!
                    val room: String = call.parameters["room"]!!
                    val display: String = principal.getClaim("display", String::class)!!
                    if (room !in principal.getListClaim("rooms", String::class)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "${principal.subject} is not in $room"))
                        return@webSocket
                    }
                    send("welcome $display to $room")
                    for (frame in incoming) {
                        if (frame !is Frame.Text) {
                            continue
                        }
                        val text: String = frame.readText()
                        if (text == "/leave") {
                            close(CloseReason(CloseReason.Codes.NORMAL, "$display left $room"))
                        } else {
                            send("[$room] $display (${principal.subject}): $text")
                        }
                    }
                }
            }
        }
    }

    /** A token the sign-in service issued for [subject], before [customize] adjusts it, signed with [secret]. */
    private fun token(
        subject: String,
        secret: String = SECRET,
        customize: JWTCreator.Builder.() -> Unit = {},
    ): String {
        val builder: JWTCreator.Builder = JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withSubject(subject)
            .withClaim("display", subject.replaceFirstChar { it.uppercase() })
            .withClaim("rooms", listOf("general", "release"))
            .withExpiresAt(Date(System.currentTimeMillis() + LIFETIME_MILLIS))
        builder.customize()
        return builder.sign(Algorithm.HMAC256(secret))
    }

    @Test
    fun headerTokenOpensARoomChannelThatNamesTheMember() = runBlocking {
        val transcript: MutableList<String> = mutableListOf()
        var leaveReason: CloseReason? = null

        client.webSocket("$baseUrl/rooms/general", request = { bearerAuth(token("ana")) }) {
            transcript += receiveText()
            send("hello all")
            transcript += receiveText()
            send("ship it")
            transcript += receiveText()
            send("/leave")
            leaveReason = awaitClose()
        }

        assertEquals(
            listOf(
                "welcome Ana to general",
                "[general] Ana (ana): hello all",
                "[general] Ana (ana): ship it",
            ),
            transcript,
        )
        assertEquals(CloseReason(CloseReason.Codes.NORMAL, "Ana left general"), leaveReason)
    }

    @Test
    fun queryParameterTokenAuthenticatesABrowserHandshake() = runBlocking {
        val transcript: MutableList<String> = mutableListOf()

        client.webSocket("$baseUrl/rooms/release?$TOKEN_PARAMETER=${token("milan")}") {
            transcript += receiveText()
            send("tagging 2.0")
            transcript += receiveText()
            send("/leave")
            awaitClose()
        }

        assertEquals(listOf("welcome Milan to release", "[release] Milan (milan): tagging 2.0"), transcript)
    }

    @Test
    fun memberOutsideTheRoomIsClosedWithAPolicyViolation() = runBlocking {
        var reason: CloseReason? = null

        client.webSocket("$baseUrl/rooms/security", request = { bearerAuth(token("ana")) }) {
            reason = awaitClose()
        }

        assertEquals(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "ana is not in security"), reason)
    }

    @Test
    fun handshakeWithoutATokenIsRefusedWithTheChallenge() = runBlocking {
        assertRefused(refusedHandshake("/rooms/general"))
    }

    @Test
    fun handshakesWithRejectedTokensAreRefusedWithTheChallenge() = runBlocking {
        val rejected: List<String> = listOf(
            token("ana", secret = "another-services-secret"),
            token("ana") { withAudience("billing") },
            token("ana") { withExpiresAt(Date(EXPIRED_AT_MILLIS)) },
            token("ana") { withClaim("display", "") },
        )

        for (issued in rejected) {
            assertRefused(refusedHandshake("/rooms/general") { bearerAuth(issued) })
            assertRefused(refusedHandshake("/rooms/general?$TOKEN_PARAMETER=$issued"))
        }
    }

    @Test
    fun queryParameterTokenTakesPrecedenceOverTheHeader() = runBlocking {
        val refused: String? = refusedHandshake("/rooms/general?$TOKEN_PARAMETER=not.a.jwt") {
            bearerAuth(token("ana"))
        }
        var welcome: String? = null
        client.webSocket(
            "$baseUrl/rooms/general?$TOKEN_PARAMETER=${token("milan")}",
            request = { bearerAuth(token("ana", secret = "another-services-secret")) },
        ) {
            welcome = receiveText()
            send("/leave")
            awaitClose()
        }

        assertRefused(refused)
        assertEquals("welcome Milan to general", welcome)
    }

    /** Opens a channel to [path] that the chat must refuse, and returns the challenge the handshake got. */
    private suspend fun refusedHandshake(path: String, configure: HttpRequestBuilder.() -> Unit = {}): String? {
        val failure: Throwable? = runCatching {
            client.webSocket("$baseUrl$path", request = configure) {
                fail<Unit>("the chat accepted the handshake for $path")
            }
        }.exceptionOrNull()
        return assertInstanceOf(SignInRequired::class.java, failure).challenge
    }

    private suspend fun DefaultClientWebSocketSession.receiveText(): String = withTimeout(TIMEOUT_MILLIS) {
        (incoming.receive() as Frame.Text).readText()
    }

    private suspend fun DefaultClientWebSocketSession.awaitClose(): CloseReason? = withTimeout(TIMEOUT_MILLIS) {
        closeReason.await()
    }

    private fun assertRefused(challenge: String?) {
        assertEquals("Bearer realm=\"$REALM\"", challenge)
    }

    private companion object {
        const val SECRET: String = "chat-signing-secret"
        const val ISSUER: String = "https://signin.chat.example"
        const val AUDIENCE: String = "chat-rooms"
        const val REALM: String = "chat"
        const val TOKEN_PARAMETER: String = "access_token"
        const val LIFETIME_MILLIS: Long = 3_600_000
        const val EXPIRED_AT_MILLIS: Long = 1_000_000_000_000
        const val TIMEOUT_MILLIS: Long = 10_000
    }
}
