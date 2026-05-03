/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_eventstream.eventstream;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class EventstreamTest {

    @Test
    void headerValuesExposeTypedValuesAndValueSemantics() {
        Instant timestamp = Instant.ofEpochMilli(1_704_067_200_123L);
        Date date = Date.from(timestamp);
        UUID uuid = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        byte[] bytes = new byte[] {1, 2, 3, 4};
        ByteBuffer sourceBuffer = ByteBuffer.wrap(new byte[] {9, 8, 7, 6, 5});
        sourceBuffer.position(1);
        sourceBuffer.limit(4);

        HeaderValue trueValue = HeaderValue.fromBoolean(true);
        HeaderValue falseValue = HeaderValue.fromBoolean(false);
        HeaderValue byteValue = HeaderValue.fromByte((byte) 0x7f);
        HeaderValue shortValue = HeaderValue.fromShort((short) 32_000);
        HeaderValue integerValue = HeaderValue.fromInteger(123_456_789);
        HeaderValue longValue = HeaderValue.fromLong(9_876_543_210L);
        HeaderValue byteArrayValue = HeaderValue.fromByteArray(bytes);
        HeaderValue byteBufferValue = HeaderValue.fromByteBuffer(sourceBuffer);
        HeaderValue stringValue = HeaderValue.fromString("event-stream");
        HeaderValue timestampValue = HeaderValue.fromTimestamp(timestamp);
        HeaderValue dateValue = HeaderValue.fromDate(date);
        HeaderValue uuidValue = HeaderValue.fromUuid(uuid);

        assertThat(trueValue.getBoolean()).isTrue();
        assertThat(falseValue.getBoolean()).isFalse();
        assertThat(byteValue.getByte()).isEqualTo((byte) 0x7f);
        assertThat(shortValue.getShort()).isEqualTo((short) 32_000);
        assertThat(integerValue.getInteger()).isEqualTo(123_456_789);
        assertThat(longValue.getLong()).isEqualTo(9_876_543_210L);
        assertThat(byteArrayValue.getByteArray()).containsExactly(new byte[] {1, 2, 3, 4});
        assertThat(byteBufferValue.getByteArray()).containsExactly(new byte[] {8, 7, 6});
        assertThat(sourceBuffer.position()).isEqualTo(1);
        assertThat(stringValue.getString()).isEqualTo("event-stream");
        assertThat(timestampValue.getTimestamp()).isEqualTo(timestamp);
        assertThat(timestampValue.getDate()).isEqualTo(date);
        assertThat(dateValue.getTimestamp()).isEqualTo(timestamp);
        assertThat(uuidValue.getUuid()).isEqualTo(uuid);

        ByteBuffer byteBuffer = byteArrayValue.getByteBuffer();
        assertThat(byteBuffer.remaining()).isEqualTo(bytes.length);
        assertThat(readRemaining(byteBuffer)).containsExactly(bytes);
        assertThat(HeaderValue.fromString("event-stream")).isEqualTo(stringValue).hasSameHashCodeAs(stringValue);
        assertThat(HeaderValue.fromByteArray(new byte[] {1, 2, 3, 4})).isEqualTo(byteArrayValue);
        assertThat(stringValue.toString()).isEqualTo("\"event-stream\"");
        assertThat(byteArrayValue.toString()).isEqualTo("AQIDBA==");
        assertThat(timestampValue.toString()).isEqualTo(timestamp.toString());

        assertThatThrownBy(stringValue::getInteger).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected integer");
        assertThatThrownBy(integerValue::getString).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(byteArrayValue::getUuid).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected UUID");
    }

    @Test
    void messageRoundTripsAllHeaderTypesThroughByteBufferAndOutputStream() {
        Map<String, HeaderValue> headers = completeHeaders();
        byte[] payload = "{\"message\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        Message message = new Message(headers, payload);

        ByteBuffer encoded = message.toByteBuffer();
        byte[] encodedBytes = readRemaining(encoded.duplicate());
        byte[] separatelyEncodedHeaders = Message.encodeHeaders(headers.entrySet());

        assertThat(encoded.getInt(0)).isEqualTo(encodedBytes.length);
        assertThat(encoded.getInt(4)).isEqualTo(separatelyEncodedHeaders.length);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        message.encode(outputStream);
        assertThat(outputStream.toByteArray()).containsExactly(encodedBytes);

        ByteBuffer decodeBuffer = ByteBuffer.wrap(encodedBytes);
        Message decoded = Message.decode(decodeBuffer);

        assertThat(decodeBuffer.position()).isEqualTo(decodeBuffer.limit());
        assertThat(decoded).isEqualTo(message).hasSameHashCodeAs(message);
        assertThat(decoded.getPayload()).containsExactly(payload);
        assertThat(decoded.getHeaders()).containsExactlyEntriesOf(headers);
        assertThat(decoded.getHeaders().get("booleanTrue").getBoolean()).isTrue();
        assertThat(decoded.getHeaders().get("booleanFalse").getBoolean()).isFalse();
        assertThat(decoded.getHeaders().get("integer").getInteger()).isEqualTo(1_234_567);
        assertThat(decoded.getHeaders().get("long").getLong()).isEqualTo(1_234_567_890_123L);
        assertThat(decoded.getHeaders().get("bytes").getByteArray()).containsExactly(new byte[] {10, 20, 30});
        assertThat(decoded.getHeaders().get("string").getString()).isEqualTo("value");
        assertThat(decoded.getHeaders().get("timestamp").getTimestamp())
                .isEqualTo(Instant.ofEpochMilli(1_704_067_200_000L));
        assertThat(decoded.getHeaders().get("uuid").getUuid())
                .isEqualTo(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        assertThatThrownBy(() -> decoded.getHeaders().put("new", HeaderValue.fromString("rejected")))
                .isInstanceOf(UnsupportedOperationException.class);

        byte[] returnedPayload = decoded.getPayload();
        returnedPayload[0] = 'X';
        assertThat(decoded.getPayload()).containsExactly(payload);
    }

    @Test
    void messageBuilderDefaultBuilderCreatesEquivalentMessages() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put(":message-type", HeaderValue.fromString("event"));
        headers.put(":event-type", HeaderValue.fromString("Records"));
        byte[] payload = "records".getBytes(StandardCharsets.UTF_8);

        Message built = MessageBuilder.defaultBuilder().build(headers, payload);
        Message direct = new Message(headers, payload);

        assertThat(built).isEqualTo(direct);
        assertThat(Message.decode(built.toByteBuffer())).isEqualTo(direct);
    }

    @Test
    void decoderBuffersPartialInputAndReturnsDecodedMessages() {
        Message first = new Message(Map.of("name", HeaderValue.fromString("first")), new byte[] {1, 2, 3});
        Message second = new Message(Map.of("name", HeaderValue.fromString("second")), new byte[] {4, 5});
        byte[] encodedFirst = readRemaining(first.toByteBuffer());
        byte[] encodedSecond = readRemaining(second.toByteBuffer());
        byte[] combined = concat(encodedFirst, encodedSecond);
        MessageDecoder decoder = new MessageDecoder();

        decoder.feed(combined, 0, 5);
        assertThat(decoder.getDecodedMessages()).isEmpty();

        ByteBuffer firstRemainder = ByteBuffer.wrap(combined, 5, encodedFirst.length - 5);
        assertThat(decoder.feed(firstRemainder)).isSameAs(decoder);
        assertThat(firstRemainder.remaining()).isZero();

        List<Message> firstDecodedMessages = decoder.getDecodedMessages();
        assertThat(firstDecodedMessages).containsExactly(first);
        assertThatThrownBy(() -> firstDecodedMessages.add(first)).isInstanceOf(UnsupportedOperationException.class);

        ByteBuffer secondPrefix = ByteBuffer.wrap(combined, encodedFirst.length, encodedSecond.length - 3);
        decoder.feed(secondPrefix);
        assertThat(secondPrefix.remaining()).isZero();
        assertThat(decoder.getDecodedMessages()).isEmpty();
        decoder.feed(combined, combined.length - 3, 3);

        assertThat(decoder.getDecodedMessages()).containsExactly(second);
        assertThat(decoder.getDecodedMessages()).isEmpty();
    }

    @Test
    void decoderAcceptsMessagesLargerThanItsInitialBuffer() {
        byte[] payload = new byte[2 * 1024 * 1024 + 128];
        Arrays.fill(payload, (byte) 'a');
        payload[0] = 1;
        payload[payload.length - 1] = 2;
        Message message = new Message(Map.of("name", HeaderValue.fromString("large")), payload);
        byte[] encoded = readRemaining(message.toByteBuffer());
        MessageDecoder decoder = new MessageDecoder();

        decoder.feed(encoded, 0, 64);
        assertThat(decoder.getDecodedMessages()).isEmpty();
        decoder.feed(encoded, 64, encoded.length - 64);

        List<Message> decodedMessages = decoder.getDecodedMessages();
        assertThat(decodedMessages).hasSize(1);
        Message decoded = decodedMessages.get(0);
        assertThat(decoded.getHeaders()).containsExactlyEntriesOf(message.getHeaders());
        assertThat(decoded.getPayload()).containsExactly(payload);
        assertThat(decoder.getDecodedMessages()).isEmpty();
    }

    @Test
    void decoderCanDispatchToConsumer() {
        Message first = new Message(Map.of("sequence", HeaderValue.fromInteger(1)), new byte[] {1});
        Message second = new Message(Map.of("sequence", HeaderValue.fromInteger(2)), new byte[] {2});
        List<Message> consumed = new ArrayList<>();
        MessageDecoder decoder = new MessageDecoder(consumed::add);

        decoder.feed(concat(readRemaining(first.toByteBuffer()), readRemaining(second.toByteBuffer())));

        assertThat(consumed).containsExactly(first, second);
        assertThatThrownBy(decoder::getDecodedMessages).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decodeRejectsCorruptChecksums() {
        Message message = new Message(Map.of("name", HeaderValue.fromString("checksum")), new byte[] {1, 2, 3});
        byte[] corruptPreludeChecksum = readRemaining(message.toByteBuffer());
        corruptPreludeChecksum[11] ^= 0x01;

        assertThatThrownBy(() -> Message.decode(ByteBuffer.wrap(corruptPreludeChecksum)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prelude checksum failure");

        byte[] corruptMessageChecksum = readRemaining(message.toByteBuffer());
        corruptMessageChecksum[corruptMessageChecksum.length - 1] ^= 0x01;

        assertThatThrownBy(() -> Message.decode(ByteBuffer.wrap(corruptMessageChecksum)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message checksum failure");
    }

    @Test
    void messageToStringUsesContentTypeToRenderPayload() {
        Message textMessage = new Message(
                Map.of(":content-type", HeaderValue.fromString("text/plain")),
                "plain text".getBytes(StandardCharsets.UTF_8));
        Message binaryMessage = new Message(Map.of(), new byte[] {1, 2, 3});

        assertThat(textMessage.toString()).contains(":content-type: \"text/plain\"").contains("plain text");
        assertThat(binaryMessage.toString()).contains("AQID");
    }

    private static Map<String, HeaderValue> completeHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("booleanTrue", HeaderValue.fromBoolean(true));
        headers.put("booleanFalse", HeaderValue.fromBoolean(false));
        headers.put("integer", HeaderValue.fromInteger(1_234_567));
        headers.put("long", HeaderValue.fromLong(1_234_567_890_123L));
        headers.put("bytes", HeaderValue.fromByteArray(new byte[] {10, 20, 30}));
        headers.put("string", HeaderValue.fromString("value"));
        headers.put("timestamp", HeaderValue.fromTimestamp(Instant.ofEpochMilli(1_704_067_200_000L)));
        headers.put("uuid", HeaderValue.fromUuid(UUID.fromString("11111111-2222-3333-4444-555555555555")));
        return headers;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static byte[] readRemaining(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes);
        return bytes;
    }
}
