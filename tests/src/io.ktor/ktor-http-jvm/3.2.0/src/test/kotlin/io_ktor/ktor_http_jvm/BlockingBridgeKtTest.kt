/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_http_jvm

import io.ktor.http.ContentType
import io.ktor.http.content.OutputStreamContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class BlockingBridgeKtTest {
    @Test
    public fun outputStreamContentWritesThroughBlockingBridge(): Unit = runBlocking {
        withTimeout(TEST_TIMEOUT_MILLIS) {
            val payload: String = "blocking bridge uses the parking probe"
            val payloadBytes: ByteArray = payload.toByteArray(Charsets.UTF_8)
            val channel: ByteChannel = ByteChannel(autoFlush = true)
            val content: OutputStreamContent = OutputStreamContent(
                body = {
                    write(payloadBytes)
                    flush()
                },
                contentType = ContentType.Text.Plain,
                contentLength = payloadBytes.size.toLong()
            )

            val writer: Job = launch {
                try {
                    content.writeTo(channel)
                } finally {
                    channel.flushAndClose()
                }
            }

            val written: String = channel.toByteArray().toString(Charsets.UTF_8)
            writer.join()

            assertThat(written).isEqualTo(payload)
        }
    }

    private companion object {
        private const val TEST_TIMEOUT_MILLIS: Long = 5_000
    }
}
