/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_server_content_negotiation_jvm

import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentTypeWithQuality
import io.ktor.server.plugins.contentnegotiation.suitableCharset
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

public class KtorServerContentNegotiationJvmTest {
    @Test
    fun customConverterDeserializesRequestBodiesByContentType(): Unit = testApplication {
        val converter = MessageConverter()
        application {
            install(ContentNegotiation) {
                register(MessageContentType, converter)
            }
            routing {
                post("/messages") {
                    val message = call.receive<Message>()
                    call.respondText("received:${message.name}:${message.count}")
                }
            }
        }

        val response = client.post("/messages") {
            contentType(MessageContentType.withCharset(StandardCharsets.ISO_8859_1))
            setBody("name=request;count=7")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("received:request:7")
        assertThat(converter.deserializedTypes).contains(Message::class)
        assertThat(converter.deserializationCharsets).contains(StandardCharsets.ISO_8859_1)
    }

    @Test
    fun responseConversionHonorsAcceptQualityAndAcceptCharset(): Unit = testApplication {
        val messageConverter = MessageConverter(prefix = "message")
        val alternateConverter = MessageConverter(prefix = "alternate")
        application {
            install(ContentNegotiation) {
                register(MessageContentType, messageConverter)
                register(AlternateMessageContentType, alternateConverter)
            }
            routing {
                get("/messages") {
                    call.respond(Message("quality", 11))
                }
            }
        }

        val response = client.get("/messages") {
            header(HttpHeaders.Accept, "$MessageContentType; q=0.2, $AlternateMessageContentType; q=0.9")
            header(HttpHeaders.AcceptCharset, "UTF-16")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(AlternateMessageContentType.toString())
        assertThat(response.bodyAsText()).isEqualTo("alternate:name=quality;count=11")
        assertThat(messageConverter.serializedTypes).isEmpty()
        assertThat(alternateConverter.serializedTypes).contains(Message::class)
        assertThat(alternateConverter.serializationCharsets).contains(StandardCharsets.UTF_16)
    }

    @Test
    fun acceptContributorCanSelectConverterFromApplicationState(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                accept { call, acceptedTypes ->
                    if (call.request.queryParameters["format"] == "alternate") {
                        listOf(ContentTypeWithQuality(AlternateMessageContentType))
                    } else {
                        acceptedTypes
                    }
                }
                register(MessageContentType, MessageConverter(prefix = "message"))
                register(AlternateMessageContentType, MessageConverter(prefix = "alternate"))
            }
            routing {
                get("/negotiated") {
                    call.respond(Message("selected", 13))
                }
            }
        }

        val response = client.get("/negotiated?format=alternate") {
            accept(MessageContentType)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(AlternateMessageContentType.toString())
        assertThat(response.bodyAsText()).isEqualTo("alternate:name=selected;count=13")
    }

    @Test
    fun acceptHeaderComplianceRejectsConvertedContentWithUnexpectedContentType(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                checkAcceptHeaderCompliance = true
                register(AlternateMessageContentType, MislabeledMessageConverter)
            }
            routing {
                get("/strict") {
                    call.respond(Message("strict", 17))
                }
            }
        }

