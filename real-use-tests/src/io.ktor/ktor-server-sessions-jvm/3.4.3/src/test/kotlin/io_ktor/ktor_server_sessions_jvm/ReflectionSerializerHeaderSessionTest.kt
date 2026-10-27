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
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.get
import io.ktor.server.sessions.header
import io.ktor.server.sessions.reflectionSessionSerializer
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reflection-based session serializer installed in place of the kotlinx-serialization default.
 *
 * A self-service kiosk keeps its sign-up wizard state in an `X-Wizard` header that the kiosk
 * front end replays on each step. The state holds JDK types such as [UUID] and [BigDecimal]
 * that kotlinx-serialization only knows as `@Contextual`, so the application switches the
 * session to `reflectionSessionSerializer`, which reads the properties through kotlin-reflect
 * and rebuilds the objects through their constructors on every request.
 */
class ReflectionSerializerHeaderSessionTest {

    enum class Plan { BASIC, FAMILY, PREMIUM }

    @Serializable
    data class Address(val street: String, val postalCode: Int)

    @Serializable
    data class WizardState(
        @Contextual val kioskId: UUID,
        val step: Int,
        val fullName: String,
        val plan: Plan,
        val newsletter: Boolean,
        @Contextual val monthlyFee: BigDecimal,
        val memberSince: Long,
        val interests: List<String>,
        val answers: Map<String, String>,
        val referralCode: String?,
        val address: Address?,
    )

    @Test
    fun wizardStateRoundTripsThroughReflectionSerializer() = testApplication {
        val kioskId: UUID = UUID.fromString("3f2b8c1e-9a4d-4e57-b0c2-6d1f7a9e8b30")

        application {
            install(Sessions) {
                header<WizardState>("X-Wizard") {
                    serializer = reflectionSessionSerializer()
                }
            }
            routing {
                post("/wizard/start") {
                    val fullName: String = call.request.queryParameters["name"]!!
                    call.sessions.set(
                        WizardState(
                            kioskId = kioskId,
                            step = 1,
                            fullName = fullName,
                            plan = Plan.BASIC,
                            newsletter = false,
                            monthlyFee = BigDecimal("9.50"),
                            memberSince = 1_760_000_000_000L,
                            interests = emptyList(),
                            answers = emptyMap(),
                            referralCode = null,
                            address = null,
                        ),
                    )
                    call.respondText("step 1")
                }
                post("/wizard/plan/{plan}") {
                    val state: WizardState? = call.sessions.get<WizardState>()
                    if (state == null) {
                        call.respondText("start first", status = HttpStatusCode.Conflict)
                    } else {
                        val plan: Plan = Plan.valueOf(call.parameters["plan"]!!)
                        val fee: BigDecimal = if (plan == Plan.FAMILY) BigDecimal("19.75") else state.monthlyFee
                        call.sessions.set(
                            state.copy(
                                step = 2,
                                plan = plan,
                                monthlyFee = fee,
                                newsletter = true,
                                interests = listOf("yoga", "swimming"),
                                answers = mapOf("heardFrom" to "friend", "goal" to "stay fit"),
                                referralCode = "FRIEND-77",
                            ),
                        )
                        call.respondText("step 2")
                    }
                }
                post("/wizard/address") {
                    val state: WizardState = call.sessions.get<WizardState>()!!
                    call.sessions.set(state.copy(step = 3, address = Address("Bahnhofstrasse 1", 8001)))
                    call.respondText("step 3")
                }
                get("/wizard/summary") {
                    val state: WizardState? = call.sessions.get<WizardState>()
                    if (state == null) {
                        call.respondText("no wizard", status = HttpStatusCode.NotFound)
                    } else {
                        val address: String = state.address?.let { "${it.street}, ${it.postalCode}" } ?: "-"
                        call.respondText(
                            listOf(
                                state.kioskId,
                                state.step,
                                state.fullName,
                                state.plan,
                                state.newsletter,
                                state.monthlyFee,
                                state.memberSince,
                                state.interests.joinToString("+"),
                                state.answers.toSortedMap().entries.joinToString("+") { "${it.key}=${it.value}" },
                                state.referralCode ?: "-",
                                address,
                            ).joinToString("|"),
                        )
                    }
                }
            }
        }

        assertEquals(HttpStatusCode.NotFound, client.get("/wizard/summary").status)
        assertEquals(HttpStatusCode.Conflict, client.post("/wizard/plan/FAMILY").status)

        val started: HttpResponse = client.post("/wizard/start?name=Ada%20Lovelace")
        assertEquals("step 1", started.bodyAsText())
        val firstState: String = started.headers["X-Wizard"]!!
        assertTrue(firstState.contains("step=%23i1"), "the reflection format tags each property: $firstState")
        assertTrue(firstState.contains("plan=%23sBASIC"), "enums are written by name: $firstState")
        assertTrue(firstState.contains("referralCode=%23n"), "null properties are tagged as null: $firstState")

        val firstSummary: String = client.get("/wizard/summary") { header("X-Wizard", firstState) }.bodyAsText()
        assertEquals("$kioskId|1|Ada Lovelace|BASIC|false|9.50|1760000000000|||-|-", firstSummary)

        val planned: HttpResponse = client.post("/wizard/plan/FAMILY") { header("X-Wizard", firstState) }
        assertEquals("step 2", planned.bodyAsText())
        val secondState: String = planned.headers["X-Wizard"]!!

        val addressed: HttpResponse = client.post("/wizard/address") { header("X-Wizard", secondState) }
        assertEquals("step 3", addressed.bodyAsText())
        val thirdState: String = addressed.headers["X-Wizard"]!!
        assertTrue(thirdState.contains("Bahnhofstrasse"), "the nested address travels in the header: $thirdState")

        val summary: String = client.get("/wizard/summary") { header("X-Wizard", thirdState) }.bodyAsText()
        assertEquals(
            "$kioskId|3|Ada Lovelace|FAMILY|true|19.75|1760000000000|yoga+swimming|goal=stay fit+heardFrom=friend" +
                "|FRIEND-77|Bahnhofstrasse 1, 8001",
            summary,
        )

        val replayedOld: String = client.get("/wizard/summary") { header("X-Wizard", secondState) }.bodyAsText()
        assertTrue(replayedOld.startsWith("$kioskId|2|Ada Lovelace|FAMILY|true|19.75|"), replayedOld)
        assertTrue(replayedOld.endsWith("|FRIEND-77|-"), "the older header still lacks the address: $replayedOld")

        val unchanged: HttpResponse = client.get("/wizard/summary") { header("X-Wizard", thirdState) }
        assertNull(unchanged.headers["X-Wizard"], "reading the session does not resend it")
    }
}
