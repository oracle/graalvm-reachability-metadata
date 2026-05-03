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
import java.util.Base64;
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
    @Test
    void headerValueFactoriesExposeTypedValuesAndEquality() {
        Instant timestamp = Instant.ofEpochMilli(1_714_063_222_345L);
        Date date = Date.from(timestamp);
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        byte[] bytes = new byte[] {1, 2, 3, 4};

        HeaderValue trueValue = HeaderValue.fromBoolean(true);
        HeaderValue falseValue = HeaderValue.fromBoolean(false);
        HeaderValue byteValue = HeaderValue.fromByte((byte) 0x7f);
        HeaderValue shortValue = HeaderValue.fromShort((short) 32_000);
        HeaderValue integerValue = HeaderValue.fromInteger(123_456_789);
        HeaderValue longValue = HeaderValue.fromLong(9_876_543_210L);
        HeaderValue byteArrayValue = HeaderValue.fromByteArray(bytes);
        HeaderValue byteBufferValue = HeaderValue.fromByteBuffer(ByteBuffer.wrap(new byte[] {9, 8, 7, 6}, 1, 2));
        HeaderValue stringValue = HeaderValue.fromString("application/json");
        HeaderValue timestampValue = HeaderValue.fromTimestamp(timestamp);
        HeaderValue dateValue = HeaderValue.fromDate(date);
        HeaderValue uuidValue = HeaderValue.fromUuid(uuid);

        bytes[0] = 99;

        assertThat(trueValue.getBoolean()).isTrue();
        assertThat(falseValue.getBoolean()).isFalse();
        assertThat(byteValue.getByte()).isEqualTo((byte) 0x7f);
        assertThat(shortValue.getShort()).isEqualTo((short) 32_000);
        assertThat(integerValue.getInteger()).isEqualTo(123_456_789);
        assertThat(longValue.getLong()).isEqualTo(9_876_543_210L);
        assertThat(byteArrayValue.getByteArray()).containsExactly(1, 2, 3, 4);
        assertThat(byteBufferValue.getByteBuffer()).isEqualTo(ByteBuffer.wrap(new byte[] {8, 7}));
        assertThat(stringValue.getString()).isEqualTo("application/json");
        assertThat(timestampValue.getTimestamp()).isEqualTo(timestamp);
        assertThat(dateValue.getDate()).isEqualTo(date);
        assertThat(dateValue).isEqualTo(timestampValue);
        assertThat(uuidValue.getUuid()).isEqualTo(uuid);

        assertThat(HeaderValue.fromInteger(123_456_789))
                .isEqualTo(integerValue)
                .hasSameHashCodeAs(integerValue)
                .hasToString("123456789");
        assertThat(stringValue).hasToString("\"application/json\"");
        assertThat(byteArrayValue).hasToString(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3, 4}));
        assertThat(uuidValue).hasToString(uuid.toString());
    }

    @Test
    void fromByteBufferReadsOnlyRemainingBytesWithoutChangingSourcePosition() {
        ByteBuffer source = ByteBuffer.wrap(new byte[] {10, 20, 30, 40, 50});
        source.position(1);
        source.limit(4);

        HeaderValue value = HeaderValue.fromByteBuffer(source);

        assertThat(source.position()).isEqualTo(1);
        assertThat(source.limit()).isEqualTo(4);
        assertThat(value.getByteArray()).containsExactly(20, 30, 40);
    }

    @Test
    void wrongHeaderValueAccessorReportsTypeMismatch() {
        HeaderValue value = HeaderValue.fromString("text/plain");

        assertThatThrownBy(value::getInteger)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected integer")
                .hasMessageContaining("STRING");
        assertThatThrownBy(value::getBoolean)
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(value::getUuid)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected UUID")
                .hasMessageContaining("STRING");
    }

    @Test
    void messageRoundTripPreservesHeadersPayloadAndValueSemantics() throws Exception {
        Map<String, HeaderValue> headers = supportedRoundTripHeaders();
        byte[] payload = "{\"message\":\"hello event stream\"}".getBytes(StandardCharsets.UTF_8);
        Message message = MessageBuilder.defaultBuilder().build(headers, payload);

        ByteArrayOutputStream encodedByStream = new ByteArrayOutputStream();
        message.encode(encodedByStream);
        ByteBuffer encoded = message.toByteBuffer();

        assertThat(encodedByStream.toByteArray()).containsExactly(toByteArray(encoded.duplicate()));

        Message decoded = Message.decode(encoded.duplicate());

        assertThat(decoded).isEqualTo(message);
        assertThat(decoded.hashCode()).isEqualTo(message.hashCode());
        assertThat(decoded.getPayload()).containsExactly(payload);
        assertThat(decoded.getPayload()).isNotSameAs(payload);
        assertThat(decoded.getHeaders()).containsExactlyEntriesOf(headers);
        assertThat(decoded.getHeaders().get("booleanTrue").getBoolean()).isTrue();
        assertThat(decoded.getHeaders().get("booleanFalse").getBoolean()).isFalse();
        assertThat(decoded.getHeaders().get("integer").getInteger()).isEqualTo(42);
        assertThat(decoded.getHeaders().get("long").getLong()).isEqualTo(4_294_967_296L);
        assertThat(decoded.getHeaders().get("bytes").getByteArray()).containsExactly(5, 4, 3, 2, 1);
        assertThat(decoded.getHeaders().get("string").getString()).isEqualTo("value");
        assertThat(decoded.getHeaders().get("timestamp").getTimestamp())
                .isEqualTo(Instant.ofEpochMilli(1_714_063_222_345L));
        assertThat(decoded.getHeaders().get("uuid").getUuid())
                .isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThatThrownBy(() -> decoded.getHeaders().put("new", HeaderValue.fromString("value")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void decodeReadsFromCurrentByteBufferPositionAndLeavesTrailingBytesUnread() {
        Message message = new Message(Map.of("event", HeaderValue.fromString("positioned")), new byte[] {3, 1, 4});
        byte[] encoded = toByteArray(message.toByteBuffer());
        byte[] framed = new byte[2 + encoded.length + 2];
        framed[0] = 99;
        framed[1] = 100;
        System.arraycopy(encoded, 0, framed, 2, encoded.length);
        framed[framed.length - 2] = 101;
        framed[framed.length - 1] = 102;
        ByteBuffer buffer = ByteBuffer.wrap(framed);
        buffer.position(2);

        Message decoded = Message.decode(buffer);

        assertThat(decoded).isEqualTo(message);
        assertThat(buffer.position()).isEqualTo(2 + encoded.length);
        assertThat(buffer.remaining()).isEqualTo(2);
        assertThat(buffer.get()).isEqualTo((byte) 101);
        assertThat(buffer.get()).isEqualTo((byte) 102);
    }

    @Test
    void messageDefensivelyCopiesPayloadInputAndOutput() {
        byte[] payload = new byte[] {1, 2, 3};
        Message message = new Message(Map.of(), payload);

        payload[0] = 9;
        byte[] returnedPayload = message.getPayload();
        returnedPayload[1] = 8;

        assertThat(message.getPayload()).containsExactly(1, 2, 3);
    }

    @Test
    void encodeHeadersMatchesMessageHeaderSectionAndSupportsByteAndShortHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("byte", HeaderValue.fromByte((byte) -12));
        headers.put("short", HeaderValue.fromShort((short) 12_345));
        headers.put("string", HeaderValue.fromString("metadata"));
        byte[] payload = new byte[] {10, 20};
        byte[] encodedHeaders = Message.encodeHeaders(headers.entrySet());

        ByteBuffer encodedMessage = new Message(headers, payload).toByteBuffer();
        int totalLength = encodedMessage.getInt();
        int headersLength = encodedMessage.getInt();
        encodedMessage.position(12);
        byte[] messageHeaderSection = new byte[headersLength];
        encodedMessage.get(messageHeaderSection);

        assertThat(headersLength).isEqualTo(encodedHeaders.length);
        assertThat(messageHeaderSection).containsExactly(encodedHeaders);
        assertThat(totalLength).isEqualTo(16 + encodedHeaders.length + payload.length);

        Message decoded = Message.decode(new Message(headers, payload).toByteBuffer());
        assertThat(decoded.getHeaders().keySet()).containsExactly("byte", "short", "string");
        assertThat(decoded.getHeaders().get("byte").getByte()).isEqualTo((byte) -12);
        assertThat(decoded.getHeaders().get("short").getShort()).isEqualTo((short) 12_345);
        assertThat(decoded.getHeaders().get("string").getString()).isEqualTo("metadata");
    }

    @Test
    void decoderBuffersPartialInputAndEmitsCompleteMessagesInOrder() {
        Message first = new Message(Map.of("name", HeaderValue.fromString("first")), new byte[] {1, 2, 3});
        Message second = new Message(Map.of("name", HeaderValue.fromString("second")), new byte[] {4, 5});
        byte[] firstBytes = toByteArray(first.toByteBuffer());
        byte[] secondBytes = toByteArray(second.toByteBuffer());
        MessageDecoder decoder = new MessageDecoder();

        decoder.feed(firstBytes, 0, 5);
        assertThat(decoder.getDecodedMessages()).isEmpty();

        decoder.feed(firstBytes, 5, firstBytes.length - 5);
        assertThat(decoder.getDecodedMessages()).containsExactly(first);
        assertThat(decoder.getDecodedMessages()).isEmpty();

        byte[] prefixedSecond = new byte[secondBytes.length + 2];
        prefixedSecond[0] = 99;
        System.arraycopy(secondBytes, 0, prefixedSecond, 1, secondBytes.length);
        prefixedSecond[prefixedSecond.length - 1] = 100;

        decoder.feed(prefixedSecond, 1, secondBytes.length);
        assertThat(decoder.getDecodedMessages()).containsExactly(second);
    }

    @Test
    void decoderWithConsumerReceivesMessagesAndRejectsBufferedOutputAccess() {
        List<Message> decodedMessages = new ArrayList<>();
        MessageDecoder decoder = new MessageDecoder(decodedMessages::add);
        Message message = new Message(Map.of("event", HeaderValue.fromString("created")), new byte[] {10});
        ByteBuffer encoded = message.toByteBuffer();

        MessageDecoder returnedDecoder = decoder.feed(encoded);

        assertThat(returnedDecoder).isSameAs(decoder);
        assertThat(encoded.hasRemaining()).isFalse();
        assertThat(decodedMessages).containsExactly(message);
        assertThatThrownBy(decoder::getDecodedMessages)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decodeRejectsCorruptedMessageChecksum() {
        Message message = new Message(Map.of("name", HeaderValue.fromString("checksum")), new byte[] {1, 2, 3});
        byte[] encoded = toByteArray(message.toByteBuffer());
        encoded[encoded.length - 1] ^= 0x01;

        assertThatThrownBy(() -> Message.decode(ByteBuffer.wrap(encoded)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message checksum failure");
    }

    @Test
    void toStringRendersTextPayloadsAsUtf8AndBinaryPayloadsAsBase64() {
        Message textMessage = new Message(
                Map.of(":content-type", HeaderValue.fromString("application/json")),
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        Message binaryMessage = new Message(Map.of(), new byte[] {0, 1, 2, 3});

        assertThat(textMessage.toString())
                .contains(":content-type:\"application/json\"")
                .contains("{\"ok\":true}");
        assertThat(binaryMessage.toString())
                .contains(Base64.getEncoder().encodeToString(new byte[] {0, 1, 2, 3}))
                .doesNotContain("{\"ok\":true}");
    }

    private static Map<String, HeaderValue> supportedRoundTripHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("booleanTrue", HeaderValue.fromBoolean(true));
        headers.put("booleanFalse", HeaderValue.fromBoolean(false));
        headers.put("integer", HeaderValue.fromInteger(42));
        headers.put("long", HeaderValue.fromLong(4_294_967_296L));
        headers.put("bytes", HeaderValue.fromByteArray(new byte[] {5, 4, 3, 2, 1}));
        headers.put("string", HeaderValue.fromString("value"));
        headers.put("timestamp", HeaderValue.fromTimestamp(Instant.ofEpochMilli(1_714_063_222_345L)));
        headers.put("uuid", HeaderValue.fromUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));
        return headers;
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
