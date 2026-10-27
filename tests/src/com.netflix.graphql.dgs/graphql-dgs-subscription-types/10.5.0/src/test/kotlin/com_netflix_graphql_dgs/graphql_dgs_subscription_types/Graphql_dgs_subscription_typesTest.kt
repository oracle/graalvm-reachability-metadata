/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_netflix_graphql_dgs.graphql_dgs_subscription_types

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.graphql.types.subscription.DataPayload
import com.netflix.graphql.types.subscription.EmptyPayload
import com.netflix.graphql.types.subscription.Error as SubscriptionError
import com.netflix.graphql.types.subscription.GQL_COMPLETE
import com.netflix.graphql.types.subscription.GQL_CONNECTION_ACK
import com.netflix.graphql.types.subscription.GQL_CONNECTION_ERROR
import com.netflix.graphql.types.subscription.GQL_CONNECTION_INIT
import com.netflix.graphql.types.subscription.GQL_CONNECTION_KEEP_ALIVE
import com.netflix.graphql.types.subscription.GQL_CONNECTION_TERMINATE
import com.netflix.graphql.types.subscription.GQL_DATA
import com.netflix.graphql.types.subscription.GQL_ERROR
import com.netflix.graphql.types.subscription.GQL_START
import com.netflix.graphql.types.subscription.GQL_STOP
import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL
import com.netflix.graphql.types.subscription.GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL
import com.netflix.graphql.types.subscription.OperationMessage
import com.netflix.graphql.types.subscription.QueryPayload
import com.netflix.graphql.types.subscription.SSEDataPayload
import com.netflix.graphql.types.subscription.SSE_GQL_SUBSCRIPTION_DATA
import com.netflix.graphql.types.subscription.websockets.CloseCode
import com.netflix.graphql.types.subscription.websockets.ExecutionResult
import com.netflix.graphql.types.subscription.websockets.Message
import com.netflix.graphql.types.subscription.websockets.MessageType
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

public class Graphql_dgs_subscription_typesTest {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    @Test
    public fun operationMessagesRoundTripWithDeductedPayloadTypes() {
        val messages: List<OperationMessage> = listOf(
            OperationMessage(
                type = "start",
                payload = QueryPayload(
                    variables = mapOf("authorId" to 42),
                    extensions = mapOf("persisted" to true),
                    operationName = "AuthorUpdates",
                    query = "subscription AuthorUpdates { authorUpdated { id name } }",
                    key = "authors",
                ),
                id = "query-1",
            ),
            OperationMessage(
                type = "data",
                payload = DataPayload(
                    data = mapOf("authorUpdated" to mapOf("id" to 42, "name" to "Octavia")),
                    errors = listOf(mapOf("message" to "partial result")),
                ),
                id = "query-1",
            ),
            OperationMessage(type = "connection_init", payload = EmptyPayload),
        )

        val decoded: List<OperationMessage> = messages.map { message: OperationMessage ->
            roundTrip(message, OperationMessage::class.java)
        }

        assertThat(decoded).isEqualTo(messages)
        assertThat(decoded[0].payload).isInstanceOf(QueryPayload::class.java)
        assertThat(decoded[1].payload).isInstanceOf(DataPayload::class.java)
        assertThat(decoded[2].payload).isEqualTo(emptyMap<String, Any?>())
    }

    @Test
    public fun operationMessageDefaultsDistinguishAbsentAndEmptyPayloads() {
        val absentPayload: OperationMessage = mapper.readValue(
            """{"type":"connection_ack"}""",
            OperationMessage::class.java,
        )
        val emptyPayload: OperationMessage = mapper.readValue(
            """{"type":"connection_ack","payload":{}}""",
            OperationMessage::class.java,
        )

        assertThat(absentPayload.payload).isNull()
        assertThat(absentPayload.id).isEmpty()
        assertThat(emptyPayload.payload).isEqualTo(emptyMap<String, Any?>())
        assertThat(emptyPayload.id).isEmpty()
    }

