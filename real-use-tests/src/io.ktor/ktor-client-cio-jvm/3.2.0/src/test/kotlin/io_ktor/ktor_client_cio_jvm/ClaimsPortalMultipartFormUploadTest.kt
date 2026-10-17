/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_cio_jvm

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.parameters
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveMultipart
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.server.cio.CIO as ServerCIO

/**
 * HTML form submission over the CIO client engine: URL-encoded forms and multipart/form-data uploads.
 *
 * An insurance claims portal client first signs in through a URL-encoded login form, then files a
 * claim as a multipart upload that carries text fields, a repeated field, an in-memory photo, and a
 * scanned receipt streamed from a channel. The claims service parses each body the way a browser
 * form would be parsed and records what it received, so the test observes exactly what the client
 * put on the wire, including a caller-chosen multipart boundary.
 */
class ClaimsPortalMultipartFormUploadTest {

    /** One multipart part as the claims service parsed it. */
    data class ReceivedPart(
        val name: String,
        val kind: String,
        val fileName: String?,
        val contentType: String?,
        val content: ByteArray,
    )

    /** Every request body the claims service parsed, keyed by the route that parsed it. */
    data class ReceivedForm(val route: String, val contentType: String, val fields: Map<String, List<String>>)

    private lateinit var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private var port: Int = 0

    private val receivedForms: ConcurrentLinkedQueue<ReceivedForm> = ConcurrentLinkedQueue()
    private val receivedParts: ConcurrentLinkedQueue<ReceivedPart> = ConcurrentLinkedQueue()

