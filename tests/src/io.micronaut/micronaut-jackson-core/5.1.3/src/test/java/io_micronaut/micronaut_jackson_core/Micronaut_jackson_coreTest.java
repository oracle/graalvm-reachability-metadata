/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut.micronaut_jackson_core;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.env.PropertySourceLoader;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.jackson.core.env.CloudFoundryVcapApplicationPropertySourceLoader;
import io.micronaut.jackson.core.env.CloudFoundryVcapServicesPropertySourceLoader;
import io.micronaut.jackson.core.env.EnvJsonPropertySourceLoader;
import io.micronaut.jackson.core.env.JsonPropertySourceLoader;
import io.micronaut.jackson.core.parser.JacksonCoreParserFactory;
import io.micronaut.jackson.core.parser.JacksonCoreProcessor;
import io.micronaut.jackson.core.tree.JsonNodeTreeCodec;
import io.micronaut.jackson.core.tree.JsonStreamTransfer;
import io.micronaut.jackson.core.tree.TreeGenerator;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import tools.jackson.core.Base64Variants;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.json.JsonFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"deprecation", "removal"})
public class Micronaut_jackson_coreTest {
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    @Test
    void jsonPropertySourceLoaderFlattensNestedJsonAndPreservesValues() throws IOException {
        String json = """
                {
                  "server": {
                    "port": 8080,
                    "enabled": true,
                    "labels": ["blue", null]
                  },
                  "ratio": 1.25,
                  "empty": {}
                }
                """;

        Map<String, Object> properties = new JsonPropertySourceLoader().read("application", input(json));

        assertThat(properties)
                .containsEntry("server.port", 8080)
                .containsEntry("server.enabled", true)
                .containsEntry("server.labels", Arrays.asList("blue", null))
                .containsEntry("ratio", 1.25d)
                .containsEntry("empty", Map.of());
        assertThat(new JsonPropertySourceLoader().getExtensions())
                .containsExactly(JsonPropertySourceLoader.FILE_EXTENSION);
    }

    @Test
    void cloudFoundryLoadersCreateDocumentedPropertyPrefixes() throws IOException {
        String applicationJson = """
                {
                  "application_name": "orders",
                  "limits": {"memory": 512},
                  "uris": ["orders.example.test"]
                }
                """;
        String servicesJson = """
                {
                  "database": [
                    {
                      "name": "primary",
                      "label": "postgresql",
                      "credentials": {"host": "db.internal", "port": 5432}
                    }
                  ],
                  "cache": [
                    {
                      "label": "redis",
                      "credentials": {"host": "cache.internal"}
                    }
                  ]
                }
                """;

        CloudFoundryVcapApplicationPropertySourceLoader applicationLoader =
                new CloudFoundryVcapApplicationPropertySourceLoader();
        Map<String, Object> application = applicationLoader.read("application", input(applicationJson));
        CloudFoundryVcapServicesPropertySourceLoader servicesLoader =
                new CloudFoundryVcapServicesPropertySourceLoader();
        Map<String, Object> services = servicesLoader.read("services", input(servicesJson));

        assertThat(application)
                .containsEntry("vcap.application.application_name", "orders")
                .containsEntry("vcap.application.limits.memory", 512)
                .containsEntry("vcap.application.uris", List.of("orders.example.test"));
        assertThat(services)
                .containsEntry("vcap.services.primary.name", "primary")
                .containsEntry("vcap.services.primary.label", "postgresql")
                .containsEntry("vcap.services.primary.credentials.host", "db.internal")
                .containsEntry("vcap.services.primary.credentials.port", 5432)
                .containsEntry("vcap.services.redis.label", "redis")
                .containsEntry("vcap.services.redis.credentials.host", "cache.internal");
        assertThat(applicationLoader.getExtensions()).containsExactly("VCAP_APPLICATION");
        assertThat(servicesLoader.getExtensions()).containsExactly("VCAP_SERVICES");
        assertThat(applicationLoader.getOrder())
                .isEqualTo(CloudFoundryVcapApplicationPropertySourceLoader.POSITION);
        assertThat(servicesLoader.getOrder())
                .isEqualTo(CloudFoundryVcapServicesPropertySourceLoader.POSITION);
    }

