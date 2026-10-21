/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_websockets_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSocketUpgrade
import io.ktor.server.websocket.WebSockets
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Covers upgrades answered by hand with a `WebSocketUpgrade` response: a chess server's plain
 * `get` route checks the player and the game before deciding, upgrades an admitted player by
 * responding with `WebSocketUpgrade` under its subprotocol, and refuses others with HTTP errors.
 */
class ManualUpgradeResponseTest {

    /** What one player observed over the upgraded connection. */
    private data class PlayedGame(val protocol: String?, val messages: List<String>, val closeReason: CloseReason?)

    @Test
    fun upgradesAnAdmittedPlayerAndPlaysUntilTheServerEndsTheGame(): Unit = testApplication {
        installChessServer()
        val client: HttpClient = createClient {
            install(ClientWebSockets)
        }
        val messages: MutableList<String> = mutableListOf()
        var protocol: String? = null
        var closeReason: CloseReason? = null

        client.webSocket(
            "/games/42/play?player=ada",
            request = { header(HttpHeaders.SecWebSocketProtocol, PROTOCOL) },
        ) {
            protocol = call.response.headers[HttpHeaders.SecWebSocketProtocol]
            messages += (incoming.receive() as Frame.Text).readText()
            for (move: String in listOf("e4", "Nf3")) {
                send(Frame.Text("move $move"))
                messages += (incoming.receive() as Frame.Text).readText()
            }
            for (frame: Frame in incoming) {
                messages += "unexpected ${frame.frameType}"
            }
            closeReason = this.closeReason.await()
        }
        val game = PlayedGame(protocol, messages, closeReason)

        assertEquals(PROTOCOL, game.protocol)
        assertEquals(listOf("welcome ada to game 42", "ack e4 (1/2)", "ack Nf3 (2/2)"), game.messages)
        assertNotNull(game.closeReason, "the server should end the game with a close frame")
        assertEquals(CloseReason.Codes.NORMAL.code, game.closeReason!!.code)
        assertEquals("game over", game.closeReason!!.message)
    }

    @Test
    fun refusesAnUnknownPlayerWithoutUpgrading(): Unit = testApplication {
        installChessServer()

        val response: HttpResponse = client.get("/games/42/play?player=mallory")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("unknown player mallory", response.bodyAsText())
    }

    @Test
    fun refusesAFinishedGameWithoutUpgrading(): Unit = testApplication {
        installChessServer()

        val response: HttpResponse = client.get("/games/$FINISHED_GAME/play?player=ada")

        assertEquals(HttpStatusCode.Gone, response.status)
        assertEquals("game $FINISHED_GAME is over", response.bodyAsText())
    }

    private fun ApplicationTestBuilder.installChessServer() {
        application {
            install(WebSockets)
            routing {
                get("/games/{gameId}/play") {
                    val gameId: String = call.parameters["gameId"]!!
                    val player: String? = call.request.queryParameters["player"]
                    when {
                        player !in PLAYERS ->
                            call.respondText("unknown player $player", status = HttpStatusCode.Forbidden)
                        gameId == FINISHED_GAME ->
                            call.respondText("game $gameId is over", status = HttpStatusCode.Gone)
                        else -> call.respond(
                            WebSocketUpgrade(call, protocol = PROTOCOL) {
                                playGame(this, gameId, player!!)
                            },
                        )
                    }
                }
            }
        }
    }

    /** Runs one game on the raw upgraded session: greet, acknowledge moves, and close after the last. */
    private suspend fun playGame(session: WebSocketSession, gameId: String, player: String) {
        session.outgoing.send(Frame.Text("welcome $player to game $gameId"))
        var moves = 0
        for (frame: Frame in session.incoming) {
            when (frame) {
                is Frame.Text -> {
                    moves += 1
                    val move: String = frame.readText().removePrefix("move ")
                    session.outgoing.send(Frame.Text("ack $move ($moves/$MOVES_PER_GAME)"))
                    if (moves == MOVES_PER_GAME) {
                        session.outgoing.send(Frame.Close(CloseReason(CloseReason.Codes.NORMAL, "game over")))
                    }
                }
                is Frame.Close -> break
                else -> Unit
            }
        }
    }

    private companion object {

        const val PROTOCOL: String = "chess.v1"

        const val FINISHED_GAME: String = "7"

        const val MOVES_PER_GAME: Int = 2

        val PLAYERS: Set<String> = setOf("ada", "grace")
    }
}
