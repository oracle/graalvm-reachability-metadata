/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.aws_json_protocol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ClientEndpointProvider;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.protocol.MarshallLocation;
import software.amazon.awssdk.core.protocol.MarshallingType;
import software.amazon.awssdk.core.traits.LocationTrait;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.ExceptionMetadata;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;
import software.amazon.awssdk.protocols.json.AwsJsonProtocol;
import software.amazon.awssdk.protocols.json.AwsJsonProtocolFactory;
import software.amazon.awssdk.protocols.json.AwsJsonProtocolMetadata;
import software.amazon.awssdk.protocols.json.DefaultJsonContentTypeResolver;
import software.amazon.awssdk.protocols.json.JsonContent;
import software.amazon.awssdk.protocols.json.JsonOperationMetadata;
import software.amazon.awssdk.protocols.json.SdkJsonGenerator;
import software.amazon.awssdk.protocols.json.StructuredJsonGenerator;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.thirdparty.jackson.core.JsonFactory;
import software.amazon.awssdk.utils.BinaryUtils;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.utils.StringInputStream;
import software.amazon.awssdk.utils.builder.Buildable;

public class Aws_json_protocolTest {
    private static final URI ENDPOINT = URI.create("https://service.example.com");
    private static final JsonOperationMetadata JSON_PAYLOAD_OPERATION = JsonOperationMetadata.builder()
                                                                                            .isPayloadJson(true)
                                                                                            .build();

    @Test
    void contentTypeResolverUsesProtocolVersionAndRestJsonOverride() {
        DefaultJsonContentTypeResolver resolver = new DefaultJsonContentTypeResolver("application/x-amz-json-");
        AwsJsonProtocolMetadata awsJsonMetadata = AwsJsonProtocolMetadata.builder()
                                                                        .protocol(AwsJsonProtocol.AWS_JSON)
                                                                        .protocolVersion("1.1")
                                                                        .build();
        AwsJsonProtocolMetadata restJsonMetadata = AwsJsonProtocolMetadata.builder()
                                                                         .protocol(AwsJsonProtocol.REST_JSON)
                                                                         .protocolVersion("1.0")
                                                                         .build();

        assertThat(resolver.resolveContentType(awsJsonMetadata)).isEqualTo("application/x-amz-json-1.1");
        assertThat(resolver.resolveContentType(restJsonMetadata)).isEqualTo("application/json");

        AwsJsonProtocolFactory explicitContentTypeFactory = AwsJsonProtocolFactory.builder()
                                                                                 .clientConfiguration(
                                                                                     clientConfiguration())
                                                                                 .protocol(AwsJsonProtocol.AWS_JSON)
                                                                                 .protocolVersion("1.1")
                                                                                 .contentType("application/custom-json")
                                                                                 .build();
        assertThat(explicitContentTypeFactory.getContentType()).isEqualTo("application/custom-json");
    }

    @Test
    void structuredJsonGeneratorWritesScalarBinaryTemporalAndNumericValues() {
        byte[] binary = new byte[] {1, 2, 3, 4};
        StructuredJsonGenerator generator = new SdkJsonGenerator(new JsonFactory(), "application/json");

        generator.writeStartObject()
                 .writeFieldName("string").writeValue("hello")
                 .writeFieldName("boolean").writeValue(true)
                 .writeFieldName("integer").writeValue(7)
                 .writeFieldName("long").writeValue(Long.MAX_VALUE)
                 .writeFieldName("float").writeValue(1.25f)
                 .writeFieldName("double").writeValue(2.5d)
                 .writeFieldName("short").writeValue((short) 3)
                 .writeFieldName("byte").writeValue((byte) 4)
                 .writeFieldName("binary").writeValue(ByteBuffer.wrap(binary))
                 .writeFieldName("instant").writeValue(Instant.ofEpochMilli(123456))
                 .writeFieldName("bigDecimal").writeValue(new BigDecimal("1234567890.123456789"))
                 .writeFieldName("bigInteger").writeValue(new BigInteger("9876543210123456789"))
                 .writeFieldName("rawNumber").writeNumber("6.02")
                 .writeFieldName("nullValue").writeNull()
                 .writeEndObject();

        String json = new String(generator.getBytes(), UTF_8);

        assertThat(json).contains("\"string\":\"hello\"")
                        .contains("\"boolean\":true")
                        .contains("\"integer\":7")
                        .contains("\"long\":9223372036854775807")
                        .contains("\"float\":1.25")
                        .contains("\"double\":2.5")
                        .contains("\"short\":3")
                        .contains("\"byte\":4")
                        .contains("\"binary\":\"" + BinaryUtils.toBase64(binary) + "\"")
                        .contains("\"instant\":123.456")
                        .contains("\"bigDecimal\":\"1234567890.123456789\"")
                        .contains("\"bigInteger\":9876543210123456789")
                        .contains("\"rawNumber\":6.02")
                        .contains("\"nullValue\":null");
    }

