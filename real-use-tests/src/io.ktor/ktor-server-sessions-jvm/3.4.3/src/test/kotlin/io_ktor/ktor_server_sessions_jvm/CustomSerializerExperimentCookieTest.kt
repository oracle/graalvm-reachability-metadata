/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_sessions_jvm

import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.SessionSerializer
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Application-defined `SessionSerializer` that encodes the session in its own wire format.
 *
 * A marketing site pins each visitor to A/B experiment variants in an `EXPERIMENTS` cookie whose
 * value an edge cache also parses, so the app writes a compact `v1;visitor;exp=variant,...` string
 * with a hand-written serializer instead of JSON. The scenario checks the exact cookie text, that
 * the assignments round-trip across requests, and that an unreadable cookie counts as no session.
 */
class CustomSerializerExperimentCookieTest {

    data class ExperimentAssignments(val visitor: String, val variants: Map<String, String>)

    /** Writes `v1;<visitor>;<experiment>=<variant>,...` with experiments in sorted order. */
    class AssignmentsSerializer : SessionSerializer<ExperimentAssignments> {
        override fun serialize(session: ExperimentAssignments): String {
            val pairs: String = session.variants.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
            return "$VERSION;${session.visitor};$pairs"
        }

        override fun deserialize(text: String): ExperimentAssignments {
            val parts: List<String> = text.split(";")
            require(parts.size == 3 && parts[0] == VERSION) { "unsupported experiments cookie: $text" }
            val variants: Map<String, String> = parts[2].split(",").filter { it.isNotEmpty() }.associate { pair ->
                val (experiment: String, variant: String) = pair.split("=", limit = 2)
                experiment to variant
            }
            return ExperimentAssignments(parts[1], variants)
        }

        companion object {
            const val VERSION: String = "v1"
        }
    }

    @Test
    fun variantsAreStoredInTheApplicationWireFormatAndBadCookiesAreIgnored() = testApplication {
        val serializer = AssignmentsSerializer()

        application {
            install(Sessions) {
                cookie<ExperimentAssignments>("EXPERIMENTS") {
                    this.serializer = serializer
                    cookie.path = "/"
                }
            }
            routing {
                post("/experiments/{name}/{variant}") {
                    val current: ExperimentAssignments = call.sessions.get<ExperimentAssignments>()
                        ?: ExperimentAssignments(visitor = "visitor-7", variants = emptyMap())
                    val name: String = call.parameters["name"]!!
                    val variant: String = call.parameters["variant"]!!
                    call.sessions.set(current.copy(variants = current.variants + (name to variant)))
                    call.respondText("$name -> $variant")
                }
                get("/landing") {
                    val assignments: ExperimentAssignments? = call.sessions.get<ExperimentAssignments>()
                    if (assignments == null) {
                        call.respondText("control page")
                    } else {
                        val hero: String = assignments.variants["hero"] ?: "default"
                        val pricing: String = assignments.variants["pricing"] ?: "default"
                        call.respondText("${assignments.visitor}: hero=$hero pricing=$pricing")
                    }
                }
            }
        }

        val browser = createClient { install(HttpCookies) }

        val enrolled: HttpResponse = browser.post("/experiments/pricing/annual-first")
        assertEquals("pricing -> annual-first", enrolled.bodyAsText())
        val firstValue: String = cookieValue(enrolled)
        assertEquals("v1;visitor-7;pricing=annual-first", firstValue)

        val second: HttpResponse = browser.post("/experiments/hero/video")
        assertEquals("v1;visitor-7;hero=video,pricing=annual-first", cookieValue(second))
        assertEquals("visitor-7: hero=video pricing=annual-first", browser.get("/landing").bodyAsText())

        val decoded: ExperimentAssignments = serializer.deserialize(cookieValue(second))
        assertEquals(mapOf("hero" to "video", "pricing" to "annual-first"), decoded.variants)

        val handWritten: String = client.get("/landing") {
            header(HttpHeaders.Cookie, "EXPERIMENTS=v1%3Bvisitor-9%3Bhero%3Dstatic")
        }.bodyAsText()
        assertEquals("visitor-9: hero=static pricing=default", handWritten)

        val legacy: String = client.get("/landing") {
            header(HttpHeaders.Cookie, "EXPERIMENTS={\"visitor\":\"old\"}")
        }.bodyAsText()
        assertEquals("control page", legacy, "a cookie the serializer rejects is treated as absent")

        val reEnrolled: HttpResponse = client.post("/experiments/hero/carousel") {
            header(HttpHeaders.Cookie, "EXPERIMENTS=v0;stale")
        }
        assertEquals("v1;visitor-7;hero=carousel", cookieValue(reEnrolled))
    }

    private fun cookieValue(response: HttpResponse): String {
        val setCookie: String = response.headers[HttpHeaders.SetCookie]!!
        val cookie = parseServerSetCookieHeader(setCookie)
        assertEquals("EXPERIMENTS", cookie.name)
        assertEquals(SEVEN_DAYS_IN_SECONDS, cookie.maxAge, "the default session cookie lifetime applies")
        return cookie.value.decodeURLQueryComponent()
    }

    private companion object {
        const val SEVEN_DAYS_IN_SECONDS: Int = 7 * 24 * 60 * 60
    }
}
