/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_eventstream.eventstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.eventstream.MessageBuilder;
import software.amazon.eventstream.MessageDecoder;

public class EventstreamTest {
    private static final Instant EVENT_TIME = Instant.ofEpochMilli(1_711_111_222_333L);
    private static final UUID EVENT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void headerValuesExposeTypedAccessorsEqualityAndTextRepresentations() {
        HeaderValue trueValue = HeaderValue.fromBoolean(true);
        HeaderValue falseValue = HeaderValue.fromBoolean(false);
        HeaderValue byteValue = HeaderValue.fromByte((byte) 0x7f);
        HeaderValue shortValue = HeaderValue.fromShort((short) 32_000);
        HeaderValue integerValue = HeaderValue.fromInteger(123_456_789);
        HeaderValue longValue = HeaderValue.fromLong(9_876_543_210L);
        HeaderValue bytesValue = HeaderValue.fromByteArray(new byte[] {1, 2, 3, 4});
        HeaderValue stringValue = HeaderValue.fromString("event-stream");
        HeaderValue timestampValue = HeaderValue.fromTimestamp(EVENT_TIME);
        HeaderValue dateValue = HeaderValue.fromDate(Date.from(EVENT_TIME));
        HeaderValue uuidValue = HeaderValue.fromUuid(EVENT_ID);

        assertThat(trueValue.getBoolean()).isTrue();
        assertThat(falseValue.getBoolean()).isFalse();
        assertThat(byteValue.getByte()).isEqualTo((byte) 0x7f);
        assertThat(shortValue.getShort()).isEqualTo((short) 32_000);
        assertThat(integerValue.getInteger()).isEqualTo(123_456_789);
        assertThat(longValue.getLong()).isEqualTo(9_876_543_210L);
        assertThat(bytesValue.getByteArray()).containsExactly(new byte[] {1, 2, 3, 4});
        assertThat(bytesValue.getByteBuffer().remaining()).isEqualTo(4);
        assertThat(stringValue.getString()).isEqualTo("event-stream");
        assertThat(timestampValue.getTimestamp()).isEqualTo(EVENT_TIME);
        assertThat(timestampValue.getDate()).isEqualTo(Date.from(EVENT_TIME));
        assertThat(dateValue.getTimestamp()).isEqualTo(EVENT_TIME);
        assertThat(uuidValue.getUuid()).isEqualTo(EVENT_ID);

        assertThat(HeaderValue.fromString("event-stream"))
                .isEqualTo(stringValue)
                .hasSameHashCodeAs(stringValue)
                .hasToString("\"event-stream\"");
        assertThat(HeaderValue.fromByteArray(new byte[] {1, 2, 3, 4})).isEqualTo(bytesValue);
        assertThat(bytesValue).hasToString("AQIDBA==");
        assertThat(trueValue).hasToString("true");
        assertThat(uuidValue).hasToString(EVENT_ID.toString());
    }

    @Test
    void byteBufferHeaderValueCopiesOnlyRemainingBytesWithoutMovingSourcePosition() {
        ByteBuffer source = ByteBuffer.wrap(new byte[] {9, 8, 7, 6, 5});
        source.position(1);
        source.limit(4);

        HeaderValue value = HeaderValue.fromByteBuffer(source);

        assertThat(source.position()).isEqualTo(1);
        assertThat(source.limit()).isEqualTo(4);
        assertThat(value.getByteArray()).containsExactly(new byte[] {8, 7, 6});
    }

