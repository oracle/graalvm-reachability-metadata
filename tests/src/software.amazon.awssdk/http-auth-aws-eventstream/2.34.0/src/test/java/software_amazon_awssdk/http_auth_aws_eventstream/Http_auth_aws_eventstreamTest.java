/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.http_auth_aws_eventstream;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.auth.aws.eventstream.HttpAuthAwsEventStream;
import software.amazon.eventstream.HeaderValue;
import software.amazon.eventstream.Message;
import software.amazon.eventstream.MessageBuilder;
import software.amazon.eventstream.MessageDecoder;

public class Http_auth_aws_eventstreamTest {
    private static final byte[] PAYLOAD = "event stream payload".getBytes(StandardCharsets.UTF_8);
    private static final Instant TIMESTAMP = Instant.parse("2024-02-03T04:05:06.789Z");
    private static final UUID UUID_VALUE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void markerApiCanBeInstantiated() {
        HttpAuthAwsEventStream marker = new HttpAuthAwsEventStream();

        assertThat(marker).isExactlyInstanceOf(HttpAuthAwsEventStream.class);
    }

    @Test
    void eventStreamDependencyEncodesAndDecodesMessagesWithWireCompatibleHeaderKinds() {
        Message message = new Message(wireCompatibleHeaders(), PAYLOAD);

        Message decoded = Message.decode(message.toByteBuffer());

        assertThat(decoded).isEqualTo(message);
        assertThat(decoded.hashCode()).isEqualTo(message.hashCode());
        assertThat(decoded.getPayload()).containsExactly(PAYLOAD);
        assertDecodedHeaders(decoded.getHeaders());
    }

    @Test
    void messageBuilderCreatesMessagesThatRoundTripThroughByteBuffers() {
        byte[] byteBufferHeader = new byte[] {9, 8, 7, 6};
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("source", HeaderValue.fromString("builder"));
        headers.put("sequence", HeaderValue.fromInteger(42));
        headers.put("binary", HeaderValue.fromByteBuffer(ByteBuffer.wrap(byteBufferHeader)));

        Message builtMessage = MessageBuilder.defaultBuilder().build(headers, PAYLOAD);
        Message decoded = Message.decode(builtMessage.toByteBuffer());

        assertThat(decoded).isEqualTo(builtMessage);
        assertThat(decoded.getHeaders().get("source").getString()).isEqualTo("builder");
        assertThat(decoded.getHeaders().get("sequence").getInteger()).isEqualTo(42);
        assertThat(bytesFrom(decoded.getHeaders().get("binary").getByteBuffer())).containsExactly(byteBufferHeader);
        assertThat(decoded.getPayload()).containsExactly(PAYLOAD);
    }

    @Test
    void messageEncodesToOutputStreamAndHeadersEncodeIndependently() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("operation", HeaderValue.fromString("subscribe"));
        headers.put("attempt", HeaderValue.fromInteger(3));
        Message message = new Message(headers, PAYLOAD);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        message.encode(output);
        byte[] encodedMessage = output.toByteArray();
        byte[] encodedHeaders = Message.encodeHeaders(headers.entrySet());
        ByteBuffer wireMessage = ByteBuffer.wrap(encodedMessage);
        int totalLength = wireMessage.getInt();
        int headerLength = wireMessage.getInt();
        wireMessage.getInt();
        byte[] headerSection = new byte[headerLength];
        wireMessage.get(headerSection);

