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
import io.ktor.server.sessions.serialization.KotlinxSessionSerializer
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Session serialization through `KotlinxSessionSerializer` with an application-chosen JSON
 * `StringFormat`.
 *
 * A checkout wizard keeps its progress in an `X-Checkout` header session. The session value is
 * a sealed hierarchy of wizard steps encoded as JSON with a custom class discriminator, so a
 * single-page client can read the step it is on straight from the header, and the server
 * decodes the right subtype on every request and advances the wizard.
 */
class JsonFormatPolymorphicSessionTest {

    @Serializable
    data class Address(val street: String, val city: String, val postcode: String)

    @Serializable
    sealed class CheckoutStep {
        abstract val orderId: String
    }

    @Serializable
    @SerialName("shipping")
    data class ShippingStep(override val orderId: String, val itemSkus: List<String>) : CheckoutStep()

    @Serializable
    @SerialName("payment")
    data class PaymentStep(
        override val orderId: String,
        val itemSkus: List<String>,
        val shipTo: Address,
        val express: Boolean = false,
    ) : CheckoutStep()

    @Serializable
    @SerialName("confirmed")
    data class ConfirmedStep(override val orderId: String, val receipt: String) : CheckoutStep()

    private val wizardJson: Json = Json {
        classDiscriminator = "step"
        encodeDefaults = true
    }

    @Test
    fun wizardStepsRoundTripAsPolymorphicJsonInHeader() = testApplication {
        application {
            install(Sessions) {
                header<CheckoutStep>("X-Checkout") {
                    serializer = KotlinxSessionSerializer(wizardJson)
                }
            }
            routing {
                post("/checkout/{orderId}") {
                    val skus: List<String> = call.request.queryParameters.getAll("sku").orEmpty()
                    call.sessions.set<CheckoutStep>(ShippingStep(call.parameters["orderId"]!!, skus))
                    call.respondText("shipping")
                }
                post("/checkout/shipping") {
                    when (val step: CheckoutStep? = call.sessions.get<CheckoutStep>()) {
                        is ShippingStep -> {
                            val q = call.request.queryParameters
                            val address = Address(q["street"]!!, q["city"]!!, q["postcode"]!!)
                            val express: Boolean = q["express"] == "true"
                            call.sessions.set<CheckoutStep>(PaymentStep(step.orderId, step.itemSkus, address, express))
                            call.respondText("payment")
                        }
                        else -> call.respondText("not at shipping: $step", status = HttpStatusCode.Conflict)
                    }
                }
                post("/checkout/pay") {
                    when (val step: CheckoutStep? = call.sessions.get<CheckoutStep>()) {
                        is PaymentStep -> {
                            val receipt = "R-${step.orderId}-${step.itemSkus.size}-${step.shipTo.postcode}"
                            call.sessions.set<CheckoutStep>(ConfirmedStep(step.orderId, receipt))
                            call.respondText(if (step.express) "paid express" else "paid standard")
                        }
                        else -> call.respondText("not at payment: $step", status = HttpStatusCode.Conflict)
                    }
                }
                get("/checkout") {
                    val text: String = when (val step: CheckoutStep? = call.sessions.get<CheckoutStep>()) {
                        null -> "none"
                        is ShippingStep -> "shipping ${step.orderId} ${step.itemSkus}"
                        is PaymentStep -> "payment ${step.orderId} to ${step.shipTo.city}"
                        is ConfirmedStep -> "confirmed ${step.receipt}"
                    }
                    call.respondText(text)
                }
            }
        }

        val start: HttpResponse = client.post("/checkout/ORD-7?sku=BOOK-1&sku=PEN-3")
        assertEquals("shipping", start.bodyAsText())
        val shippingHeader: String? = start.headers["X-Checkout"]
        assertNotNull(shippingHeader)
        val shippingJson: JsonObject = Json.parseToJsonElement(shippingHeader!!).jsonObject
        assertEquals("shipping", shippingJson["step"]!!.jsonPrimitive.content)
        assertEquals("ORD-7", shippingJson["orderId"]!!.jsonPrimitive.content)
        assertEquals(listOf("BOOK-1", "PEN-3"), shippingJson["itemSkus"]!!.jsonArray.map { it.jsonPrimitive.content })

        val outOfOrder: HttpResponse = client.post("/checkout/pay") { header("X-Checkout", shippingHeader) }
        assertEquals(HttpStatusCode.Conflict, outOfOrder.status)
        assertEquals("not at payment: ShippingStep(orderId=ORD-7, itemSkus=[BOOK-1, PEN-3])", outOfOrder.bodyAsText())

        val shipped: HttpResponse = client.post("/checkout/shipping?street=1+Main+St&city=Lyon&postcode=69001") {
            header("X-Checkout", shippingHeader)
        }
        assertEquals("payment", shipped.bodyAsText())
        val paymentHeader: String = shipped.headers["X-Checkout"]!!
        val paymentJson: JsonObject = Json.parseToJsonElement(paymentHeader).jsonObject
        assertEquals("payment", paymentJson["step"]!!.jsonPrimitive.content)
        assertEquals("Lyon", paymentJson["shipTo"]!!.jsonObject["city"]!!.jsonPrimitive.content)
        assertEquals("false", paymentJson["express"]!!.jsonPrimitive.content, "encodeDefaults applies: $paymentHeader")

        assertEquals("payment ORD-7 to Lyon", client.get("/checkout") { header("X-Checkout", paymentHeader) }.bodyAsText())

        val paid: HttpResponse = client.post("/checkout/pay") { header("X-Checkout", paymentHeader) }
        assertEquals("paid standard", paid.bodyAsText())
        val confirmedHeader: String = paid.headers["X-Checkout"]!!
        assertEquals("""{"step":"confirmed","orderId":"ORD-7","receipt":"R-ORD-7-2-69001"}""", confirmedHeader)
        assertEquals("confirmed R-ORD-7-2-69001", client.get("/checkout") { header("X-Checkout", confirmedHeader) }.bodyAsText())

        val handWritten = """{"step":"payment","orderId":"ORD-9","itemSkus":["MAP-2"],""" +
            """"shipTo":{"street":"5 Quay","city":"Oslo","postcode":"0150"},"express":true}"""
        assertEquals("paid express", client.post("/checkout/pay") { header("X-Checkout", handWritten) }.bodyAsText())

        assertEquals("none", client.get("/checkout").bodyAsText())
    }
}
