/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_client_content_negotiation_jvm

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.contentnegotiation.exclude
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.ContentTypeMatcher
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

public class Ktor_client_content_negotiation_jvmTest {
    @Test
    fun `content negotiation serializes requests and deserializes responses`(): Unit = runBlocking {
        val converter = MessageConverter()
        val engine = MockEngine { request ->
            assertThat(request.headers[HttpHeaders.Accept]).contains(MessageContentType.toString())
            assertThat(request.headers[HttpHeaders.Accept]).contains("q=0.8")
            assertThat(request.body.contentType?.withoutParameters()).isEqualTo(MessageContentType)
            assertThat(request.body.readText()).isEqualTo("name=client;count=7")

            respond(
                content = "name=server;count=11",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "$MessageContentType; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                defaultAcceptHeaderQValue = 0.8
                register(MessageContentType, converter) { }
            }
        }

        try {
            val response = client.post("https://example.test/messages") {
                contentType(MessageContentType)
                setBody(Message("client", 7))
            }.body<Message>()

            assertThat(response).isEqualTo(Message("server", 11))
            assertThat(converter.serializedTypes).contains(Message::class)
            assertThat(converter.deserializedTypes).contains(Message::class)
        } finally {
            client.close()
        }
    }

    @Test
    fun `json registration accepts vendor json response content types`(): Unit = runBlocking {
        val engine = MockEngine { request ->
            assertThat(request.headers[HttpHeaders.Accept]).contains(ContentType.Application.Json.toString())

            respond(
                content = "name=vendor;count=23",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/vnd.example.message+json; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, MessageConverter()) { }
            }
        }

        try {
            val message = client.get("https://example.test/vendor-json").body<Message>()

            assertThat(message).isEqualTo(Message("vendor", 23))
        } finally {
            client.close()
        }
    }

    @Test
    fun `custom content type matcher deserializes alternate response content type`(): Unit = runBlocking {
        val engine = MockEngine { request ->
            assertThat(request.headers[HttpHeaders.Accept]).contains(MessageContentType.toString())

            respond(
                content = "name=text;count=31",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                val plainTextMatcher = object : ContentTypeMatcher {
                    override fun contains(contentType: ContentType): Boolean = contentType.match(ContentType.Text.Plain)
                }
                register(MessageContentType, MessageConverter(), plainTextMatcher) { }
            }
        }

        try {
            val message = client.get("https://example.test/plain").body<Message>()

            assertThat(message).isEqualTo(Message("text", 31))
        } finally {
            client.close()
        }
    }

    @Test
    fun `excluded content type is omitted from generated accept header`(): Unit = runBlocking {
        val engine = MockEngine { request ->
            val acceptHeader = request.headers[HttpHeaders.Accept].orEmpty()
            assertThat(acceptHeader).contains(AlternateMessageContentType.toString())
            assertThat(acceptHeader).doesNotContain(MessageContentType.toString())

            respond(content = "", status = HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                register(MessageContentType, MessageConverter()) { }
                register(AlternateMessageContentType, MessageConverter()) { }
            }
        }

        try {
            val response = client.get("https://example.test/excluded-accept") {
                exclude(MessageContentType)
            }

            assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        } finally {
            client.close()
        }
    }

    @Test
    fun `removed ignored type lets converter handle string request bodies`(): Unit = runBlocking {
        val engine = MockEngine { request ->
            assertThat(request.body.contentType?.withoutParameters()).isEqualTo(MessageContentType)
            assertThat(request.body.readText()).isEqualTo("string=converted")

            respond(
                content = "name=accepted;count=1",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "$MessageContentType; charset=UTF-8"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                removeIgnoredType<String>()
                register(MessageContentType, MessageConverter(convertStrings = true)) { }
            }
        }

        try {
            val message = client.post("https://example.test/strings") {
                contentType(MessageContentType)
                setBody("converted")
            }.body<Message>()

            assertThat(message).isEqualTo(Message("accepted", 1))
        } finally {
            client.close()
        }
    }

    private data class Message(val name: String, val count: Int)

    private class MessageConverter(
        private val convertStrings: Boolean = false,
    ) : ContentConverter {
        val serializedTypes = mutableListOf<KClass<*>>()
        val deserializedTypes = mutableListOf<KClass<*>>()

        override suspend fun serialize(
            contentType: ContentType,
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?,
        ): OutgoingContent? {
            serializedTypes += typeInfo.type
            val text = when (value) {
                is Message -> encodeMessage(value)
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
            val text = String(content.toByteArray(), charset)
            return if (typeInfo.type == Message::class) decodeMessage(text) else null
        }
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

        suspend fun OutgoingContent.readText(): String = String(toByteArray(), StandardCharsets.UTF_8)
    }
}