    @Test
    void serviceLoaderDiscoversEveryJacksonPropertySourceProvider() {
        boolean jsonProviderFound = false;
        boolean environmentProviderFound = false;
        boolean applicationProviderFound = false;
        boolean servicesProviderFound = false;

        for (PropertySourceLoader loader : ServiceLoader.load(PropertySourceLoader.class)) {
            if (loader instanceof CloudFoundryVcapApplicationPropertySourceLoader) {
                applicationProviderFound = true;
            } else if (loader instanceof CloudFoundryVcapServicesPropertySourceLoader) {
                servicesProviderFound = true;
            } else if (loader instanceof EnvJsonPropertySourceLoader) {
                environmentProviderFound = true;
            } else if (loader instanceof JsonPropertySourceLoader) {
                jsonProviderFound = true;
            }
        }

        assertThat(jsonProviderFound).isTrue();
        assertThat(environmentProviderFound).isTrue();
        assertThat(applicationProviderFound).isTrue();
        assertThat(servicesProviderFound).isTrue();
    }

    @Test
    void treeCodecReadsNestedJsonWithFullPrecisionConfiguration() throws IOException {
        JsonStreamConfig config = JsonStreamConfig.DEFAULT
                .withUseBigIntegerForInts(true)
                .withUseBigDecimalForFloats(true);
        JsonNodeTreeCodec codec = JsonNodeTreeCodec.getInstance().withConfig(config);
        String json = """
                {
                  "integer": 123456789012345678901234567890,
                  "decimal": 12345.678900,
                  "nested": ["text", true, false, null, {"value": 7}]
                }
                """;

        JsonNode root;
        try (JsonParser parser = parser(json)) {
            root = codec.readTree(parser);
        }

        assertThat(root.isObject()).isTrue();
        assertThat(root.get("integer").getNumberValue())
                .isEqualTo(new BigInteger("123456789012345678901234567890"));
        assertThat(root.get("decimal").getNumberValue()).isEqualTo(new BigDecimal("12345.678900"));
        JsonNode nested = root.get("nested");
        assertThat(nested.isArray()).isTrue();
        assertThat(nested.get(0).getStringValue()).isEqualTo("text");
        assertThat(nested.get(1).getBooleanValue()).isTrue();
        assertThat(nested.get(2).getBooleanValue()).isFalse();
        assertThat(nested.get(3).isNull()).isTrue();
        assertThat(nested.get(4).get("value").getNumberValue()).isEqualTo(new BigInteger("7"));
    }