    @Test
    void noOpStructuredJsonGeneratorIgnoresEveryWrite() {
        byte[] bytes = StructuredJsonGenerator.NO_OP.writeStartArray(2)
                                                 .writeValue("ignored")
                                                 .writeStartObject()
                                                 .writeFieldName("ignored")
                                                 .writeValue(1)
                                                 .writeEndObject()
                                                 .writeEndArray()
                                                 .getBytes();

        assertThat(bytes).isNull();
    }

    @Test
    void jsonContentReadsRawBytesAndParsesJsonNode() {
        String payload = "{\"message\":\"ok\",\"count\":3}";
        SdkHttpFullResponse response = responseWithJson(200, payload);

        JsonContent content = JsonContent.createJsonContent(response, new JsonFactory());
        JsonNode node = content.getJsonNode();

        assertThat(new String(content.getRawContent(), UTF_8)).isEqualTo(payload);
        assertThat(node.asObject().get("message").text()).isEqualTo("ok");
        assertThat(node.asObject().get("count").asNumber()).isEqualTo("3");
    }

    @Test
    void protocolMarshallerBuildsRestJsonRequestWithPathQueryHeaderAndJsonPayload() throws IOException {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.REST_JSON)
                                                              .protocolVersion("1.1")
                                                              .build();
        OperationInfo operationInfo = OperationInfo.builder()
                                                   .requestUri("/items/{ItemId}")
                                                   .httpMethod(SdkHttpMethod.PUT)
                                                   .operationIdentifier("ExampleService.UpdateItem")
                                                   .hasPayloadMembers(true)
                                                   .hasImplicitPayloadMembers(true)
                                                   .build();
        ProtocolPojo pojo = ProtocolPojo.requestPojo();

        ProtocolMarshaller<SdkHttpFullRequest> marshaller = factory.createProtocolMarshaller(operationInfo);
        SdkHttpFullRequest request = marshaller.marshall(pojo);
        String payload = utf8Content(request);