        val response = client.get("/strict") {
            accept(AlternateMessageContentType)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotAcceptable)
        assertThat(response.bodyAsText()).isEmpty()
    }

    @Test
    fun removedIgnoredTypeLetsConverterHandleStringResponses(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                removeIgnoredType<String>()
                register(MessageContentType, MessageConverter(convertStrings = true))
            }
            routing {
                get("/string") {
                    call.respond("converted")
                }
            }
        }

        val response = client.get("/string") {
            accept(MessageContentType)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(MessageContentType.toString())
        assertThat(response.bodyAsText()).isEqualTo("string=converted")
    }

    @Test
    fun clearIgnoredTypesLetsConverterHandleByteArrayResponses(): Unit = testApplication {
        application {
            install(ContentNegotiation) {
                clearIgnoredTypes()
                register(MessageContentType, ByteArrayConverter)
            }
            routing {
                get("/bytes") {
                    call.respond("converted-bytes".toByteArray(StandardCharsets.UTF_8))
                }
            }
        }

        val response = client.get("/bytes") {
            accept(MessageContentType)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(MessageContentType.toString())
        assertThat(response.bodyAsText()).isEqualTo("bytes=converted-bytes")
    }

    @Test
    fun suitableCharsetUsesAcceptCharsetQualityOrder(): Unit = testApplication {
        application {
            routing {
                get("/charset") {
                    call.respondText(call.suitableCharset(StandardCharsets.ISO_8859_1).name())
                }
            }
        }

        val response = client.get("/charset") {
            header(HttpHeaders.AcceptCharset, "UTF-16; q=0.4, UTF-8; q=0.9, ISO-8859-1; q=0.1")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo(StandardCharsets.UTF_8.name())
    }

    @Test
    fun nullableReceiveReturnsNullWhenConverterConsumesBodyWithoutValue(): Unit = testApplication {
        val converter = NullMessageConverter()
        application {
            install(ContentNegotiation) {
                register(MessageContentType, converter)
            }
            routing {
                post("/nullable") {
                    val message = call.receiveNullable<Message?>()
                    call.respondText(if (message == null) "received:null" else "received:${message.name}")
                }
            }
        }

        val response = client.post("/nullable") {
            contentType(MessageContentType)
            setBody("not-a-message")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("received:null")
        assertThat(converter.deserializedTypes).contains(Message::class)
    }

    @Test
    fun responseConversionFallsBackWhenMatchingConverterCannotSerialize(): Unit = testApplication {
        val refusingConverter = RefusingMessageConverter()
        application {
            install(ContentNegotiation) {
                register(MessageContentType, refusingConverter)
                register(MessageContentType, MessageConverter(prefix = "fallback"))
            }
            routing {
                get("/fallback") {
                    call.respond(Message("next", 19))
                }
            }
        }

        val response = client.get("/fallback") {
            accept(MessageContentType)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.headers[HttpHeaders.ContentType]).startsWith(MessageContentType.toString())
        assertThat(response.bodyAsText()).isEqualTo("fallback:name=next;count=19")
        assertThat(refusingConverter.serializedTypes).contains(Message::class)
    }

    private data class Message(val name: String, val count: Int)

    private class MessageConverter(
        private val prefix: String? = null,
        private val convertStrings: Boolean = false,
    ) : ContentConverter {
        val serializedTypes: MutableList<KClass<*>> = mutableListOf()
        val deserializedTypes: MutableList<KClass<*>> = mutableListOf()
        val serializationCharsets: MutableList<Charset> = mutableListOf()
        val deserializationCharsets: MutableList<Charset> = mutableListOf()

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): TextContent? {
            serializedTypes += typeInfo.type
            serializationCharsets += charset
            val text = when (value) {
                is Message -> listOfNotNull(prefix, encodeMessage(value)).joinToString(separator = ":")
                is String if convertStrings -> "string=$value"
                else -> return null
            }
            return TextContent(text, contentType.withCharset(charset))
        }

        override suspend fun deserialize(
            charset: Charset,
            typeInfo: TypeInfo,
            content: ByteReadChannel,
        ): Any? {
            deserializedTypes += typeInfo.type
            deserializationCharsets += charset
            val text = String(content.toByteArray(), charset)
            return if (typeInfo.type == Message::class) decodeMessage(text) else null
        }
    }

    private object MislabeledMessageConverter : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): TextContent? {
            return if (value is Message) {
                TextContent(encodeMessage(value), MessageContentType.withCharset(charset))
            } else {
                null
            }
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? = null
    }

    private class RefusingMessageConverter : ContentConverter {
        val serializedTypes: MutableList<KClass<*>> = mutableListOf()

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): TextContent? {
            serializedTypes += typeInfo.type
            return null
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? = null
    }

    private class NullMessageConverter : ContentConverter {
        val deserializedTypes: MutableList<KClass<*>> = mutableListOf()

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): TextContent? = null

        override suspend fun deserialize(
            charset: Charset,
            typeInfo: TypeInfo,
            content: ByteReadChannel,
        ): Any? {
            deserializedTypes += typeInfo.type
            content.toByteArray()
            return null
        }
    }

    private object ByteArrayConverter : ContentConverter {
        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): TextContent? {
            return if (value is ByteArray) {
                TextContent("bytes=${String(value, charset)}", contentType.withCharset(charset))
            } else {
                null
            }
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? = null
    }

    private companion object {
        val MessageContentType: ContentType = ContentType("application", "x-message")
        val AlternateMessageContentType: ContentType = ContentType("application", "x-alternate-message")

        fun encodeMessage(message: Message): String = "name=${message.name};count=${message.count}"

        fun decodeMessage(text: String): Message {
            val parts = text.split(';').associate { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=')
                key to value
            }
            return Message(
                name = requireNotNull(parts["name"]) { "Missing name in $text" },
                count = requireNotNull(parts["count"]) { "Missing count in $text" }.toInt(),
            )
        }
    }
}