    @BeforeEach
    fun startServer() {
        server = embeddedServer(ServerCIO, port = 0, host = "127.0.0.1") {
            routing {
                post("/session") {
                    val form: Parameters = call.receiveParameters()
                    receivedForms += ReceivedForm(
                        "/session",
                        call.request.contentType().withoutParameters().toString(),
                        form.entries().associate { it.key to it.value },
                    )
                    val accepted: Boolean = form["username"] == "j.doe@example.com" && form["password"] == PASSWORD
                    if (accepted) {
                        call.respondText("session=${form["username"]}", ContentType.Text.Plain)
                    } else {
                        call.respondText("invalid credentials", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                    }
                }
                post("/claims") {
                    val contentType: ContentType = call.request.contentType()
                    var partCount: Int = 0
                    call.receiveMultipart().forEachPart { part ->
                        partCount++
                        val disposition: ContentDisposition? = part.contentDisposition
                        val received: ReceivedPart = when (part) {
                            is PartData.FormItem -> ReceivedPart(
                                part.name.orEmpty(),
                                "form",
                                null,
                                part.contentType?.toString(),
                                part.value.toByteArray(Charsets.UTF_8),
                            )
                            is PartData.FileItem -> ReceivedPart(
                                part.name.orEmpty(),
                                "file",
                                disposition?.parameter(ContentDisposition.Parameters.FileName),
                                part.contentType?.toString(),
                                part.provider().toByteArray(),
                            )
                            else -> ReceivedPart(part.name.orEmpty(), "other", null, null, ByteArray(0))
                        }
                        receivedParts += received
                        part.dispose()
                    }
                    receivedForms += ReceivedForm(
                        "/claims",
                        contentType.withoutParameters().toString(),
                        mapOf("boundary" to listOfNotNull(contentType.parameter("boundary"))),
                    )
                    call.respondText("claim CLM-2041 filed with $partCount parts", ContentType.Text.Plain,
                        HttpStatusCode.Created)
                }
            }
        }.start(wait = false)
        port = runBlocking { server.engine.resolvedConnectors().first().port }
    }

    @AfterEach
    fun stopServer() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 10_000)
    }

    @Test
    fun claimantSignsInAndFilesClaimWithAttachments() {
        val portalUrl: String = "http://127.0.0.1:$port/"
        val client = HttpClient(ClientCIO) {
            defaultRequest {
                url(portalUrl)
            }
        }

        client.use {
            runBlocking {
                val rejected: HttpResponse = client.submitForm(
                    url = "session",
                    formParameters = parameters {
                        append("username", "j.doe@example.com")
                        append("password", "wrong")
                    },
                )
                assertEquals(HttpStatusCode.Unauthorized, rejected.status)
                assertEquals("invalid credentials", rejected.bodyAsText())

                val login: HttpResponse = client.submitForm(
                    url = "session",
                    formParameters = parameters {
                        append("username", "j.doe@example.com")
                        append("password", PASSWORD)
                        append("remember", "on")
                    },
                )
                assertEquals(HttpStatusCode.OK, login.status)
                assertEquals("session=j.doe@example.com", login.bodyAsText())

                val claim: HttpResponse = client.submitFormWithBinaryData(
                    url = "claims",
                    formData = formData {
                        append("policy", "POL-88-1932")
                        append("description", DESCRIPTION)
                        append("damage", "windshield")
                        append("damage", "left mirror")
                        append(
                            "photo",
                            PHOTO,
                            Headers.build {
                                append(HttpHeaders.ContentType, "image/jpeg")
                                append(HttpHeaders.ContentDisposition, "filename=\"front.jpg\"")
                            },
                        )
                        append(
                            "receipt",
                            ChannelProvider(RECEIPT.size.toLong()) { ByteReadChannel(RECEIPT) },
                            Headers.build {
                                append(HttpHeaders.ContentType, "application/pdf")
                                append(HttpHeaders.ContentDisposition, "filename=\"repair-receipt.pdf\"")
                            },
                        )
                    },
                )
                assertEquals(HttpStatusCode.Created, claim.status)
                assertEquals("claim CLM-2041 filed with 6 parts", claim.bodyAsText())
            }
        }

        val forms: List<ReceivedForm> = receivedForms.toList()
        assertEquals(3, forms.size)
        assertEquals(
            ReceivedForm(
                "/session",
                "application/x-www-form-urlencoded",
                mapOf("username" to listOf("j.doe@example.com"), "password" to listOf("wrong")),
            ),
            forms[0],
        )
        assertEquals(
            ReceivedForm(
                "/session",
                "application/x-www-form-urlencoded",
                mapOf(
                    "username" to listOf("j.doe@example.com"),
                    "password" to listOf(PASSWORD),
                    "remember" to listOf("on"),
                ),
            ),
            forms[1],
        )
        assertEquals("/claims", forms[2].route)
        assertEquals("multipart/form-data", forms[2].contentType)
        assertEquals(1, forms[2].fields.getValue("boundary").size)
        assertTrue(forms[2].fields.getValue("boundary").single().isNotBlank())

        val parts: List<ReceivedPart> = receivedParts.toList()
        assertEquals(
            listOf("policy", "description", "damage", "damage", "photo", "receipt"),
            parts.map { it.name },
        )
        assertEquals(listOf("form", "form", "form", "form", "file", "file"), parts.map { it.kind })
        assertEquals("POL-88-1932", parts[0].content.toString(Charsets.UTF_8))
        assertEquals(DESCRIPTION, parts[1].content.toString(Charsets.UTF_8))
        assertEquals(
            listOf("windshield", "left mirror"),
            parts.subList(2, 4).map { it.content.toString(Charsets.UTF_8) },
        )

        val photo: ReceivedPart = parts[4]
        assertEquals("front.jpg", photo.fileName)
        assertEquals("image/jpeg", photo.contentType)
        assertArrayEquals(PHOTO, photo.content)

        val receipt: ReceivedPart = parts[5]
        assertEquals("repair-receipt.pdf", receipt.fileName)
        assertEquals("application/pdf", receipt.contentType)
        assertArrayEquals(RECEIPT, receipt.content)
    }

    @Test
    fun multipartBodyIsSentWithItsBoundaryAndLength() {
        val client = HttpClient(ClientCIO)

        client.use {
            runBlocking {
                val body = MultiPartFormDataContent(
                    formData {
                        append("policy", "POL-12-0007")
                        append("note", "hail damage, roof and hood")
                    },
                    boundary = "claims-portal-boundary",
                )
                assertEquals("claims-portal-boundary", body.boundary)

                val response: HttpResponse = client.post("http://127.0.0.1:$port/claims") {
                    setBody(body)
                }
                assertEquals(HttpStatusCode.Created, response.status)
                assertEquals("claim CLM-2041 filed with 2 parts", response.bodyAsText())
            }
        }

        assertEquals(
            ReceivedForm("/claims", "multipart/form-data", mapOf("boundary" to listOf("claims-portal-boundary"))),
            receivedForms.single(),
        )
        assertEquals(
            listOf("POL-12-0007", "hail damage, roof and hood"),
            receivedParts.map { it.content.toString(Charsets.UTF_8) },
        )
    }

    private companion object {
        const val PASSWORD: String = "s3cret & safe=yes"

        const val DESCRIPTION: String = "Rear-ended at a red light on 2026-03-14; café parking lot, no injuries."

        /** Binary photo bytes covering every byte value, including CR, LF and dashes. */
        val PHOTO: ByteArray = ByteArray(3 * 256) { index -> index.toByte() }

        /** Scanned receipt large enough to span several channel reads. */
        val RECEIPT: ByteArray = ByteArray(96 * 1024) { index -> ((index * 31 + 7) % 251).toByte() }
    }
}
