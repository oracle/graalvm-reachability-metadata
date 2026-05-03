/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_eventstream.eventstream;

import org.junit.jupiter.api.Test;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.eventstream.MessageBuilder;
import software.amazon.eventstream.MessageDecoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventstreamTest {
    @Test
    void messageRoundTripsHeadersPayloadAndEquality() {
        Map<String, HeaderValue> headers = representativeHeaders();
        byte[] payload = "{\"message\":\"hello\",\"count\":3}".getBytes(StandardCharsets.UTF_8);
        Message message = new Message(headers, payload);

        ByteBuffer encoded = message.toByteBuffer();
        Message decoded = Message.decode(encoded.duplicate());

        assertThat(decoded).isEqualTo(message);
        assertThat(decoded.hashCode()).isEqualTo(message.hashCode());
        assertThat(decoded.getPayload()).containsExactly(payload);
        assertRepresentativeHeaders(decoded.getHeaders());
        assertThat(encoded.position()).isZero();
        assertThat(encoded.remaining()).isEqualTo(encoded.getInt(0));
    }

    @Test
    void messageBuilderAndOutputStreamEncodingProduceDecodableMessages() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put(":content-type", HeaderValue.fromString("text/plain; charset=utf-8"));
        headers.put("sequence", HeaderValue.fromInteger(42));
        byte[] payload = "event-stream payload".getBytes(StandardCharsets.UTF_8);

        Message built = MessageBuilder.defaultBuilder().build(headers, payload);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        built.encode(outputStream);

        Message decoded = Message.decode(ByteBuffer.wrap(outputStream.toByteArray()));
        assertThat(decoded).isEqualTo(built);
        assertThat(decoded.toString()).contains("sequence: 42", "event-stream payload");
    }

    @Test
    void decoderBuffersFragmentedInputAndReturnsImmutableBatches() {
        Message first = new Message(Map.of("name", HeaderValue.fromString("first")), new byte[] {1, 2, 3});
        Message second = new Message(Map.of("name", HeaderValue.fromString("second")), new byte[] {4, 5});
        byte[] firstBytes = toBytes(first.toByteBuffer());
        byte[] secondBytes = toBytes(second.toByteBuffer());
        byte[] stream = concatenate(firstBytes, secondBytes);

        MessageDecoder decoder = new MessageDecoder();
        decoder.feed(stream, 0, 5);
        assertThat(decoder.getDecodedMessages()).isEmpty();
        decoder.feed(stream, 5, firstBytes.length - 5);

        List<Message> firstBatch = decoder.getDecodedMessages();
        assertThat(firstBatch).containsExactly(first);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> firstBatch.add(second));

        decoder.feed(ByteBuffer.wrap(stream, firstBytes.length, secondBytes.length));
        assertThat(decoder.getDecodedMessages()).containsExactly(second);
        assertThat(decoder.getDecodedMessages()).isEmpty();
    }

    @Test
    void decoderConsumerReceivesMessagesFromCombinedInput() {
        Message first = new Message(Map.of("index", HeaderValue.fromInteger(1)), new byte[] {10});
        Message second = new Message(Map.of("index", HeaderValue.fromInteger(2)), new byte[] {20, 30});
        List<Message> delivered = new ArrayList<>();
        MessageDecoder decoder = new MessageDecoder(delivered::add);

        MessageDecoder returned = decoder.feed(ByteBuffer.wrap(concatenate(
                toBytes(first.toByteBuffer()),
                toBytes(second.toByteBuffer()))));

        assertThat(returned).isSameAs(decoder);
        assertThat(delivered).containsExactly(first, second);
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(decoder::getDecodedMessages);
    }

    @Test
    void headerValueFactoriesExposeTypedValuesAndDefensiveByteBufferCopy() {
        ByteBuffer sourceBuffer = ByteBuffer.wrap(new byte[] {9, 8, 7, 6});
        sourceBuffer.position(1);
        sourceBuffer.limit(3);
        HeaderValue copiedBytes = HeaderValue.fromByteBuffer(sourceBuffer);

        assertThat(HeaderValue.fromBoolean(true).getBoolean()).isTrue();
        assertThat(HeaderValue.fromBoolean(false).getBoolean()).isFalse();
        assertThat(HeaderValue.fromByte((byte) -8).getByte()).isEqualTo((byte) -8);
        assertThat(HeaderValue.fromShort((short) 4096).getShort()).isEqualTo((short) 4096);
        assertThat(HeaderValue.fromInteger(-123456).getInteger()).isEqualTo(-123456);
        assertThat(HeaderValue.fromLong(9_876_543_210L).getLong()).isEqualTo(9_876_543_210L);
        assertThat(HeaderValue.fromString("plain ascii").getString()).isEqualTo("plain ascii");
        assertThat(copiedBytes.getByteArray()).containsExactly((byte) 8, (byte) 7);
        assertThat(toBytes(copiedBytes.getByteBuffer())).containsExactly((byte) 8, (byte) 7);
        assertThat(sourceBuffer.position()).isEqualTo(1);

        Instant timestamp = Instant.ofEpochMilli(1_700_000_000_123L);
        Date date = Date.from(timestamp);
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        assertThat(HeaderValue.fromTimestamp(timestamp).getTimestamp()).isEqualTo(timestamp);
        assertThat(HeaderValue.fromTimestamp(timestamp).getDate()).isEqualTo(date);
        assertThat(HeaderValue.fromDate(date).getTimestamp()).isEqualTo(timestamp);
        assertThat(HeaderValue.fromUuid(uuid).getUuid()).isEqualTo(uuid);
    }

    @Test
    void wrongTypedHeaderAccessorsFailFast() {
        HeaderValue stringValue = HeaderValue.fromString("not-an-integer");
        HeaderValue integerValue = HeaderValue.fromInteger(7);

        assertThrows(IllegalStateException.class, stringValue::getInteger);
        assertThrows(IllegalStateException.class, integerValue::getString);
        assertThrows(IllegalStateException.class, integerValue::getUuid);
    }

    @Test
    void payloadIsDefensivelyCopiedAndDecodedHeadersAreImmutable() {
        byte[] payload = new byte[] {1, 2, 3};
        Message message = new Message(Map.of("kind", HeaderValue.fromString("copy-test")), payload);
        payload[0] = 99;

        assertThat(message.getPayload()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        byte[] returnedPayload = message.getPayload();
        returnedPayload[1] = 99;
        assertThat(message.getPayload()).containsExactly((byte) 1, (byte) 2, (byte) 3);

        Message decoded = Message.decode(message.toByteBuffer());
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> decoded.getHeaders().put("new", HeaderValue.fromBoolean(true)));
    }

    @Test
    void encodeHeadersMatchesMessagePreludeHeadersLength() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("alpha", HeaderValue.fromString("one"));
        headers.put("beta", HeaderValue.fromInteger(2));
        byte[] encodedHeaders = Message.encodeHeaders(headers.entrySet());

        Message message = new Message(headers, new byte[] {11, 12, 13});
        ByteBuffer encodedMessage = message.toByteBuffer();

        assertThat(encodedHeaders.length).isEqualTo(encodedMessage.getInt(4));
        Message decoded = Message.decode(encodedMessage.duplicate());
        assertThat(decoded.getHeaders()).containsExactlyEntriesOf(headers);
    }

    @Test
    void headerEncodingAcceptsEventStreamBoundarySizedNamesStringsAndByteArrays() {
        String maximumName = "n".repeat(255);
        String maximumString = "s".repeat(32_767);
        byte[] maximumBytes = new byte[32_767];
        Arrays.fill(maximumBytes, (byte) 0x5a);
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put(maximumName, HeaderValue.fromString("name-limit"));
        headers.put("max-string", HeaderValue.fromString(maximumString));
        headers.put("max-bytes", HeaderValue.fromByteArray(maximumBytes));

        Message decoded = Message.decode(new Message(headers, new byte[0]).toByteBuffer());

        assertThat(decoded.getHeaders().get(maximumName).getString()).isEqualTo("name-limit");
        assertThat(decoded.getHeaders().get("max-string").getString()).isEqualTo(maximumString);
        assertThat(decoded.getHeaders().get("max-bytes").getByteArray()).containsExactly(maximumBytes);
    }

    @Test
    void headerEncodingRejectsNamesStringsAndByteArraysOutsideEventStreamBounds() {
        HeaderValue validValue = HeaderValue.fromString("ok");

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("", validValue), new byte[0]).toByteBuffer())
                .withMessageContaining("Strings may not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("n".repeat(256), validValue), new byte[0]).toByteBuffer())
                .withMessageContaining("Illegal string length");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("empty-string", HeaderValue.fromString("")), new byte[0])
                        .toByteBuffer())
                .withMessageContaining("Strings may not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("long-string", HeaderValue.fromString("s".repeat(32_768))),
                        new byte[0]).toByteBuffer())
                .withMessageContaining("Illegal string length");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("empty-bytes", HeaderValue.fromByteArray(new byte[0])),
                        new byte[0]).toByteBuffer())
                .withMessageContaining("Byte arrays may not be empty");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Message(Map.of("long-bytes", HeaderValue.fromByteArray(new byte[32_768])),
                        new byte[0]).toByteBuffer())
                .withMessageContaining("Illegal byte array length");
    }

    @Test
    void decodeHonorsCurrentByteBufferPositionAndAdvancesToMessageEnd() {
        Message message = new Message(Map.of("offset", HeaderValue.fromLong(55L)), new byte[] {1, 3, 5});
        byte[] encoded = toBytes(message.toByteBuffer());
        byte[] wrapped = new byte[encoded.length + 6];
        Arrays.fill(wrapped, 0, 3, (byte) 0x7f);
        System.arraycopy(encoded, 0, wrapped, 3, encoded.length);
        ByteBuffer buffer = ByteBuffer.wrap(wrapped);
        buffer.position(3);
        buffer.limit(3 + encoded.length);

        Message decoded = Message.decode(buffer);

        assertThat(decoded).isEqualTo(message);
        assertThat(buffer.position()).isEqualTo(3 + encoded.length);
    }

    @Test
    void corruptedChecksumsAreRejected() {
        Message message = new Message(Map.of("crc", HeaderValue.fromString("checked")), new byte[] {1, 2, 3, 4});

        byte[] badPrelude = toBytes(message.toByteBuffer());
        badPrelude[0] ^= 0x01;
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Message.decode(ByteBuffer.wrap(badPrelude)))
                .withMessageContaining("Prelude checksum failure");

        byte[] badMessage = toBytes(message.toByteBuffer());
        badMessage[badMessage.length - 5] ^= 0x01;
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> Message.decode(ByteBuffer.wrap(badMessage)))
                .withMessageContaining("Message checksum failure");
    }

    @Test
    void toStringRendersTextPayloadsAndBase64BinaryPayloads() {
        Message text = new Message(
                Map.of(":content-type", HeaderValue.fromString("application/json")),
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
        Message binary = new Message(Map.of(), new byte[] {0, 1, 2, 3});

        assertThat(text.toString()).contains(":content-type: \"application/json\"", "{\"ok\":true}");
        assertThat(binary.toString()).contains(Base64.getEncoder().encodeToString(new byte[] {0, 1, 2, 3}));
    }

    @Test
    void nullInputsAreRejectedByPublicFactoriesAndConstructors() {
        assertThrows(NullPointerException.class, () -> new Message(null, new byte[0]));
        assertThrows(NullPointerException.class, () -> new Message(Map.of(), null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromByteArray(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromByteBuffer(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromString(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromTimestamp(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromDate(null));
        assertThrows(NullPointerException.class, () -> HeaderValue.fromUuid(null));
    }

    private static Map<String, HeaderValue> representativeHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put(":message-type", HeaderValue.fromString("event"));
        headers.put(":content-type", HeaderValue.fromString("application/json"));
        headers.put("boolTrue", HeaderValue.fromBoolean(true));
        headers.put("boolFalse", HeaderValue.fromBoolean(false));
        headers.put("int", HeaderValue.fromInteger(Integer.MIN_VALUE + 123));
        headers.put("long", HeaderValue.fromLong(Long.MAX_VALUE - 456));
        headers.put("bytes", HeaderValue.fromByteArray(new byte[] {0, 1, 2, 127, -128}));
        headers.put("timestamp", HeaderValue.fromTimestamp(Instant.ofEpochMilli(1_234_567_890L)));
        headers.put("uuid", HeaderValue.fromUuid(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")));
        return headers;
    }

    private static void assertRepresentativeHeaders(Map<String, HeaderValue> headers) {
        assertThat(headers.get(":message-type").getString()).isEqualTo("event");
        assertThat(headers.get(":content-type").getString()).isEqualTo("application/json");
        assertThat(headers.get("boolTrue").getBoolean()).isTrue();
        assertThat(headers.get("boolFalse").getBoolean()).isFalse();
        assertThat(headers.get("int").getInteger()).isEqualTo(Integer.MIN_VALUE + 123);
        assertThat(headers.get("long").getLong()).isEqualTo(Long.MAX_VALUE - 456);
        assertThat(headers.get("bytes").getByteArray())
                .containsExactly((byte) 0, (byte) 1, (byte) 2, (byte) 127, (byte) -128);
        assertThat(headers.get("timestamp").getTimestamp()).isEqualTo(Instant.ofEpochMilli(1_234_567_890L));
        assertThat(headers.get("uuid").getUuid()).isEqualTo(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"));
    }

    private static byte[] toBytes(ByteBuffer buffer) {
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
