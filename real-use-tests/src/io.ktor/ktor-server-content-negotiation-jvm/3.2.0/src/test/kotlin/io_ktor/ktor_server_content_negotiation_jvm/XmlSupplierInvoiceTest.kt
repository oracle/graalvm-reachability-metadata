/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.xml.xml
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

/**
 * Exercises XML document conversion for a supplier invoice intake and acknowledgement endpoint.
 */
@Timeout(60)
class XmlSupplierInvoiceTest {
    @Test
    fun acceptsInvoiceElementsAndAttributesAndReturnsCalculatedXmlAcknowledgement(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                xml()
            }
            routing {
                post("/supplier/invoices") {
                    val invoice: Invoice = call.receive<Invoice>()
                    val receipt: Acknowledgement = Acknowledgement(
                        invoice.reference,
                        invoice.supplier,
                        invoice.lines.sumOf { it.quantity },
                        invoice.lines.sumOf { it.quantity * it.unitCents }
                    )
                    call.respond(HttpStatusCode.Created, receipt)
                }
            }
        }

        val supplierClient: HttpClient = createClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 20_000
                socketTimeoutMillis = 20_000
            }
        }
        // A partner's wire document mixes attributes, repeated elements, and escaped text.
        val response: HttpResponse = supplierClient.post("/supplier/invoices") {
            contentType(ContentType.Application.Xml)
            accept(ContentType.Application.Xml)
            setBody(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <invoice reference="INV-204">
                  <supplier>Office &amp; Paper</supplier>
                  <line sku="PAD-A4" quantity="2" unitCents="375"/>
                  <line sku="PEN-BLUE" quantity="3" unitCents="120"/>
                </invoice>
                """.trimIndent()
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ContentType.Application.Xml, response.contentType()?.withoutParameters())
        val document: String = response.bodyAsText()
        assertTrue(document.contains("<acknowledgement"), document)
        assertTrue(document.contains("Office &amp; Paper"), document)
        val receipt: Acknowledgement = XML.decodeFromString<Acknowledgement>(document)
        assertEquals(Acknowledgement("INV-204", "Office & Paper", 5, 1110), receipt)
    }

    @Serializable
    @XmlSerialName("invoice", "", "")
    data class Invoice(
        val reference: String,
        @XmlElement(true) val supplier: String,
        @XmlElement(true) @XmlSerialName("line", "", "") val lines: List<InvoiceLine>
    )

    @Serializable
    data class InvoiceLine(val sku: String, val quantity: Int, val unitCents: Int)

    @Serializable
    @XmlSerialName("acknowledgement", "", "")
    data class Acknowledgement(
        val reference: String,
        @XmlElement(true) val supplier: String,
        val acceptedUnits: Int,
        val totalCents: Int
    )
}
