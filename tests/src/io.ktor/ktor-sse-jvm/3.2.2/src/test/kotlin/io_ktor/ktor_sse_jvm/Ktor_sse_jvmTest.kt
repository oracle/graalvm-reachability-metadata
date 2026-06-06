/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_ktor.ktor_sse_jvm

import io.ktor.sse.ServerSentEvent
import io.ktor.sse.ServerSentEventMetadata
import io.ktor.sse.TypedServerSentEvent
import io.ktor.utils.io.InternalAPI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

public class Ktor_sse_jvmTest {
    @Test
    fun `server sent event exposes metadata and renders wire format`() {
        val event = ServerSentEvent(
            data = "first line\nsecond line\rthird line\r\nfourth line",
            event = "inventory-update",
            id = "event-42",
            retry = 1_500,
            comments = "keep-alive\nclient-visible",
        )

        assertMetadata(
            metadata = event,
            expectedData = "first line\nsecond line\rthird line\r\nfourth line",
            expectedEvent = "inventory-update",
            expectedId = "event-42",
            expectedRetry = 1_500,
            expectedComments = "keep-alive\nclient-visible",
        )
        assertThat(event.toString()).isEqualTo(
            "event: inventory-update\r\n" +
                "data: first line\r\n" +
                "data: second line\r\n" +
                "data: third line\r\n" +
                "data: fourth line\r\n" +
                "id: event-42\r\n" +
                "retry: 1500\r\n" +
                ": keep-alive\r\n" +
                ": client-visible\r\n",
        )
    }

    @Test
    fun `server sent event preserves empty fields and blank lines in wire format`() {
        val event = ServerSentEvent(
            data = "first line\n\nthird line\n",
            event = "",
            id = "",
            retry = 0,
            comments = "\nheartbeat\n",
        )

        assertMetadata(
            metadata = event,
            expectedData = "first line\n\nthird line\n",
            expectedEvent = "",
            expectedId = "",
            expectedRetry = 0,
            expectedComments = "\nheartbeat\n",
        )
        assertThat(event.toString()).isEqualTo(
            "event: \r\n" +
                "data: first line\r\n" +
                "data: \r\n" +
                "data: third line\r\n" +
                "data: \r\n" +
                "id: \r\n" +
                "retry: 0\r\n" +
                ": \r\n" +
                ": heartbeat\r\n" +
                ": \r\n",
        )
    }

    @Test
    fun `server sent event supports defaults copies destructuring and equality`() {
        val emptyEvent = ServerSentEvent()
        assertMetadata(
            metadata = emptyEvent,
            expectedData = null,
            expectedEvent = null,
            expectedId = null,
            expectedRetry = null,
            expectedComments = null,
        )
        assertThat(emptyEvent.toString()).isEmpty()

        val original = ServerSentEvent(data = "payload", event = "created", id = "1", retry = 250, comments = "ready")
        val (data, event, id, retry, comments) = original
        assertThat(data).isEqualTo("payload")
        assertThat(event).isEqualTo("created")
        assertThat(id).isEqualTo("1")
        assertThat(retry).isEqualTo(250)
        assertThat(comments).isEqualTo("ready")

        val equalCopy = original.copy()
        val changedCopy = original.copy(data = "updated", retry = 500, comments = null)
        assertThat(equalCopy).isEqualTo(original)
        assertThat(equalCopy.hashCode()).isEqualTo(original.hashCode())
        assertMetadata(
            metadata = changedCopy,
            expectedData = "updated",
            expectedEvent = "created",
            expectedId = "1",
            expectedRetry = 500,
            expectedComments = null,
        )
        assertThat(changedCopy).isNotEqualTo(original)
        assertThat(changedCopy.toString()).isEqualTo(
            "event: created\r\n" +
                "data: updated\r\n" +
                "id: 1\r\n" +
                "retry: 500\r\n",
        )
    }