        assertThat(totalLength).isEqualTo(encodedMessage.length);
        assertThat(headerSection).containsExactly(encodedHeaders);
        assertThat(Message.decode(ByteBuffer.wrap(encodedMessage))).isEqualTo(message);
    }

    @Test
    void streamingDecoderHandlesFragmentedInputAndMultipleMessages() {
        Message first = new Message(wireCompatibleHeaders(), PAYLOAD);
        Message second = MessageBuilder.defaultBuilder().build(
                Map.of("message", HeaderValue.fromString("second")),
                "follow-up".getBytes(StandardCharsets.UTF_8));
        byte[] firstBytes = bytesFrom(first.toByteBuffer());
        byte[] secondBytes = bytesFrom(second.toByteBuffer());
        List<Message> decodedMessages = new ArrayList<>();
        MessageDecoder decoder = new MessageDecoder(decodedMessages::add);

        int split = firstBytes.length / 2;
        decoder.feed(firstBytes, 0, split);
        assertThat(decodedMessages).isEmpty();

        decoder.feed(firstBytes, split, firstBytes.length - split);
        decoder.feed(ByteBuffer.wrap(secondBytes));

        assertThat(decodedMessages).containsExactly(first, second);
    }

    @Test
    void headerValueFactoriesExposePrimitiveAccessors() {
        assertThat(HeaderValue.fromBoolean(true).getBoolean()).isTrue();
        assertThat(HeaderValue.fromBoolean(false).getBoolean()).isFalse();
        assertThat(HeaderValue.fromByte((byte) 0x7F).getByte()).isEqualTo((byte) 0x7F);
        assertThat(HeaderValue.fromShort((short) 32_000).getShort()).isEqualTo((short) 32_000);
        assertThat(HeaderValue.fromInteger(1_234_567).getInteger()).isEqualTo(1_234_567);
        assertThat(HeaderValue.fromLong(9_876_543_210L).getLong()).isEqualTo(9_876_543_210L);
        assertThat(HeaderValue.fromByteArray(new byte[] {1, 2, 3, 4}).getByteArray())
                .containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThat(HeaderValue.fromString("hello event stream").getString()).isEqualTo("hello event stream");
        assertThat(HeaderValue.fromTimestamp(TIMESTAMP).getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(HeaderValue.fromDate(Date.from(TIMESTAMP)).getDate()).isEqualTo(Date.from(TIMESTAMP));
        assertThat(HeaderValue.fromUuid(UUID_VALUE).getUuid()).isEqualTo(UUID_VALUE);
    }

    @Test
    void defaultDecoderCollectsDecodedMessages() {
        Message message = new Message(Map.of("name", HeaderValue.fromString("collected")), PAYLOAD);
        byte[] encoded = bytesFrom(message.toByteBuffer());
        MessageDecoder decoder = new MessageDecoder();

        decoder.feed(Arrays.copyOfRange(encoded, 0, 8));
        assertThat(decoder.getDecodedMessages()).isEmpty();

        decoder.feed(Arrays.copyOfRange(encoded, 8, encoded.length));

        assertThat(decoder.getDecodedMessages()).containsExactly(message);
    }

    @Test
    void messageStringRepresentationHonorsContentType() {
        byte[] jsonPayload = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        Message jsonMessage = new Message(
                Map.of(":content-type", HeaderValue.fromString("application/json")),
                jsonPayload);
        byte[] binaryPayload = new byte[] {1, 2, 3, 4};
        Message binaryMessage = new Message(Map.of("event", HeaderValue.fromString("binary")), binaryPayload);

        assertThat(jsonMessage.toString())
                .contains(":content-type: \"application/json\"")
                .endsWith(new String(jsonPayload, StandardCharsets.UTF_8) + "\n");
        assertThat(binaryMessage.toString())
                .contains("event: \"binary\"")
                .endsWith(Base64.getEncoder().encodeToString(binaryPayload) + "\n");
    }

    private static Map<String, HeaderValue> wireCompatibleHeaders() {
        Map<String, HeaderValue> headers = new LinkedHashMap<>();
        headers.put("boolean-true", HeaderValue.fromBoolean(true));
        headers.put("boolean-false", HeaderValue.fromBoolean(false));
        headers.put("integer", HeaderValue.fromInteger(1_234_567));
        headers.put("long", HeaderValue.fromLong(9_876_543_210L));
        headers.put("bytes", HeaderValue.fromByteArray(new byte[] {1, 2, 3, 4}));
        headers.put("string", HeaderValue.fromString("hello event stream"));
        headers.put("timestamp", HeaderValue.fromTimestamp(TIMESTAMP));
        headers.put("uuid", HeaderValue.fromUuid(UUID_VALUE));
        return headers;
    }

    private static void assertDecodedHeaders(Map<String, HeaderValue> headers) {
        assertThat(headers.get("boolean-true").getBoolean()).isTrue();
        assertThat(headers.get("boolean-false").getBoolean()).isFalse();
        assertThat(headers.get("integer").getInteger()).isEqualTo(1_234_567);
        assertThat(headers.get("long").getLong()).isEqualTo(9_876_543_210L);
        assertThat(headers.get("bytes").getByteArray()).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
        assertThat(headers.get("string").getString()).isEqualTo("hello event stream");
        assertThat(headers.get("timestamp").getTimestamp()).isEqualTo(TIMESTAMP);
        assertThat(headers.get("timestamp").getDate()).isEqualTo(Date.from(TIMESTAMP));
        assertThat(headers.get("uuid").getUuid()).isEqualTo(UUID_VALUE);
    }

    private static byte[] bytesFrom(ByteBuffer buffer) {
        ByteBuffer copy = buffer.slice();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