    @Test
    public fun queryPayloadRoundTripsAndAppliesDefaults() {
        val payload: QueryPayload = QueryPayload(
            variables = mapOf("episode" to "JEDI"),
            extensions = mapOf("traceId" to "trace-7"),
            operationName = "Reviews",
            query = "subscription Reviews { reviewAdded { stars commentary } }",
            key = "reviews",
        )

        val decoded: QueryPayload = roundTrip(payload, QueryPayload::class.java)
        val defaults: QueryPayload = mapper.readValue(
            """
            {
              "query": "subscription { reviewAdded { stars } }",
              "ignoredByProtocolModel": true
            }
            """.trimIndent(),
            QueryPayload::class.java,
        )

        assertThat(decoded).isEqualTo(payload)
        assertThat(defaults.variables).isEmpty()
        assertThat(defaults.extensions).isEmpty()
        assertThat(defaults.operationName).isNull()
        assertThat(defaults.key).isEmpty()
    }

    @Test
    public fun dataAndSsePayloadsRoundTripAndApplyDefaults() {
        val dataPayload: DataPayload = DataPayload(
            data = mapOf("stockPrice" to 101.25),
            errors = listOf(mapOf("message" to "delayed quote")),
        )
        val ssePayload: SSEDataPayload = SSEDataPayload(
            data = mapOf("stockPrice" to 102.5),
            errors = listOf("stale source"),
            subId = "prices-1",
        )

        val decodedData: DataPayload = roundTrip(dataPayload, DataPayload::class.java)
        val decodedSse: SSEDataPayload = roundTrip(ssePayload, SSEDataPayload::class.java)
        val defaultData: DataPayload = mapper.readValue("""{"data":null}""", DataPayload::class.java)
        val defaultSse: SSEDataPayload = mapper.readValue(
            """{"data":{"status":"ready"},"subId":"status-1"}""",
            SSEDataPayload::class.java,
        )

        assertThat(decodedData).isEqualTo(dataPayload)
        assertThat(decodedSse).isEqualTo(ssePayload)
        assertThat(defaultData.errors).isEmpty()
        assertThat(defaultSse.errors).isEmpty()
        assertThat(defaultSse.type).isEqualTo(SSE_GQL_SUBSCRIPTION_DATA)
    }

    @Test
    public fun everyWebSocketMessageVariantRoundTripsPolymorphically() {
        val messages: List<Message> = listOf(
            Message.ConnectionInitMessage(mapOf("authorization" to "Bearer token")),
            Message.ConnectionAckMessage(mapOf("accepted" to true)),
            Message.PingMessage(mapOf("sequence" to 1)),
            Message.PongMessage(mapOf("sequence" to 1)),
            Message.SubscribeMessage(
                id = "subscription-1",
                payload = Message.SubscribeMessage.Payload(
                    operationName = "OnMessage",
                    query = "subscription OnMessage { messageAdded { id text } }",
                    variables = mapOf("channel" to "general"),
                    extensions = mapOf("client" to "native-test"),
                ),
            ),
            Message.NextMessage(
                id = "subscription-1",
                payload = ExecutionResult(
                    data = mapOf("messageAdded" to mapOf("id" to "m-1", "text" to "hello")),
                    errors = emptyList(),
                ),
            ),
            Message.ErrorMessage(
                id = "subscription-1",
                payload = listOf(mapOf("message" to "subscription rejected")),
            ),
            Message.CompleteMessage(id = "subscription-1"),
        )

        val decoded: List<Message> = messages.map { message: Message ->
            val json: String = mapper.writeValueAsString(message)
            mapper.readValue(json, Message::class.java)
        }

        assertThat(decoded).isEqualTo(messages)
        assertThat(decoded.map { message: Message -> message.type }).containsExactly(
            MessageType.CONNECTION_INIT,
            MessageType.CONNECTION_ACK,
            MessageType.PING,
            MessageType.PONG,
            MessageType.SUBSCRIBE,
            MessageType.NEXT,
            MessageType.ERROR,
            MessageType.COMPLETE,
        )
    }