    @Test
    fun `typed server sent event keeps typed data and serializes to wire format`() {
        val event = TypedServerSentEvent(
            data = Payload(id = 7, name = "kotlin"),
            event = "typed",
            id = "typed-7",
            retry = 2_000,
            comments = "typed-comment",
        )

        assertMetadata(
            metadata = event,
            expectedData = Payload(id = 7, name = "kotlin"),
            expectedEvent = "typed",
            expectedId = "typed-7",
            expectedRetry = 2_000,
            expectedComments = "typed-comment",
        )
        assertThat(event.toString()).isEqualTo(
            "TypedServerSentEvent(data=Payload(id=7, name=kotlin), event=typed, id=typed-7, retry=2000, " +
                "comments=typed-comment)",
        )
    }

    @Test
    @OptIn(InternalAPI::class)
    fun `typed server sent event serializes typed payload with custom serializer`() {
        var serializedPayloads = 0
        val event = TypedServerSentEvent(
            data = Payload(id = 12, name = "serialized"),
            event = "payload-ready",
            id = "payload-12",
            retry = 3_000,
            comments = "serialized-comment",
        )

        val wireFormat = event.toString { payload: Payload ->
            serializedPayloads += 1
            "${payload.id}:${payload.name}\nconfirmed"
        }

        assertThat(serializedPayloads).isEqualTo(1)
        assertThat(wireFormat).isEqualTo(
            "event: payload-ready\r\n" +
                "data: 12:serialized\r\n" +
                "data: confirmed\r\n" +
                "id: payload-12\r\n" +
                "retry: 3000\r\n" +
                ": serialized-comment\r\n",
        )
    }

    @Test
    @OptIn(InternalAPI::class)
    fun `typed server sent event custom serializer is not used when typed payload is absent`() {
        val event = TypedServerSentEvent<Payload>(event = "heartbeat", id = "empty-payload")

        val wireFormat = event.toString { payload: Payload ->
            throw AssertionError("Serializer should not be called for absent payload $payload")
        }

        assertThat(wireFormat).isEqualTo(
            "event: heartbeat\r\n" +
                "id: empty-payload\r\n",
        )
    }

    @Test
    fun `typed server sent event supports defaults copies destructuring and equality`() {
        val emptyEvent = TypedServerSentEvent<Payload>()
        assertMetadata(
            metadata = emptyEvent,
            expectedData = null,
            expectedEvent = null,
            expectedId = null,
            expectedRetry = null,
            expectedComments = null,
        )
        assertThat(emptyEvent.toString()).isEqualTo(
            "TypedServerSentEvent(data=null, event=null, id=null, retry=null, comments=null)",
        )

        val original = TypedServerSentEvent(data = Payload(id = 1, name = "one"), event = "created", id = "1")
        val (data, event, id, retry, comments) = original
        assertThat(data).isEqualTo(Payload(id = 1, name = "one"))
        assertThat(event).isEqualTo("created")
        assertThat(id).isEqualTo("1")
        assertThat(retry).isNull()
        assertThat(comments).isNull()

        val equalCopy = original.copy()
        val changedCopy = original.copy(data = Payload(id = 2, name = "two"), retry = 750, comments = "updated")
        assertThat(equalCopy).isEqualTo(original)
        assertThat(equalCopy.hashCode()).isEqualTo(original.hashCode())
        assertMetadata(
            metadata = changedCopy,
            expectedData = Payload(id = 2, name = "two"),
            expectedEvent = "created",
            expectedId = "1",
            expectedRetry = 750,
            expectedComments = "updated",
        )
        assertThat(changedCopy).isNotEqualTo(original)
        assertThat(changedCopy.toString()).isEqualTo(
            "TypedServerSentEvent(data=Payload(id=2, name=two), event=created, id=1, retry=750, comments=updated)",
        )
    }

    private fun <T> assertMetadata(
        metadata: ServerSentEventMetadata<T>,
        expectedData: T?,
        expectedEvent: String?,
        expectedId: String?,
        expectedRetry: Long?,
        expectedComments: String?,
    ) {
        assertThat(metadata.data).isEqualTo(expectedData)
        assertThat(metadata.event).isEqualTo(expectedEvent)
        assertThat(metadata.id).isEqualTo(expectedId)
        assertThat(metadata.retry).isEqualTo(expectedRetry)
        assertThat(metadata.comments).isEqualTo(expectedComments)
    }

    private data class Payload(val id: Int, val name: String)
}
