/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.aws_query_protocol;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ClientEndpointProvider;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.client.config.SdkClientConfiguration;
import software.amazon.awssdk.core.client.config.SdkClientOption;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.http.HttpResponseHandler;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.core.protocol.MarshallLocation;
import software.amazon.awssdk.core.protocol.MarshallingType;
import software.amazon.awssdk.core.traits.ListTrait;
import software.amazon.awssdk.core.traits.LocationTrait;
import software.amazon.awssdk.core.traits.MapTrait;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpFullResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.ExceptionMetadata;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.ProtocolMarshaller;
import software.amazon.awssdk.protocols.query.AwsEc2ProtocolFactory;
import software.amazon.awssdk.protocols.query.AwsQueryProtocolFactory;
import software.amazon.awssdk.protocols.query.unmarshall.AwsXmlErrorProtocolUnmarshaller;
import software.amazon.awssdk.protocols.query.unmarshall.XmlDomParser;
import software.amazon.awssdk.protocols.query.unmarshall.XmlElement;
import software.amazon.awssdk.protocols.query.unmarshall.XmlErrorUnmarshaller;
import software.amazon.awssdk.utils.builder.Buildable;

public class Aws_query_protocolTest {
    @Test
    void xmlElementBuilderPreservesChildrenAttributesAndDuplicateChildRules() {
        XmlElement firstItem = XmlElement.builder()
            .elementName("Item")
            .textContent("first")
            .build();
        XmlElement secondItem = XmlElement.builder()
            .elementName("Item")
            .textContent("second")
            .build();
        XmlElement marker = XmlElement.builder()
            .elementName("Marker")
            .textContent("done")
            .build();
        Map<String, String> attributes = Map.of("ns:attr", "value", ":plain", "plain-value");

        XmlElement root = XmlElement.builder()
            .elementName("Root")
            .attributes(attributes)
            .addChildElement(firstItem)
            .addChildElement(marker)
            .addChildElement(secondItem)
            .textContent("root text")
            .build();

        assertThat(root.elementName()).isEqualTo("Root");
        assertThat(root.textContent()).isEqualTo("root text");
        assertThat(root.children()).containsExactly(firstItem, marker, secondItem);
        assertThat(root.getFirstChild()).isSameAs(firstItem);
        assertThat(root.getElementsByName("Item")).containsExactly(firstItem, secondItem);
        assertThat(root.getElementByName("Marker")).isSameAs(marker);
        assertThat(root.getOptionalElementByName("Missing")).isEmpty();
        assertThat(root.attributes()).containsExactlyInAnyOrderEntriesOf(attributes);
        assertThat(root.getOptionalAttributeByName("ns:attr")).contains("value");

        assertThatThrownBy(() -> root.getElementByName("Item"))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("more than one element");
        assertThatThrownBy(() -> root.children().add(marker))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> root.attributes().put("new", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void xmlDomParserReadsNestedElementsAttributesTextAndEmptyInputs() {
        String xml = """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <QueryResponse xmlns:t=\"urn:test\" requestId=\"abc-123\">
                <Items>
                    <Item t:type=\"primary\">one</Item>
                    <Item>two<![CDATA[-suffix]]></Item>
                </Items>
                <Message>Hello &amp; goodbye</Message>
            </QueryResponse>
            """;

        XmlElement root = XmlDomParser.parse(new ByteArrayInputStream(xml.getBytes(UTF_8)));

        assertThat(root.elementName()).isEqualTo("QueryResponse");
        assertThat(root.getOptionalAttributeByName(":requestId")).contains("abc-123");
        assertThat(root.getElementByName("Message").textContent()).isEqualTo("Hello & goodbye");
        XmlElement items = root.getElementByName("Items");
        assertThat(items.getElementsByName("Item")).extracting(XmlElement::textContent)
            .containsExactly("one", "two-suffix");
        assertThat(items.getElementsByName("Item").get(0).getOptionalAttributeByName("t:type"))
            .contains("primary");

        assertThat(XmlDomParser.parse(new ByteArrayInputStream(new byte[0]))).isSameAs(XmlElement.empty());
        assertThat(XmlElement.empty().elementName()).isEqualTo("eof");
        assertThat(XmlElement.empty().children()).isEmpty();
        assertThatThrownBy(() -> XmlDomParser.parse(new ByteArrayInputStream("<broken".getBytes(UTF_8))))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("Could not parse XML response");
    }

    @Test
    void queryProtocolFactoryMarshallsSdkPojoIntoActionVersionAndPayloadParameters() {
        AwsQueryProtocolFactory factory = AwsQueryProtocolFactory.builder()
            .clientConfiguration(clientConfiguration("https://query.example.com/service"))
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .build();
        OperationInfo operationInfo = OperationInfo.builder()
            .requestUri("/")
            .httpMethod(SdkHttpMethod.POST)
            .operationIdentifier("DescribeThings")
            .apiVersion("2010-05-08")
            .hasPayloadMembers(true)
            .build();

        ProtocolMarshaller<SdkHttpFullRequest> marshaller = factory.createProtocolMarshaller(operationInfo);
        SdkHttpFullRequest request = marshaller.marshall(new SimpleQueryRequest("alpha beta", 7));

        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(request.protocol()).isEqualTo("https");
        assertThat(request.host()).isEqualTo("query.example.com");
        assertThat(request.encodedPath()).isEmpty();
        assertThat(request.firstMatchingRawQueryParameter("Action")).contains("DescribeThings");
        assertThat(request.firstMatchingRawQueryParameter("Version")).contains("2010-05-08");
        assertThat(request.firstMatchingRawQueryParameter("Name")).contains("alpha beta");
        assertThat(request.firstMatchingRawQueryParameter("Count")).contains("7");
        assertThat(request.encodedQueryParameters()).hasValueSatisfying(query ->
            assertThat(query).contains("Action=DescribeThings", "Version=2010-05-08", "Count=7"));
    }

    @Test
    void queryAndEc2ProtocolFactoriesMarshallCollectionPayloadMembersWithProtocolSpecificListNames() {
        AwsQueryProtocolFactory queryFactory = AwsQueryProtocolFactory.builder()
            .clientConfiguration(clientConfiguration("https://query.example.com/service"))
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .build();
        AwsEc2ProtocolFactory ec2Factory = AwsEc2ProtocolFactory.builder()
            .clientConfiguration(clientConfiguration("https://ec2.example.com/service"))
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .build();
        OperationInfo operationInfo = OperationInfo.builder()
            .requestUri("/")
            .httpMethod(SdkHttpMethod.POST)
            .operationIdentifier("DescribeCollections")
            .apiVersion("2010-05-08")
            .hasPayloadMembers(true)
            .build();
        Map<String, String> dimensions = new LinkedHashMap<>();
        dimensions.put("size", "large");
        dimensions.put("color", "blue");
        CollectionQueryRequest collectionRequest = new CollectionQueryRequest(List.of("red", "blue"), dimensions);

        SdkHttpFullRequest queryRequest = queryFactory.createProtocolMarshaller(operationInfo)
            .marshall(collectionRequest);
        SdkHttpFullRequest ec2Request = ec2Factory.createProtocolMarshaller(operationInfo)
            .marshall(collectionRequest);

        assertThat(queryRequest.firstMatchingRawQueryParameter("Tags.member.1")).contains("red");
        assertThat(queryRequest.firstMatchingRawQueryParameter("Tags.member.2")).contains("blue");
        assertThat(queryRequest.firstMatchingRawQueryParameter("Dimensions.entry.1.Key")).contains("size");
        assertThat(queryRequest.firstMatchingRawQueryParameter("Dimensions.entry.1.Value")).contains("large");
        assertThat(queryRequest.firstMatchingRawQueryParameter("Dimensions.entry.2.Key")).contains("color");
        assertThat(queryRequest.firstMatchingRawQueryParameter("Dimensions.entry.2.Value")).contains("blue");

        assertThat(ec2Request.firstMatchingRawQueryParameter("Tags.1")).contains("red");
        assertThat(ec2Request.firstMatchingRawQueryParameter("Tags.2")).contains("blue");
        assertThat(ec2Request.firstMatchingRawQueryParameter("Tags.member.1")).isEmpty();
    }

    @Test
    void queryResponseHandlerUnmarshallsCollectionPayloadMembers() throws Exception {
        AwsQueryProtocolFactory factory = AwsQueryProtocolFactory.builder()
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .build();
        HttpResponseHandler<CollectionQueryResponse> handler = factory.createResponseHandler(
            CollectionQueryResponse.Builder::new);
        SdkHttpFullResponse response = response(200, """
            <DescribeCollectionsResponse>
                <DescribeCollectionsResult>
                    <Tags>
                        <member>red</member>
                        <member>blue</member>
                    </Tags>
                    <Dimensions>
                        <entry>
                            <Key>size</Key>
                            <Value>large</Value>
                        </entry>
                        <entry>
                            <Key>color</Key>
                            <Value>blue</Value>
                        </entry>
                    </Dimensions>
                </DescribeCollectionsResult>
            </DescribeCollectionsResponse>
            """);

        CollectionQueryResponse unmarshalled = handler.handle(response, new ExecutionAttributes());

        assertThat(unmarshalled.tags()).containsExactly("red", "blue");
        assertThat(unmarshalled.dimensions()).containsEntry("size", "large")
            .containsEntry("color", "blue");
    }

    @Test
    void queryResponseHandlerUnwrapsResultElementAndResponseMetadata() throws Exception {
        AwsQueryProtocolFactory factory = AwsQueryProtocolFactory.builder()
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .build();
        HttpResponseHandler<SimpleQueryResponse> handler = factory.createResponseHandler(
            SimpleQueryResponse.Builder::new);
        SdkHttpFullResponse response = response(200, """
            <DescribeThingsResponse>
                <DescribeThingsResult>
                    <ResultName>returned value</ResultName>
                </DescribeThingsResult>
                <ResponseMetadata>
                    <RequestId>xml-response-request</RequestId>
                </ResponseMetadata>
            </DescribeThingsResponse>
            """);

        SimpleQueryResponse unmarshalled = handler.handle(response, new ExecutionAttributes());

        assertThat(unmarshalled.resultName()).isEqualTo("returned value");
        assertThat(unmarshalled.responseMetadata().requestId()).isEqualTo("xml-response-request");
    }

    @Test
    void queryAndEc2FactoriesExtractModeledErrorsWithProtocolSpecificRoots() throws Exception {
        AwsQueryProtocolFactory queryFactory = AwsQueryProtocolFactory.builder()
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .registerModeledException(modeledException("ModeledQueryError"))
            .build();
        AwsEc2ProtocolFactory ec2Factory = AwsEc2ProtocolFactory.builder()
            .defaultServiceExceptionSupplier(TestServiceException.Builder::new)
            .registerModeledException(modeledException("ModeledEc2Error"))
            .build();
        ExecutionAttributes attributes = new ExecutionAttributes()
            .putAttribute(SdkExecutionAttribute.SERVICE_NAME, "query-service")
            .putAttribute(SdkExecutionAttribute.TIME_OFFSET, 12);

        AwsServiceException queryException = queryFactory.createErrorResponseHandler().handle(
            response(400, """
                <ErrorResponse>
                    <Error>
                        <Code>ModeledQueryError</Code>
                        <Message>query failed</Message>
                    </Error>
                    <RequestId>query-request</RequestId>
                </ErrorResponse>
                """),
            attributes);
        AwsServiceException ec2Exception = ec2Factory.createErrorResponseHandler().handle(
            response(503, """
                <Response>
                    <Errors>
                        <Error>
                            <Code>ModeledEc2Error</Code>
                            <Message>ec2 failed</Message>
                        </Error>
                    </Errors>
                    <RequestID>ec2-request</RequestID>
                </Response>
                """),
            attributes);

        assertModeledException(queryException, "ModeledQueryError", "query failed", "query-request", 400);
        assertModeledException(ec2Exception, "ModeledEc2Error", "ec2 failed", "ec2-request", 503);
        assertThat(queryException.awsErrorDetails().serviceName()).isEqualTo("query-service");
    }

    @Test
    void publicErrorProtocolUnmarshallerSupportsCustomRootsAndInvalidXmlFallback() throws Exception {
        boolean[] customUnmarshallerInvoked = {false};
        XmlErrorUnmarshaller customUnmarshaller = new XmlErrorUnmarshaller() {
            @SuppressWarnings("unchecked")
            @Override
            public <TypeT extends SdkPojo> TypeT unmarshall(SdkPojo sdkPojo, XmlElement errorRoot,
                    SdkHttpFullResponse response) {
                customUnmarshallerInvoked[0] = true;
                TestServiceException.Builder builder = (TestServiceException.Builder) sdkPojo;
                return (TypeT) builder.message("custom:" + errorRoot.getElementByName("Message").textContent())
                    .build();
            }
        };
        AwsXmlErrorProtocolUnmarshaller unmarshaller = AwsXmlErrorProtocolUnmarshaller.builder()
            .defaultExceptionSupplier(TestServiceException.Builder::new)
            .exceptions(List.of(modeledException("CustomError")))
            .errorRootExtractor(root -> root.getOptionalElementByName("Problem"))
            .errorUnmarshaller(customUnmarshaller)
            .build();
        ExecutionAttributes attributes = new ExecutionAttributes()
            .putAttribute(SdkExecutionAttribute.SERVICE_NAME, "custom-service");

        AwsServiceException customException = unmarshaller.handle(response(418, """
            <Envelope>
                <Problem>
                    <Code>CustomError</Code>
                    <Message>teapot</Message>
                </Problem>
                <RequestId>custom-request</RequestId>
            </Envelope>
            """), attributes);
        AwsServiceException fallbackException = unmarshaller.handle(
            response(500, "<not-xml", Map.of("x-amzn-RequestId", List.of("header-request"))), attributes);

        assertThat(customUnmarshallerInvoked[0]).isTrue();
        assertThat(customException).isInstanceOf(TestServiceException.class);
        assertThat(customException.getMessage()).contains("teapot");
        assertThat(customException.requestId()).isEqualTo("custom-request");
        assertThat(customException.statusCode()).isEqualTo(418);
        assertThat(customException.awsErrorDetails().errorCode()).isEqualTo("CustomError");
        assertThat(customException.awsErrorDetails().rawResponse().asUtf8String()).contains("<Envelope>");

        assertThat(fallbackException).isInstanceOf(TestServiceException.class);
        assertThat(fallbackException.statusCode()).isEqualTo(500);
        assertThat(fallbackException.requestId()).isEqualTo("header-request");
        assertThat(fallbackException.awsErrorDetails().errorCode()).isNull();
        assertThat(fallbackException.awsErrorDetails().rawResponse().asUtf8String()).isEqualTo("<not-xml");
    }

    private static void assertModeledException(AwsServiceException exception, String code, String message,
            String requestId, int statusCode) {
        assertThat(exception).isInstanceOf(TestServiceException.class);
        assertThat(exception.getMessage()).contains(message);
        assertThat(exception.requestId()).isEqualTo(requestId);
        assertThat(exception.statusCode()).isEqualTo(statusCode);
        assertThat(exception.awsErrorDetails().errorCode()).isEqualTo(code);
        assertThat(exception.awsErrorDetails().errorMessage()).isEqualTo(message);
        assertThat(exception.awsErrorDetails().sdkHttpResponse().statusCode()).isEqualTo(statusCode);
    }

    private static SdkClientConfiguration clientConfiguration(String endpoint) {
        return SdkClientConfiguration.builder()
            .option(SdkClientOption.CLIENT_ENDPOINT_PROVIDER,
                ClientEndpointProvider.forEndpointOverride(URI.create(endpoint)))
            .build();
    }

    private static ExceptionMetadata modeledException(String errorCode) {
        return ExceptionMetadata.builder()
            .errorCode(errorCode)
            .exceptionBuilderSupplier(TestServiceException.Builder::new)
            .build();
    }

    private static SdkHttpFullResponse response(int statusCode, String body) {
        return response(statusCode, body, Map.of());
    }

    private static SdkHttpFullResponse response(int statusCode, String body, Map<String, List<String>> headers) {
        SdkHttpFullResponse.Builder builder = SdkHttpFullResponse.builder()
            .statusCode(statusCode)
            .content(AbortableInputStream.create(new ByteArrayInputStream(body.getBytes(UTF_8))));
        headers.forEach(builder::putHeader);
        return builder.build();
    }

    private static SdkField<String> stringPayloadField(String memberName) {
        return SdkField.<String>builder(MarshallingType.STRING)
            .memberName(memberName)
            .getter(value -> ((SimpleQueryRequest) value).name)
            .setter((target, value) -> ((SimpleQueryRequest) target).name = value)
            .traits(LocationTrait.builder()
                .location(MarshallLocation.PAYLOAD)
                .locationName(memberName)
                .unmarshallLocationName(memberName)
                .build())
            .build();
    }

    private static SdkField<Integer> integerPayloadField(String memberName) {
        return SdkField.<Integer>builder(MarshallingType.INTEGER)
            .memberName(memberName)
            .getter(value -> ((SimpleQueryRequest) value).count)
            .setter((target, value) -> ((SimpleQueryRequest) target).count = value)
            .traits(LocationTrait.builder()
                .location(MarshallLocation.PAYLOAD)
                .locationName(memberName)
                .unmarshallLocationName(memberName)
                .build())
            .build();
    }

    private static SdkField<String> responseStringPayloadField(String memberName) {
        return SdkField.<String>builder(MarshallingType.STRING)
            .memberName(memberName)
            .getter(value -> value instanceof SimpleQueryResponse.Builder
                ? ((SimpleQueryResponse.Builder) value).resultName
                : ((SimpleQueryResponse) value).resultName)
            .setter((target, value) -> ((SimpleQueryResponse.Builder) target).resultName = value)
            .traits(LocationTrait.builder()
                .location(MarshallLocation.PAYLOAD)
                .locationName(memberName)
                .unmarshallLocationName(memberName)
                .build())
            .build();
    }

    private static SdkField<List<?>> collectionRequestTagsPayloadField(String memberName) {
        return SdkField.<List<?>>builder(MarshallingType.LIST)
            .memberName(memberName)
            .getter(value -> ((CollectionQueryRequest) value).tags)
            .traits(collectionLocationTrait(memberName), stringListTrait(false))
            .build();
    }

    private static SdkField<Map<String, ?>> collectionRequestDimensionsPayloadField(String memberName) {
        return SdkField.<Map<String, ?>>builder(MarshallingType.MAP)
            .memberName(memberName)
            .getter(value -> ((CollectionQueryRequest) value).dimensions)
            .traits(collectionLocationTrait(memberName), stringMapTrait(false))
            .build();
    }

    private static SdkField<List<?>> collectionResponseTagsPayloadField(String memberName) {
        return SdkField.<List<?>>builder(MarshallingType.LIST)
            .memberName(memberName)
            .getter(value -> value instanceof CollectionQueryResponse.Builder
                ? ((CollectionQueryResponse.Builder) value).tags
                : ((CollectionQueryResponse) value).tags)
            .setter((target, value) -> ((CollectionQueryResponse.Builder) target).tags = stringList(value))
            .traits(collectionLocationTrait(memberName), stringListTrait(false))
            .build();
    }

    private static SdkField<Map<String, ?>> collectionResponseDimensionsPayloadField(String memberName) {
        return SdkField.<Map<String, ?>>builder(MarshallingType.MAP)
            .memberName(memberName)
            .getter(value -> value instanceof CollectionQueryResponse.Builder
                ? ((CollectionQueryResponse.Builder) value).dimensions
                : ((CollectionQueryResponse) value).dimensions)
            .setter((target, value) -> ((CollectionQueryResponse.Builder) target).dimensions = stringMap(value))
            .traits(collectionLocationTrait(memberName), stringMapTrait(false))
            .build();
    }

    private static LocationTrait collectionLocationTrait(String memberName) {
        return LocationTrait.builder()
            .location(MarshallLocation.PAYLOAD)
            .locationName(memberName)
            .unmarshallLocationName(memberName)
            .build();
    }

    private static ListTrait stringListTrait(boolean flattened) {
        return ListTrait.builder()
            .memberLocationName("member")
            .memberFieldInfo(stringCollectionValueField("member"))
            .isFlattened(flattened)
            .build();
    }

    private static MapTrait stringMapTrait(boolean flattened) {
        return MapTrait.builder()
            .keyLocationName("Key")
            .valueLocationName("Value")
            .valueFieldInfo(stringCollectionValueField("Value"))
            .isFlattened(flattened)
            .build();
    }

    private static SdkField<String> stringCollectionValueField(String memberName) {
        return SdkField.<String>builder(MarshallingType.STRING)
            .memberName(memberName)
            .getter(value -> (String) value)
            .traits(collectionLocationTrait(memberName))
            .build();
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(List<?> value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Map<String, ?> value) {
        return (Map<String, String>) value;
    }

    private static final class SimpleQueryRequest implements SdkPojo {
        private static final List<SdkField<?>> SDK_FIELDS = List.of(
            stringPayloadField("Name"),
            integerPayloadField("Count"));

        private String name;
        private Integer count;

        private SimpleQueryRequest(String name, Integer count) {
            this.name = name;
            this.count = count;
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }
    }

    private static final class CollectionQueryRequest implements SdkPojo {
        private static final List<SdkField<?>> SDK_FIELDS = List.of(
            collectionRequestTagsPayloadField("Tags"),
            collectionRequestDimensionsPayloadField("Dimensions"));

        private final List<?> tags;
        private final Map<String, ?> dimensions;

        private CollectionQueryRequest(List<?> tags, Map<String, ?> dimensions) {
            this.tags = tags;
            this.dimensions = dimensions;
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }
    }

    private static final class CollectionQueryResponse extends AwsResponse implements SdkPojo {
        private static final List<SdkField<?>> SDK_FIELDS = List.of(
            collectionResponseTagsPayloadField("Tags"),
            collectionResponseDimensionsPayloadField("Dimensions"));

        private final List<String> tags;
        private final Map<String, String> dimensions;

        private CollectionQueryResponse(Builder builder) {
            super(builder);
            this.tags = builder.tags;
            this.dimensions = builder.dimensions;
        }

        private List<String> tags() {
            return tags;
        }

        private Map<String, String> dimensions() {
            return dimensions;
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }

        private static final class Builder extends AwsResponse.BuilderImpl implements SdkPojo, Buildable {
            private List<String> tags;
            private Map<String, String> dimensions;

            private Builder() {
            }

            private Builder(CollectionQueryResponse response) {
                super(response);
                this.tags = response.tags;
                this.dimensions = response.dimensions;
            }

            @Override
            public CollectionQueryResponse build() {
                return new CollectionQueryResponse(this);
            }

            @Override
            public List<SdkField<?>> sdkFields() {
                return SDK_FIELDS;
            }
        }
    }

    private static final class SimpleQueryResponse extends AwsResponse implements SdkPojo {
        private static final List<SdkField<?>> SDK_FIELDS = List.of(responseStringPayloadField("ResultName"));

        private final String resultName;

        private SimpleQueryResponse(Builder builder) {
            super(builder);
            this.resultName = builder.resultName;
        }

        private String resultName() {
            return resultName;
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }

        private static final class Builder extends AwsResponse.BuilderImpl implements SdkPojo, Buildable {
            private String resultName;

            private Builder() {
            }

            private Builder(SimpleQueryResponse response) {
                super(response);
                this.resultName = response.resultName;
            }

            @Override
            public SimpleQueryResponse build() {
                return new SimpleQueryResponse(this);
            }

            @Override
            public List<SdkField<?>> sdkFields() {
                return SDK_FIELDS;
            }
        }
    }

    private static final class TestServiceException extends AwsServiceException implements SdkPojo {
        private static final List<SdkField<?>> SDK_FIELDS = List.of();

        private TestServiceException(Builder builder) {
            super(builder);
        }

        @Override
        public Builder toBuilder() {
            return new Builder(this);
        }

        @Override
        public List<SdkField<?>> sdkFields() {
            return SDK_FIELDS;
        }

        private static final class Builder extends AwsServiceException.BuilderImpl {
            private Builder() {
            }

            private Builder(TestServiceException exception) {
                super(exception);
            }

            @Override
            public TestServiceException build() {
                return new TestServiceException(this);
            }
        }
    }
}
