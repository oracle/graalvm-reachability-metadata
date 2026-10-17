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
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.encodeCookieValue
import io.ktor.http.formUrlEncode
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import io.ktor.server.sessions.get
import io.ktor.server.sessions.serialization.KotlinxBackwardCompatibleSessionSerializer
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Legacy Ktor 1.x session wire format read and written by the backward-compatible
 * kotlinx-serialization session serializer.
 *
 * A loyalty program migrated its storefront from Ktor 1.x and still has members whose
 * `REWARDS` cookie was written by the old release in the type-tagged `key=#<tag><value>` form.
 * The new release decodes those cookies, credits a purchase, and writes the card back in the
 * same legacy form so that nodes still running the old release during the rollout keep reading
 * it. A cookie the old release could never have produced reads back as no session and is expired.
 */
class LegacyFormatRewardsCardSessionTest {

    enum class Tier { SILVER, GOLD }

    @Serializable
    data class RewardsCard(
        val member: String,
        val points: Int,
        val lifetimeCents: Long,
        val paperless: Boolean,
        val tier: Tier,
        val stores: List<String>,
        val referralCode: String? = null,
    )

    @Test
    fun legacyCookieIsReadUpdatedAndWrittenBackInTheLegacyFormat() = testApplication {
        application {
            install(Sessions) {
                cookie<RewardsCard>(COOKIE_NAME) {
                    cookie.path = "/"
                    cookie.httpOnly = true
                    serializer = KotlinxBackwardCompatibleSessionSerializer()
                }
            }
            routing {
                get("/card") {
                    val card: RewardsCard? = call.sessions.get<RewardsCard>()
                    if (card == null) {
                        call.respondText("no card", status = HttpStatusCode.Unauthorized)
                    } else {
                        val referral: String = card.referralCode ?: "none"
                        call.respondText(
                            "${card.member} ${card.tier} ${card.points}pts " +
                                "stores=${card.stores.joinToString(",")} " +
                                "paperless=${card.paperless} referral=$referral",
                        )
                    }
                }
                post("/purchase") {
                    val card: RewardsCard? = call.sessions.get<RewardsCard>()
                    if (card == null) {
                        call.respondText("no card", status = HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val store: String = call.request.queryParameters["store"]!!
                    val amountCents: Long = call.request.queryParameters["amountCents"]!!.toLong()
                    val lifetime: Long = card.lifetimeCents + amountCents
                    val updated: RewardsCard = card.copy(
                        points = card.points + (amountCents / 100L).toInt(),
                        lifetimeCents = lifetime,
                        tier = if (lifetime >= GOLD_THRESHOLD_CENTS) Tier.GOLD else card.tier,
                        stores = if (store in card.stores) card.stores else card.stores + store,
                    )
                    call.sessions.set(updated)
                    call.respondText("${updated.tier} ${updated.points}pts")
                }
            }
        }

        // A card exactly as the Ktor 1.x release wrote it: form-encoded, every value type-tagged.
        val legacyFields: String = parameters {
            append("member", "#sAna Lee")
            append("points", "#i120")
            append("lifetimeCents", "#l9850")
            append("paperless", "#bot")
            append("tier", "#sSILVER")
            append("stores", "#cl#sOslo&#sBergen")
            append("referralCode", "#n")
        }.formUrlEncode()
        val legacyCookie: String = "$COOKIE_NAME=${encodeCookieValue(legacyFields, CookieEncoding.URI_ENCODING)}"

        val shown: HttpResponse = client.get("/card") { header(HttpHeaders.Cookie, legacyCookie) }
        assertEquals(HttpStatusCode.OK, shown.status)
        assertEquals("Ana Lee SILVER 120pts stores=Oslo,Bergen paperless=true referral=none", shown.bodyAsText())

        val purchase: HttpResponse = client.post("/purchase?store=Trondheim&amountCents=1500") {
            header(HttpHeaders.Cookie, legacyCookie)
        }
        assertEquals(HttpStatusCode.OK, purchase.status)
        assertEquals("GOLD 135pts", purchase.bodyAsText())

        val written: Cookie = rewardsCookie(purchase)
        assertEquals("/", written.path)
        // Parsing the Set-Cookie header already undoes the cookie's URI encoding.
        val fields: Parameters = parseQueryString(written.value)
        assertEquals("#sAna Lee", fields["member"])
        assertEquals("#i135", fields["points"])
        assertEquals("#l11350", fields["lifetimeCents"])
        assertEquals("#bot", fields["paperless"])
        assertEquals("#sGOLD", fields["tier"])
        assertEquals("#cl#sOslo&#sBergen&#sTrondheim", fields["stores"])
        assertEquals("#n", fields["referralCode"])

        // The rewritten cookie is still understood on the next request.
        val reread: HttpResponse = client.get("/card") {
            val value: String = encodeCookieValue(written.value, CookieEncoding.URI_ENCODING)
            header(HttpHeaders.Cookie, "$COOKIE_NAME=$value")
        }
        assertEquals(
            "Ana Lee GOLD 135pts stores=Oslo,Bergen,Trondheim paperless=true referral=none",
            reread.bodyAsText(),
        )

        val anonymous: HttpResponse = client.get("/card")
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)

        // Plain JSON was never a format the old release wrote: it reads as no card and is expired.
        val foreign: String = encodeCookieValue("{\"member\":\"Mallory\"}", CookieEncoding.URI_ENCODING)
        val rejected: HttpResponse = client.get("/card") { header(HttpHeaders.Cookie, "$COOKIE_NAME=$foreign") }
        assertEquals(HttpStatusCode.Unauthorized, rejected.status)
        val expired: Cookie = rewardsCookie(rejected)
        assertEquals("", expired.value)
        assertEquals(0, expired.maxAge)
    }

    private fun rewardsCookie(response: HttpResponse): Cookie {
        val header: String? = response.headers.getAll(HttpHeaders.SetCookie)
            ?.firstOrNull { it.startsWith("$COOKIE_NAME=") }
        assertNotNull(header, "the response sets the rewards cookie")
        return parseServerSetCookieHeader(header!!)
    }

    private companion object {
        const val COOKIE_NAME: String = "REWARDS"
        const val GOLD_THRESHOLD_CENTS: Long = 10_000L
    }
}
