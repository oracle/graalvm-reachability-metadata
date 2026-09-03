/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_jvm

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.withCharset
import io.ktor.http.content.OutputStreamContent
import io.ktor.http.content.WriterContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

public class BlockingBridgeKtTest {
    @Test
    fun outputStreamContentWritesBytesThroughBlockingBridge(): Unit = runBlocking {
        withTimeout(10_000) {
            val payload: ByteArray = "streamed ktor body".toByteArray(StandardCharsets.UTF_8)
            val content: OutputStreamContent = OutputStreamContent(
                body = {
                    write(payload)
                    flush()
                },
                contentType = ContentType.Application.OctetStream,
                status = HttpStatusCode.Accepted,
                contentLength = payload.size.toLong()
            )
            val channel: ByteChannel = ByteChannel(autoFlush = true)

            content.writeTo(channel)
            val writtenBytes: ByteArray = channel.readByteArray(payload.size)

            assertThat(writtenBytes).isEqualTo(payload)
            assertThat(content.contentType).isEqualTo(ContentType.Application.OctetStream)
            assertThat(content.status).isEqualTo(HttpStatusCode.Accepted)
            assertThat(content.contentLength).isEqualTo(payload.size.toLong())
        }
    }

    @Test
    fun writerContentEncodesTextThroughBlockingBridge(): Unit = runBlocking {
        withTimeout(10_000) {
            val content: WriterContent = WriterContent(
                body = {
                    write("writer body")
                    append('-')
                    append("Καλημέρα")
                    flush()
                },
                contentType = ContentType.Text.Plain.withCharset(StandardCharsets.UTF_8),
                status = HttpStatusCode.Created
            )
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val expectedText: String = "writer body-Καλημέρα"

            content.writeTo(channel)
            val writtenBytes: ByteArray = channel.readByteArray(
                expectedText.toByteArray(StandardCharsets.UTF_8).size
            )
            val writtenText: String = String(writtenBytes, StandardCharsets.UTF_8)

            assertThat(writtenText).isEqualTo(expectedText)
            assertThat(content.contentType.charset()).isEqualTo(StandardCharsets.UTF_8)
            assertThat(content.status).isEqualTo(HttpStatusCode.Created)
        }
    }
}