    @Test
    void wrongTypedHeaderAccessorsThrowIllegalStateException() {
        HeaderValue stringValue = HeaderValue.fromString("not-an-integer");
        HeaderValue integerValue = HeaderValue.fromInteger(42);

        assertThatThrownBy(stringValue::getInteger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected integer");
        assertThatThrownBy(integerValue::getString)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void messageRoundTripsAllEncodableHeaderTypesThroughByteBuffer() {
        byte[] payload = "{\"message\":\"hello\",\"count\":2}".getBytes(StandardCharsets.UTF_8);
        Message original = new Message(encodableHeaders(), payload);

        ByteBuffer encoded = original.toByteBuffer();
        Message decoded = Message.decode(encoded.duplicate());

        assertThat(decoded).isEqualTo(original).hasSameHashCodeAs(original);
        assertThat(decoded.getPayload()).containsExactly(payload);
        assertThat(decoded.getHeaders()).containsOnlyKeys(
                ":content-type", "boolean", "integer", "long", "blob", "string", "timestamp", "uuid");
        assertThat(decoded.getHeaders().get(":content-type").getString()).isEqualTo("application/json");
        assertThat(decoded.getHeaders().get("boolean").getBoolean()).isTrue();
        assertThat(decoded.getHeaders().get("integer").getInteger()).isEqualTo(42);
        assertThat(decoded.getHeaders().get("long").getLong()).isEqualTo(4_294_967_296L);
        assertThat(decoded.getHeaders().get("blob").getByteArray()).containsExactly(new byte[] {10, 20, 30});
        assertThat(decoded.getHeaders().get("string").getString()).isEqualTo("value");
        assertThat(decoded.getHeaders().get("timestamp").getTimestamp()).isEqualTo(EVENT_TIME);
        assertThat(decoded.getHeaders().get("uuid").getUuid()).isEqualTo(EVENT_ID);
        assertThat(decoded.toString()).contains(":content-type: \"application/json\"").contains("hello");
    }

    @Test
    void messageToStringUsesBase64ForBinaryPayloadWhenContentTypeIsAbsent() {
        Message message = new Message(Map.of("event", HeaderValue.fromString("binary")), new byte[] {0, -1, 66, 105, 110});

        assertThat(message.toString())
                .contains("event: \"binary\"")
                .contains("AP9CaW4=")
                .doesNotContain("\u0000");
    }

    @Test
    void encodeWritesTheSameBytesAsToByteBufferAndDecodeConsumesFromCurrentPosition() {
        Message message = new Message(encodableHeaders(), "payload".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        message.encode(output);
        byte[] encodedByStream = output.toByteArray();
        byte[] encodedByBuffer = remainingBytes(message.toByteBuffer());
        ByteBuffer prefixed = ByteBuffer.allocate(encodedByStream.length + 3);
        prefixed.put(new byte[] {1, 2, 3});
        prefixed.put(encodedByStream);
        prefixed.flip();
        prefixed.position(3);

        assertThat(encodedByStream).containsExactly(encodedByBuffer);
        assertThat(Message.decode(prefixed)).isEqualTo(message);
    }

    @Test
    void constructorAndAccessorsDefensivelyCopyPayloadBytes() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("name", HeaderValue.fromString("copy-test"));
        byte[] payload = new byte[] {1, 2, 3};

        Message message = new Message(headers, payload);
        payload[0] = 99;
        byte[] returnedPayload = message.getPayload();
        returnedPayload[1] = 88;

        assertThat(message.getPayload()).containsExactly(new byte[] {1, 2, 3});
        assertThat(message.getHeaders()).isSameAs(headers);
    }

    @Test
    void defaultMessageBuilderCreatesMessagesFromHeadersAndPayload() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("builder", HeaderValue.fromString("default"));
        byte[] payload = "built".getBytes(StandardCharsets.UTF_8);

        Message message = MessageBuilder.defaultBuilder().build(headers, payload);

        assertThat(message.getHeaders()).isSameAs(headers);
        assertThat(message.getHeaders().get("builder").getString()).isEqualTo("default");
        assertThat(message.getPayload()).containsExactly(payload);
        assertThat(Message.decode(message.toByteBuffer())).isEqualTo(message);
    }

    @Test
    void encodeHeadersWritesStandaloneHeaderBlockInIterableOrder() {
        List<Map.Entry<String, HeaderValue>> headers = List.of(
                Map.entry("flag", HeaderValue.fromBoolean(true)),
                Map.entry("disabled", HeaderValue.fromBoolean(false)),
                Map.entry("count", HeaderValue.fromInteger(3)),
                Map.entry("name", HeaderValue.fromString("aws")));

        byte[] encodedHeaders = Message.encodeHeaders(headers);

        assertThat(encodedHeaders).containsExactly(new byte[] {
                4, 'f', 'l', 'a', 'g', 0,
                8, 'd', 'i', 's', 'a', 'b', 'l', 'e', 'd', 1,
                5, 'c', 'o', 'u', 'n', 't', 4, 0, 0, 0, 3,
                4, 'n', 'a', 'm', 'e', 7, 0, 3, 'a', 'w', 's'
        });
    }

    @Test
    void decoderBuffersPartialFramesAndReturnsDecodedMessagesInArrivalOrder() {
        Message first = new Message(Map.of("sequence", HeaderValue.fromInteger(1)), new byte[] {1});
        Message second = new Message(Map.of("sequence", HeaderValue.fromInteger(2)), new byte[] {2, 3});
        byte[] concatenated = concatenate(remainingBytes(first.toByteBuffer()), remainingBytes(second.toByteBuffer()));
        MessageDecoder decoder = new MessageDecoder();

        decoder.feed(concatenated, 0, 5);
        assertThat(decoder.getDecodedMessages()).isEmpty();
        for (int index = 5; index < concatenated.length; index++) {
            decoder.feed(concatenated, index, 1);
        }

        List<Message> decodedMessages = decoder.getDecodedMessages();
        assertThat(decodedMessages).containsExactly(first, second);
        assertThatThrownBy(() -> decodedMessages.add(first)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(decoder.getDecodedMessages()).isEmpty();
    }

    @Test
    void decoderConsumerReceivesMessagesAndFeedByteBufferAdvancesInputPosition() {
        List<Message> consumed = new ArrayList<>();
        MessageDecoder decoder = new MessageDecoder(consumed::add);
        Message message = new Message(Map.of("mode", HeaderValue.fromString("consumer")), new byte[] {4, 5, 6});
        ByteBuffer input = message.toByteBuffer();
        int bytesToFeed = input.remaining();

        MessageDecoder returned = decoder.feed(input);

        assertThat(returned).isSameAs(decoder);
        assertThat(input.position()).isEqualTo(bytesToFeed);
        assertThat(consumed).containsExactly(message);
        assertThatThrownBy(decoder::getDecodedMessages).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void corruptedPreludeOrMessageChecksumIsRejected() {
        Message message = new Message(encodableHeaders(), "checksum".getBytes(StandardCharsets.UTF_8));
        byte[] preludeCorrupted = remainingBytes(message.toByteBuffer());
        byte[] messageCorrupted = Arrays.copyOf(preludeCorrupted, preludeCorrupted.length);
        preludeCorrupted[0] ^= 0x01;
        messageCorrupted[messageCorrupted.length - 1] ^= 0x01;

        assertThatThrownBy(() -> Message.decode(ByteBuffer.wrap(preludeCorrupted)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prelude checksum failure");
        assertThatThrownBy(() -> Message.decode(ByteBuffer.wrap(messageCorrupted)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message checksum failure");
    }

    @Test
    void invalidHeaderNamesStringsAndByteArraysAreRejectedDuringEncoding() {
        assertThatThrownBy(() -> new Message(
                Map.of("", HeaderValue.fromString("value")), new byte[] {1}).toByteBuffer())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strings may not be empty");
        assertThatThrownBy(() -> new Message(
                Map.of("name", HeaderValue.fromString("")), new byte[] {1}).toByteBuffer())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strings may not be empty");
        assertThatThrownBy(() -> new Message(
                Map.of("bytes", HeaderValue.fromByteArray(new byte[0])), new byte[] {1}).toByteBuffer())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Byte arrays may not be empty");
    }

    @Test
    void nullConstructorInputsAndNullHeaderValuesAreRejected() {
        assertThatThrownBy(() -> new Message(null, new byte[] {1}))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("headers");
        assertThatThrownBy(() -> new Message(Map.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("payload");
        assertThatThrownBy(() -> HeaderValue.fromString(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> HeaderValue.fromByteArray(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Map<String, HeaderValue> encodableHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put(":content-type", HeaderValue.fromString("application/json"));
        headers.put("boolean", HeaderValue.fromBoolean(true));
        headers.put("integer", HeaderValue.fromInteger(42));
        headers.put("long", HeaderValue.fromLong(4_294_967_296L));
        headers.put("blob", HeaderValue.fromByteArray(new byte[] {10, 20, 30}));
        headers.put("string", HeaderValue.fromString("value"));
        headers.put("timestamp", HeaderValue.fromTimestamp(EVENT_TIME));
        headers.put("uuid", HeaderValue.fromUuid(EVENT_ID));
        return headers;
    }

    private static byte[] remainingBytes(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.duplicate();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        byte[] concatenated = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, concatenated, first.length, second.length);
        return concatenated;
    }
}