    @Test
    void treeCodecWritesObjectsArraysAndSupportedNumberTypes() throws IOException {
        List<JsonNode> values = List.of(
                JsonNode.from(Byte.valueOf((byte) 1)),
                JsonNode.from(Short.valueOf((short) 2)),
                JsonNode.createNumberNode(3),
                JsonNode.createNumberNode(4L),
                JsonNode.createNumberNode(5.5f),
                JsonNode.createNumberNode(6.25d),
                JsonNode.createNumberNode(new BigInteger("70000000000000000000")),
                JsonNode.createNumberNode(new BigDecimal("8.750")),
                JsonNode.createBooleanNode(true),
                JsonNode.nullNode(),
                JsonNode.createStringNode("text"));
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put("values", JsonNode.createArrayNode(values));
        JsonNode root = JsonNode.createObjectNode(fields);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (JsonGenerator generator = JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), output)) {
            JsonNodeTreeCodec.getInstance().writeTree(generator, root);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .isEqualTo(
                        "{\"values\":[1,2,3,4,5.5,6.25,70000000000000000000,8.750,true,null,\"text\"]}");
    }

    @Test
    void treeAsTokensTraversesFieldsContainersAndScalarValues() throws IOException {
        Map<String, JsonNode> fields = new LinkedHashMap<>();
        fields.put("binary", JsonNode.createStringNode("SGVsbG8="));
        fields.put("count", JsonNode.createNumberNode(42));
        fields.put("decimal", JsonNode.createNumberNode(new BigDecimal("2.50")));
        fields.put(
                "flags",
                JsonNode.createArrayNode(List.of(JsonNode.createBooleanNode(true), JsonNode.nullNode())));
        JsonNode root = JsonNode.createObjectNode(fields);
        List<JsonToken> tokens = new ArrayList<>();

        try (JsonParser parser = JsonNodeTreeCodec.getInstance().treeAsTokens(root, ObjectReadContext.empty())) {
            assertThat(parser.streamReadInputSource()).isSameAs(root);
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                tokens.add(token);
                if (token == JsonToken.PROPERTY_NAME && "binary".equals(parser.currentName())) {
                    assertThat(parser.getText()).isEqualTo("binary");
                } else if (token == JsonToken.VALUE_STRING) {
                    assertThat(parser.getBinaryValue(Base64Variants.getDefaultVariant()))
                            .containsExactly("Hello".getBytes(StandardCharsets.UTF_8));
                } else if (token == JsonToken.VALUE_NUMBER_INT) {
                    assertThat(parser.getNumberType()).isEqualTo(JsonParser.NumberType.INT);
                    assertThat(parser.getIntValue()).isEqualTo(42);
                } else if (token == JsonToken.VALUE_NUMBER_FLOAT) {
                    assertThat(parser.getNumberType()).isEqualTo(JsonParser.NumberType.BIG_DECIMAL);
                    assertThat(parser.getDecimalValue()).isEqualByComparingTo("2.50");
                }
            }
            assertThat(parser.isClosed()).isTrue();
        }

        assertThat(tokens)
                .containsExactly(
                        JsonToken.START_OBJECT,
                        JsonToken.PROPERTY_NAME,
                        JsonToken.VALUE_STRING,
                        JsonToken.PROPERTY_NAME,
                        JsonToken.VALUE_NUMBER_INT,
                        JsonToken.PROPERTY_NAME,
                        JsonToken.VALUE_NUMBER_FLOAT,
                        JsonToken.PROPERTY_NAME,
                        JsonToken.START_ARRAY,
                        JsonToken.VALUE_TRUE,
                        JsonToken.VALUE_NULL,
                        JsonToken.END_ARRAY,
                        JsonToken.END_OBJECT);
    }

    @Test
    void streamTransferBuildsEquivalentTreesFromCurrentAndNextTokens() throws IOException {
        JsonStreamConfig preciseConfig = JsonStreamConfig.DEFAULT
                .withUseBigIntegerForInts(true)
                .withUseBigDecimalForFloats(true);
        TreeGenerator objectGenerator = JsonNodeTreeCodec.getInstance().createTreeGenerator();
        String json = """
                {"name":"sample","active":true,"missing":null,"values":[9,1.25]}
                """;

        try (JsonParser parser = parser(json)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            JsonStreamTransfer.transfer(parser, objectGenerator, preciseConfig);
        }

        assertThat(objectGenerator.isComplete()).isTrue();
        JsonNode object = objectGenerator.getCompletedValue();
        assertThat(object.get("name").getStringValue()).isEqualTo("sample");
        assertThat(object.get("active").getBooleanValue()).isTrue();
        assertThat(object.get("missing").isNull()).isTrue();
        assertThat(object.get("values").get(0).getNumberValue()).isEqualTo(new BigInteger("9"));
        assertThat(object.get("values").get(1).getNumberValue()).isEqualTo(new BigDecimal("1.25"));

        TreeGenerator scalarGenerator = JsonNodeTreeCodec.getInstance().createTreeGenerator();
        try (JsonParser parser = parser("17")) {
            JsonStreamTransfer.transferNext(parser, scalarGenerator, JsonStreamConfig.DEFAULT);
        }
        assertThat(scalarGenerator.getCompletedValue().getIntValue()).isEqualTo(17);
    }

    @Test
    void parserFactoryCreatesParsersForMicronautByteBuffers() throws IOException {
        ByteBuffer<byte[]> contextualBuffer = ByteArrayBufferFactory.INSTANCE.wrap(bytes("{\"value\":11}"));

        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(
                JSON_FACTORY, ObjectReadContext.empty(), contextualBuffer)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_OBJECT);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.PROPERTY_NAME);
            assertThat(parser.currentName()).isEqualTo("value");
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_NUMBER_INT);
            assertThat(parser.getIntValue()).isEqualTo(11);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
        }

        ByteBuffer<byte[]> legacyBuffer = ByteArrayBufferFactory.INSTANCE.wrap(bytes("[true,false]"));
        try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(JSON_FACTORY, legacyBuffer)) {
            assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_TRUE);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.VALUE_FALSE);
            assertThat(parser.nextToken()).isEqualTo(JsonToken.END_ARRAY);
        }
    }

    @Test
    void parserFactoryReadsTheReadableRegionOfHeapAndDirectNettyBuffers() throws IOException {
        String prefix = "ignored-prefix";
        String json = "{\"source\":\"netty\",\"value\":29}";
        String suffix = "ignored-suffix";
        byte[] content = bytes(prefix + json + suffix);
        List<ByteBuf> nativeBuffers = List.of(
                Unpooled.wrappedBuffer(content), Unpooled.directBuffer(content.length).writeBytes(content));

        for (ByteBuf nativeBuffer : nativeBuffers) {
            try {
                nativeBuffer.setIndex(bytes(prefix).length, bytes(prefix + json).length);
                ByteBuffer<ByteBuf> buffer = NettyByteBufferFactory.DEFAULT.wrap(nativeBuffer);

                try (JsonParser parser = JacksonCoreParserFactory.createJsonParser(
                        JSON_FACTORY, ObjectReadContext.empty(), buffer)) {
                    JsonNode root = JsonNodeTreeCodec.getInstance().readTree(parser);
                    assertThat(root.get("source").getStringValue()).isEqualTo("netty");
                    assertThat(root.get("value").getIntValue()).isEqualTo(29);
                }
            } finally {
                nativeBuffer.release();
            }
        }
    }

    @Test
    void processorMaterializesOneTreeFromChunkedInput() {
        JacksonCoreProcessor processor =
                new JacksonCoreProcessor(false, JSON_FACTORY, JsonStreamConfig.DEFAULT);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        RecordingSubscription upstream = connect(processor, subscriber);

        processor.onNext(bytes("{\"message\":\"hel"));
        assertThat(processor.needMoreInput()).isTrue();
        processor.onNext(bytes("lo\",\"count\":2}"));
        processor.onComplete();

        assertThat(subscriber.error).isNull();
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.nodes).hasSize(1);
        assertThat(subscriber.nodes.get(0).get("message").getStringValue()).isEqualTo("hello");
        assertThat(subscriber.nodes.get(0).get("count").getIntValue()).isEqualTo(2);
        assertThat(upstream.requestCalls).isPositive();
        assertThat(upstream.cancelled).isFalse();
    }

    @Test
    void processorStreamsRootArrayElementsIndividually() {
        JacksonCoreProcessor processor =
                new JacksonCoreProcessor(true, JSON_FACTORY, JsonStreamConfig.DEFAULT);
        RecordingSubscriber subscriber = new RecordingSubscriber();
        connect(processor, subscriber);

        processor.onNext(bytes("[{\"id\":1},"));
        processor.onNext(bytes("{\"id\":2},{\"id\":3}]"));
        processor.onComplete();

        assertThat(subscriber.error).isNull();
        assertThat(subscriber.completed).isTrue();
        assertThat(subscriber.nodes)
                .extracting(node -> node.get("id").getIntValue())
                .containsExactly(1, 2, 3);
    }

    private static RecordingSubscription connect(
            JacksonCoreProcessor processor, RecordingSubscriber subscriber) {
        RecordingSubscription upstream = new RecordingSubscription();
        processor.subscribe(subscriber);
        processor.onSubscribe(upstream);
        return upstream;
    }

    private static JsonParser parser(String json) throws IOException {
        return JSON_FACTORY.createParser(ObjectReadContext.empty(), bytes(json));
    }

    private static ByteArrayInputStream input(String value) {
        return new ByteArrayInputStream(bytes(value));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingSubscriber implements Subscriber<JsonNode> {
        private final List<JsonNode> nodes = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(16);
        }

        @Override
        public void onNext(JsonNode node) {
            nodes.add(node);
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
        }

        @Override
        public void onComplete() {
            completed = true;
        }
    }

    private static final class RecordingSubscription implements Subscription {
        private int requestCalls;
        private boolean cancelled;

        @Override
        public void request(long count) {
            requestCalls++;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