    @Test
    public fun executionResultsRetainTypedGraphQlErrors() {
        val graphQLError: GraphQLError = GraphqlErrorBuilder.newError()
            .message("price service unavailable")
            .path(listOf("priceUpdated"))
            .extensions(mapOf("classification" to "UPSTREAM"))
            .build()
        val nextMessage: Message.NextMessage = Message.NextMessage(
            id = "subscription-errors",
            payload = ExecutionResult(
                data = mapOf("priceUpdated" to null),
                errors = listOf(graphQLError),
            ),
        )

        val retainedError: GraphQLError = nextMessage.payload.errors.single()
        assertThat(nextMessage.payload.data).isEqualTo(mapOf("priceUpdated" to null))
        assertThat(retainedError.message).isEqualTo("price service unavailable")
        assertThat(retainedError.path).containsExactly("priceUpdated")
        assertThat(retainedError.extensions).containsEntry("classification", "UPSTREAM")
    }

    @Test
    public fun webSocketMessagesUseDefaultPayloadValues() {
        val init: Message = mapper.readValue(
            """{"type":"connection_init"}""",
            Message::class.java,
        )
        val subscribe: Message = mapper.readValue(
            """
            {
              "type": "subscribe",
              "id": "minimal-1",
              "payload": {"query": "subscription { heartbeat }"}
            }
            """.trimIndent(),
            Message::class.java,
        )

        assertThat(init).isEqualTo(Message.ConnectionInitMessage())
        assertThat(subscribe).isEqualTo(
            Message.SubscribeMessage(
                id = "minimal-1",
                payload = Message.SubscribeMessage.Payload(
                    query = "subscription { heartbeat }",
                ),
            ),
        )
    }

    @Test
    public fun missingOrUnknownMessageTypeIsRejected() {
        assertThatThrownBy {
            mapper.readValue("""{"id":"subscription-1"}""", Message::class.java)
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("type")

        assertThatThrownBy {
            mapper.readValue("""{"type":"not-a-protocol-message"}""", Message::class.java)
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("not-a-protocol-message")

        assertThatThrownBy {
            mapper.readValue("""{"payload":{}}""", OperationMessage::class.java)
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("type")
    }

    @Test
    public fun missingOrMalformedQueriesAreRejected() {
        assertThatThrownBy {
            mapper.readValue("""{"variables":{"id":1}}""", QueryPayload::class.java)
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("query")

        assertThatThrownBy {
            mapper.readValue("""{"query":["not","graphql"]}""", QueryPayload::class.java)
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("query")

        assertThatThrownBy {
            mapper.readValue(
                """{"type":"subscribe","id":"subscription-1","payload":{}}""",
                Message::class.java,
            )
        }.isInstanceOf(JsonMappingException::class.java)
            .hasMessageContaining("query")
    }

    @Test
    public fun legacyOperationMessageConstantsExposeProtocolWireValues() {
        assertThat(
            listOf(
                GQL_CONNECTION_INIT,
                GQL_CONNECTION_ACK,
                GQL_CONNECTION_ERROR,
                GQL_START,
                GQL_STOP,
                GQL_DATA,
                GQL_ERROR,
                GQL_COMPLETE,
                GQL_CONNECTION_TERMINATE,
                GQL_CONNECTION_KEEP_ALIVE,
            ),
        ).containsExactly(
            "connection_init",
            "connection_ack",
            "connection_error",
            "start",
            "stop",
            "data",
            "error",
            "complete",
            "connection_terminate",
            "ka",
        )
    }

    @Test
    public fun protocolConstantsCloseCodesAndErrorPayloadExposeWireValues() {
        val error: SubscriptionError = roundTrip(
            SubscriptionError(message = "subscription unavailable"),
            SubscriptionError::class.java,
        )

        assertThat(GRAPHQL_SUBSCRIPTIONS_WS_PROTOCOL).isEqualTo("graphql-ws")
        assertThat(GRAPHQL_SUBSCRIPTIONS_TRANSPORT_WS_PROTOCOL).isEqualTo("graphql-transport-ws")
        assertThat(CloseCode.Unauthorized.code).isEqualTo(4401)
        assertThat(CloseCode.SubscriberAlreadyExists.code).isEqualTo(4409)
        assertThat(error.message).isEqualTo("subscription unavailable")
    }

    private fun <T : Any> roundTrip(value: T, type: Class<T>): T {
        val json: String = mapper.writeValueAsString(value)
        return mapper.readValue(json, type)
    }
}