        assertThat(request.method()).isEqualTo(SdkHttpMethod.PUT);
        assertThat(request.host()).isEqualTo("service.example.com");
        assertThat(request.encodedPath()).isEqualTo("/items/item-123");
        assertThat(request.firstMatchingRawQueryParameter("maxResults")).contains("25");
        assertThat(request.firstMatchingHeader("X-Custom-Header")).contains("header-value");
        assertThat(request.firstMatchingHeader("Content-Type")).contains("application/json");
        assertThat(payload).contains("\"Name\":\"Widget\"")
                           .contains("\"Enabled\":true")
                           .contains("\"Count\":42");
    }

    @Test
    void protocolMarshallerAddsAwsJsonTargetAndQueryCompatibilityHeaders() throws IOException {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.AWS_JSON)
                                                              .protocolVersion("1.1")
                                                              .hasAwsQueryCompatible(true)
                                                              .build();
        OperationInfo operationInfo = OperationInfo.builder()
                                                   .requestUri("/")
                                                   .httpMethod(SdkHttpMethod.POST)
                                                   .operationIdentifier("ExampleService.UpdateItem")
                                                   .hasPayloadMembers(true)
                                                   .hasImplicitPayloadMembers(true)
                                                   .build();

        SdkHttpFullRequest request = factory.createProtocolMarshaller(operationInfo)
                                            .marshall(ProtocolPojo.requestPojo());

        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(request.encodedPath()).isEqualTo("/");
        assertThat(request.firstMatchingHeader("Content-Type")).contains("application/x-amz-json-1.1");
        assertThat(request.firstMatchingHeader("X-Amz-Target")).contains("ExampleService.UpdateItem");
        assertThat(request.firstMatchingHeader("x-amzn-query-mode")).contains("true");
        assertThat(utf8Content(request)).contains("\"Name\":\"Widget\"")
                                        .contains("\"Enabled\":true")
                                        .contains("\"Count\":42");
    }

    @Test
    void responseHandlerUnmarshallsJsonPayloadAndHeadersIntoSdkPojo() throws Exception {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.REST_JSON)
                                                              .protocolVersion("1.1")
                                                              .build();
        HttpResponseHandler<ProtocolPojo> handler = factory.createResponseHandler(JSON_PAYLOAD_OPERATION,
                                                                                  ProtocolPojo::new);
        SdkHttpFullResponse response = responseWithJson(200, """
            {"Name":"ResponseWidget","Enabled":false,"Count":9}
            """).toBuilder()
               .putHeader("X-Custom-Header", "response-header")
               .build();

        ProtocolPojo pojo = handler.handle(response, new ExecutionAttributes());

        assertThat(pojo.name()).isEqualTo("ResponseWidget");
        assertThat(pojo.enabled()).isFalse();
        assertThat(pojo.count()).isEqualTo(9);
        assertThat(pojo.customHeader()).isEqualTo("response-header");
    }

    @Test
    void protocolMarshallerSerializesDocumentPayloadMember() throws IOException {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.REST_JSON)
                                                              .protocolVersion("1.1")
                                                              .build();
        OperationInfo operationInfo = OperationInfo.builder()
                                                   .requestUri("/documents")
                                                   .httpMethod(SdkHttpMethod.POST)
                                                   .operationIdentifier("ExampleService.PutDocument")
                                                   .hasPayloadMembers(true)
                                                   .hasImplicitPayloadMembers(true)
                                                   .build();

        SdkHttpFullRequest request = factory.createProtocolMarshaller(operationInfo)
                                            .marshall(DocumentPojo.requestPojo());
        String payload = utf8Content(request);

        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(request.encodedPath()).isEqualTo("/documents");
        assertThat(payload).contains("\"Document\":")
                           .contains("\"title\":\"example\"")
                           .contains("\"active\":true")
                           .contains("\"count\":3")
                           .contains("\"tags\":[\"alpha\",\"beta\"]")
                           .contains("\"metadata\":")
                           .contains("\"empty\":null");
    }

    @Test
    void responseHandlerUnmarshallsDocumentPayloadMember() throws Exception {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.REST_JSON)
                                                              .protocolVersion("1.1")
                                                              .build();
        HttpResponseHandler<DocumentPojo> handler = factory.createResponseHandler(JSON_PAYLOAD_OPERATION,
                                                                                  DocumentPojo::new);
        SdkHttpFullResponse response = responseWithJson(200, """
            {
              "Document": {
                "title": "example",
                "active": true,
                "count": 3,
                "tags": ["alpha", "beta"],
                "metadata": {"empty": null}
              }
            }
            """);

        Document document = handler.handle(response, new ExecutionAttributes()).document();

        assertThat(document.asMap().get("title").asString()).isEqualTo("example");
        assertThat(document.asMap().get("active").asBoolean()).isTrue();
        assertThat(document.asMap().get("count").asNumber().stringValue()).isEqualTo("3");
        assertThat(document.asMap().get("tags").asList())
            .extracting(Document::asString)
            .containsExactly("alpha", "beta");
        assertThat(document.asMap().get("metadata").asMap().get("empty").isNull()).isTrue();
    }

    @Test
    void errorResponseHandlerExtractsModeledAwsJsonErrorDetails() throws Exception {
        AwsJsonProtocolFactory factory = AwsJsonProtocolFactory.builder()
                                                              .clientConfiguration(clientConfiguration())
                                                              .protocol(AwsJsonProtocol.AWS_JSON)
                                                              .protocolVersion("1.1")
                                                              .defaultServiceExceptionSupplier(
                                                                  AwsServiceException::builder)
                                                              .build();
        ExceptionMetadata modeledException = ExceptionMetadata.builder()
                                                             .errorCode("ModeledException")
                                                             .exceptionBuilderSupplier(AwsServiceException::builder)
                                                             .httpStatusCode(400)
                                                             .build();
        HttpResponseHandler<AwsServiceException> handler = factory.createErrorResponseHandler(
            JSON_PAYLOAD_OPERATION,
            errorCode -> "ModeledException".equals(errorCode) ? Optional.of(modeledException) : Optional.empty());
        SdkHttpFullResponse response = responseWithJson(400, """
            {"__type":"com.example#ModeledException","message":"request failed"}
            """).toBuilder()
               .putHeader("x-amzn-RequestId", "request-123")
               .build();

        AwsServiceException exception = handler.handle(response, new ExecutionAttributes());

        assertThat(exception.statusCode()).isEqualTo(400);
        assertThat(exception.requestId()).isEqualTo("request-123");
        assertThat(exception.awsErrorDetails().errorCode()).isEqualTo("ModeledException");
        assertThat(exception.awsErrorDetails().errorMessage()).isEqualTo("request failed");
        assertThat(exception.awsErrorDetails().sdkHttpResponse().statusCode()).isEqualTo(400);
        assertThat(new String(exception.awsErrorDetails().rawResponse().asByteArray(), UTF_8))
            .contains("ModeledException")
            .contains("request failed");
    }

    private static SdkClientConfiguration clientConfiguration() {
        return SdkClientConfiguration.builder()
                                     .option(SdkClientOption.CLIENT_ENDPOINT_PROVIDER,
                                             ClientEndpointProvider.forEndpointOverride(ENDPOINT))
                                     .build();
    }

    private static SdkHttpFullResponse responseWithJson(int statusCode, String json) {
        return SdkHttpFullResponse.builder()
                                  .statusCode(statusCode)
                                  .putHeader("Content-Type", "application/json")
                                  .content(AbortableInputStream.create(new StringInputStream(json.trim())))
                                  .build();
    }

    private static String utf8Content(SdkHttpFullRequest request) throws IOException {
        Optional<software.amazon.awssdk.http.ContentStreamProvider> provider = request.contentStreamProvider();
        if (provider.isEmpty()) {
            return "";
        }
        try (InputStream stream = provider.get().newStream()) {
            return IoUtils.toUtf8String(stream);
        }
    }

    private static SdkField<String> stringField(String memberName, MarshallLocation location, String locationName) {
        return SdkField.<String>builder(MarshallingType.STRING)
                       .memberName(memberName)
                       .getter(getterTarget -> ((ProtocolPojo) getterTarget).value(memberName))
                       .setter((setterTarget, value) -> ((ProtocolPojo) setterTarget).setValue(memberName, value))
                       .traits(LocationTrait.builder()
                                            .location(location)
                                            .locationName(locationName)
                                            .unmarshallLocationName(locationName)
                                            .build())
                       .build();
    }

    private static SdkField<Integer> integerField(String memberName, MarshallLocation location, String locationName) {
        return SdkField.<Integer>builder(MarshallingType.INTEGER)
                       .memberName(memberName)
                       .getter(getterTarget -> ((ProtocolPojo) getterTarget).integerValue(memberName))
                       .setter((setterTarget, value) -> ((ProtocolPojo) setterTarget).setIntegerValue(memberName,
                                                                                                       value))
                       .traits(LocationTrait.builder()
                                            .location(location)
                                            .locationName(locationName)
                                            .unmarshallLocationName(locationName)
                                            .build())
                       .build();
    }

    private static SdkField<Boolean> booleanField(String memberName, MarshallLocation location, String locationName) {
        return SdkField.<Boolean>builder(MarshallingType.BOOLEAN)
                       .memberName(memberName)
                       .getter(getterTarget -> ((ProtocolPojo) getterTarget).booleanValue(memberName))
                       .setter((setterTarget, value) -> ((ProtocolPojo) setterTarget).setBooleanValue(memberName,
                                                                                                       value))
                       .traits(LocationTrait.builder()
                                            .location(location)
                                            .locationName(locationName)
                                            .unmarshallLocationName(locationName)
                                            .build())
                       .build();
    }

    private static SdkField<Document> documentField(String memberName, MarshallLocation location, String locationName) {
        return SdkField.<Document>builder(MarshallingType.DOCUMENT)
                       .memberName(memberName)
                       .getter(getterTarget -> ((DocumentPojo) getterTarget).documentValue(memberName))
                       .setter((setterTarget, value) -> ((DocumentPojo) setterTarget).setDocumentValue(memberName,
                                                                                                        value))
                       .traits(LocationTrait.builder()
                                            .location(location)
                                            .locationName(locationName)
                                            .unmarshallLocationName(locationName)
                                            .build())
                       .build();
    }

    private static final class ProtocolPojo implements SdkPojo, Buildable {
        private static final String ITEM_ID = "itemId";
        private static final String NAME = "name";
        private static final String MAX_RESULTS = "maxResults";
        private static final String CUSTOM_HEADER = "customHeader";
        private static final String ENABLED = "enabled";
        private static final String COUNT = "count";

        private static final SdkField<String> ITEM_ID_FIELD = stringField(ITEM_ID, MarshallLocation.PATH, "ItemId");
        private static final SdkField<String> NAME_FIELD = stringField(NAME, MarshallLocation.PAYLOAD, "Name");
        private static final SdkField<Integer> MAX_RESULTS_FIELD = integerField(MAX_RESULTS,
                                                                                MarshallLocation.QUERY_PARAM,
                                                                                "maxResults");
        private static final SdkField<String> CUSTOM_HEADER_FIELD = stringField(CUSTOM_HEADER,
                                                                                MarshallLocation.HEADER,
                                                                                "X-Custom-Header");
        private static final SdkField<Boolean> ENABLED_FIELD = booleanField(ENABLED,
                                                                            MarshallLocation.PAYLOAD,
                                                                            "Enabled");
        private static final SdkField<Integer> COUNT_FIELD = integerField(COUNT, MarshallLocation.PAYLOAD, "Count");
        private static final List<SdkField<?>> REQUEST_SDK_FIELDS = Arrays.asList(ITEM_ID_FIELD,
                                                                                  NAME_FIELD,
                                                                                  MAX_RESULTS_FIELD,
                                                                                  CUSTOM_HEADER_FIELD,
                                                                                  ENABLED_FIELD,
                                                                                  COUNT_FIELD);
        private static final List<SdkField<?>> RESPONSE_SDK_FIELDS = Arrays.asList(NAME_FIELD,
                                                                                   CUSTOM_HEADER_FIELD,
                                                                                   ENABLED_FIELD,
                                                                                   COUNT_FIELD);

        private boolean requestShape;
        private String itemId;
        private String name;
        private Integer maxResults;
        private String customHeader;
        private Boolean enabled;
        private Integer count;

        static ProtocolPojo requestPojo() {
            ProtocolPojo pojo = new ProtocolPojo();
            pojo.requestShape = true;
            pojo.itemId = "item-123";
            pojo.name = "Widget";
            pojo.maxResults = 25;
            pojo.customHeader = "header-value";
            pojo.enabled = true;
            pojo.count = 42;
            return pojo;
        }

        String name() {
            return name;
        }

        String customHeader() {
            return customHeader;
        }

        Boolean enabled() {
            return enabled;
        }

        Integer count() {
            return count;
        }

        String value(String memberName) {
            switch (memberName) {
                case ITEM_ID:
                    return itemId;
                case NAME:
                    return name;
                case CUSTOM_HEADER:
                    return customHeader;
                default:
                    throw new IllegalArgumentException("Unknown string member: " + memberName);
            }
        }

        void setValue(String memberName, String value) {
            switch (memberName) {
                case ITEM_ID:
                    itemId = value;
                    return;
                case NAME:
                    name = value;
                    return;
                case CUSTOM_HEADER:
                    customHeader = value;
                    return;
                default:
                    throw new IllegalArgumentException("Unknown string member: " + memberName);
            }
        }

        Integer integerValue(String memberName) {
            switch (memberName) {
                case MAX_RESULTS:
                    return maxResults;
                case COUNT:
                    return count;
                default:
                    throw new IllegalArgumentException("Unknown integer member: " + memberName);
            }
        }

        void setIntegerValue(String memberName, Integer value) {
            switch (memberName) {
                case MAX_RESULTS:
                    maxResults = value;
                    return;
                case COUNT:
                    count = value;
                    return;
                default:
                    throw new IllegalArgumentException("Unknown integer member: " + memberName);
            }
        }

        Boolean booleanValue(String memberName) {
            if (ENABLED.equals(memberName)) {
                return enabled;
            }
            throw new IllegalArgumentException("Unknown boolean member: " + memberName);
        }

        void setBooleanValue(String memberName, Boolean value) {
            if (ENABLED.equals(memberName)) {
                enabled = value;
                return;
            }
            throw new IllegalArgumentException("Unknown boolean member: " + memberName);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return requestShape ? REQUEST_SDK_FIELDS : RESPONSE_SDK_FIELDS;
        }

        @Override
        public ProtocolPojo build() {
            return this;
        }

        @Override
        public Map<String, SdkField<?>> sdkFieldNameToField() {
            return SdkPojo.super.sdkFieldNameToField();
        }
    }

    private static final class DocumentPojo implements SdkPojo, Buildable {
        private static final String DOCUMENT = "document";
        private static final SdkField<Document> DOCUMENT_FIELD = documentField(DOCUMENT,
                                                                               MarshallLocation.PAYLOAD,
                                                                               "Document");
        private static final List<SdkField<?>> SDK_FIELDS = Arrays.asList(DOCUMENT_FIELD);

        private Document document;

        static DocumentPojo requestPojo() {
            DocumentPojo pojo = new DocumentPojo();
            pojo.document = Document.fromMap(Map.of("title", Document.fromString("example"),
                                                    "active", Document.fromBoolean(true),
                                                    "count", Document.fromNumber(3),
                                                    "tags", Document.fromList(Arrays.asList(
                                                        Document.fromString("alpha"),
                                                        Document.fromString("beta"))),
                                                    "metadata", Document.fromMap(Map.of("empty",
                                                                                        Document.fromNull()))));
            return pojo;
        }

        Document document() {
            return document;
        }

        Document documentValue(String memberName) {
            if (DOCUMENT.equals(memberName)) {
                return document;
            }
            throw new IllegalArgumentException("Unknown document member: " + memberName);
        }

        void setDocumentValue(String memberName, Document value) {
            if (DOCUMENT.equals(memberName)) {
                document = value;
                return;
            }
            throw new IllegalArgumentException("Unknown document member: " + memberName);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }

        @Override
        public DocumentPojo build() {
            return this;
        }

        @Override
        public Map<String, SdkField<?>> sdkFieldNameToField() {
            return SdkPojo.super.sdkFieldNameToField();
        }
    }
}
