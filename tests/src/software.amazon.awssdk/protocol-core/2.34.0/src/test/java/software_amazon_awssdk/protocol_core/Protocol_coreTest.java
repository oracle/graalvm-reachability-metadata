/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.protocol_core;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.SdkField;
import software.amazon.awssdk.core.SdkPojo;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.protocol.MarshallLocation;
import software.amazon.awssdk.core.protocol.MarshallingType;
import software.amazon.awssdk.core.traits.LocationTrait;
import software.amazon.awssdk.core.traits.TimestampFormatTrait;
import software.amazon.awssdk.core.traits.Trait;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.protocols.core.AbstractMarshallingRegistry;
import software.amazon.awssdk.protocols.core.ExceptionMetadata;
import software.amazon.awssdk.protocols.core.InstantToString;
import software.amazon.awssdk.protocols.core.NumberToInstant;
import software.amazon.awssdk.protocols.core.OperationInfo;
import software.amazon.awssdk.protocols.core.OperationMetadataAttribute;
import software.amazon.awssdk.protocols.core.PathMarshaller;
import software.amazon.awssdk.protocols.core.ProtocolUtils;
import software.amazon.awssdk.protocols.core.StringToInstant;
import software.amazon.awssdk.protocols.core.StringToValueConverter;
import software.amazon.awssdk.protocols.core.ValueToStringConverter;

public class Protocol_coreTest {
    private static final Instant SAMPLE_INSTANT = Instant.parse("2020-01-02T03:04:05Z");

    @Test
    void simpleConvertersHandleCommonScalarAndBinaryTypes() {
        final SdkBytes bytes = SdkBytes.fromUtf8String("protocol core");
        final String encodedBytes = ValueToStringConverter.FROM_SDK_BYTES.convert(bytes, null);

        assertThat(ValueToStringConverter.FROM_STRING.convert("value", null)).isEqualTo("value");
        assertThat(ValueToStringConverter.FROM_INTEGER.convert(42, null)).isEqualTo("42");
        assertThat(ValueToStringConverter.FROM_LONG.convert(123456789L, null)).isEqualTo("123456789");
        assertThat(ValueToStringConverter.FROM_SHORT.convert((short) 7, null)).isEqualTo("7");
        assertThat(ValueToStringConverter.FROM_BYTE.convert((byte) 3, null)).isEqualTo("3");
        assertThat(ValueToStringConverter.FROM_FLOAT.convert(1.25f, null)).isEqualTo("1.25");
        assertThat(ValueToStringConverter.FROM_DOUBLE.convert(2.5d, null)).isEqualTo("2.5");
        assertThat(ValueToStringConverter.FROM_BIG_DECIMAL.convert(new BigDecimal("12.340"), null)).isEqualTo("12.340");
        assertThat(ValueToStringConverter.FROM_BOOLEAN.convert(true, null)).isEqualTo("true");

        assertThat(StringToValueConverter.TO_STRING.convert("value", null)).isEqualTo("value");
        assertThat(StringToValueConverter.TO_INTEGER.convert("42", null)).isEqualTo(42);
        assertThat(StringToValueConverter.TO_LONG.convert("123456789", null)).isEqualTo(123456789L);
        assertThat(StringToValueConverter.TO_SHORT.convert("7", null)).isEqualTo((short) 7);
        assertThat(StringToValueConverter.TO_BYTE.convert("3", null)).isEqualTo((byte) 3);
        assertThat(StringToValueConverter.TO_FLOAT.convert("1.25", null)).isEqualTo(1.25f);
        assertThat(StringToValueConverter.TO_DOUBLE.convert("2.5", null)).isEqualTo(2.5d);
        assertThat(StringToValueConverter.TO_BIG_DECIMAL.convert("12.340", null)).isEqualByComparingTo("12.340");
        assertThat(StringToValueConverter.TO_BOOLEAN.convert("true", null)).isTrue();
        assertThat(StringToValueConverter.TO_SDK_BYTES.convert(encodedBytes, null).asUtf8String())
            .isEqualTo("protocol core");
    }

    @Test
    void timestampConvertersUseDefaultFormatsAndFieldTraits() {
        final Map<MarshallLocation, TimestampFormatTrait.Format> defaults = new HashMap<>();
        defaults.put(MarshallLocation.HEADER, TimestampFormatTrait.Format.RFC_822);
        defaults.put(MarshallLocation.PAYLOAD, TimestampFormatTrait.Format.UNIX_TIMESTAMP);

        final SdkField<Instant> headerField = instantField(MarshallLocation.HEADER);
        final SdkField<Instant> isoPayloadField = instantField(MarshallLocation.PAYLOAD,
                                                               TimestampFormatTrait.Format.ISO_8601);
        final SdkField<Instant> unixPayloadField = instantField(MarshallLocation.PAYLOAD);
        final SdkField<Instant> millisPayloadField = instantField(MarshallLocation.PAYLOAD,
                                                                  TimestampFormatTrait.Format.UNIX_TIMESTAMP_MILLIS);
        final InstantToString instantToString = InstantToString.create(defaults);
        final StringToInstant stringToInstant = StringToInstant.create(defaults);
        final NumberToInstant numberToInstant = NumberToInstant.create(defaults);

        final String rfc822 = instantToString.convert(SAMPLE_INSTANT, headerField);
        final String iso8601 = instantToString.convert(SAMPLE_INSTANT, isoPayloadField);
        final String unixTimestamp = instantToString.convert(SAMPLE_INSTANT, unixPayloadField);

        assertThat(stringToInstant.convert(rfc822, headerField)).isEqualTo(SAMPLE_INSTANT);
        assertThat(stringToInstant.convert(iso8601, isoPayloadField)).isEqualTo(SAMPLE_INSTANT);
        assertThat(stringToInstant.convert(unixTimestamp, unixPayloadField)).isEqualTo(SAMPLE_INSTANT);
        assertThat(stringToInstant.convert("1577934245123", millisPayloadField))
            .isEqualTo(Instant.parse("2020-01-02T03:04:05.123Z"));
        assertThat(numberToInstant.convert(1577934245, unixPayloadField)).isEqualTo(SAMPLE_INSTANT);
        assertThat(numberToInstant.convert(1577934245123L, millisPayloadField))
            .isEqualTo(Instant.parse("2020-01-02T03:04:05.123Z"));
        assertThat(instantToString.convert(null, headerField)).isNull();
        assertThat(stringToInstant.convert(null, headerField)).isNull();
        assertThat(numberToInstant.convert(null, headerField)).isNull();
    }

    @Test
    void numberToInstantConvertsFractionalUnixTimestampsToEpochMilliseconds() {
        final Map<MarshallLocation, TimestampFormatTrait.Format> defaults = Map.of(
            MarshallLocation.PAYLOAD, TimestampFormatTrait.Format.UNIX_TIMESTAMP);
        final SdkField<Instant> payloadField = instantField(MarshallLocation.PAYLOAD);
        final NumberToInstant numberToInstant = NumberToInstant.create(defaults);

        assertThat(numberToInstant.convert(1099510880.773d, payloadField))
            .isEqualTo(Instant.ofEpochMilli(1099510880773L));
        assertThat(numberToInstant.convert(1099510880.771d, payloadField))
            .isEqualTo(Instant.ofEpochMilli(1099510880771L));
    }

    @Test
    void timestampConvertersReportUnsupportedLocationsAndInvalidNumericValues() {
        final Map<MarshallLocation, TimestampFormatTrait.Format> defaults = Map.of(
            MarshallLocation.PAYLOAD, TimestampFormatTrait.Format.UNIX_TIMESTAMP);
        final SdkField<Instant> queryField = instantField(MarshallLocation.QUERY_PARAM);
        final SdkField<Instant> payloadField = instantField(MarshallLocation.PAYLOAD);

        assertThatThrownBy(() -> InstantToString.create(defaults).convert(SAMPLE_INSTANT, queryField))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("No default timestamp marshaller found");
        assertThatThrownBy(() -> StringToInstant.create(defaults).convert("not-a-number", payloadField))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("Unable to parse date");
        assertThatThrownBy(() -> NumberToInstant.create(defaults).convert(1, queryField))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("Timestamps are not supported");
    }

    @Test
    void pathMarshallersEncodeLabelsAccordingToGreediness() {
        assertThat(PathMarshaller.NON_GREEDY.marshall("/bucket/{Key}", "Key", "a/b c+"))
            .isEqualTo("/bucket/a%2Fb%20c%2B");
        assertThat(PathMarshaller.GREEDY.marshall("/files/{Proxy+}", "Proxy", "/a b/c+"))
            .isEqualTo("/files/a%20b/c%2B");
        assertThat(PathMarshaller.GREEDY_WITH_SLASHES.marshall("/files/{Proxy+}", "Proxy", "/a b/c+"))
            .isEqualTo("/files//a%20b/c%2B");

        assertThatThrownBy(() -> PathMarshaller.NON_GREEDY.marshall("/bucket/{Key}", "Key", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Key cannot be empty");
    }

    @Test
    void protocolUtilsCombinesEndpointPathOperationPathAndStaticQueryParameters() {
        final String requestUri = "/v1/resource?Action=ListThings&Flag;Version=2012-10-17";
        final OperationInfo operationInfo = OperationInfo.builder()
                                                         .requestUri(requestUri)
                                                         .httpMethod(SdkHttpMethod.POST)
                                                         .build();

        final SdkHttpFullRequest request = ProtocolUtils
            .createSdkHttpRequest(operationInfo, URI.create("https://service.example.com/root?Endpoint=Configured"))
            .build();

        assertThat(request.method()).isEqualTo(SdkHttpMethod.POST);
        assertThat(request.protocol()).isEqualTo("https");
        assertThat(request.host()).isEqualTo("service.example.com");
        assertThat(request.encodedPath()).isEqualTo("/root/v1/resource");
        assertThat(request.rawQueryParameters())
            .containsEntry("Endpoint", singletonList("Configured"))
            .containsEntry("Action", singletonList("ListThings"))
            .containsEntry("Version", singletonList("2012-10-17"))
            .containsEntry("Flag", singletonList(null));
    }

    @Test
    void operationInfoStoresOperationFlagsAndTypedAdditionalMetadata() {
        final OperationMetadataAttribute<String> stringAttribute = new OperationMetadataAttribute<>(String.class);
        final OperationMetadataAttribute<Map<String, String>> mapAttribute = OperationMetadataAttribute
            .forUnsafe(Map.class);
        final Map<String, String> metadata = Map.of("protocol", "aws-json");

        final OperationInfo operationInfo = OperationInfo.builder()
                                                         .requestUri("/things/{ThingId}")
                                                         .httpMethod(SdkHttpMethod.PUT)
                                                         .operationIdentifier("PutThing")
                                                         .apiVersion("2020-01-02")
                                                         .hasExplicitPayloadMember(true)
                                                         .hasPayloadMembers(true)
                                                         .hasImplicitPayloadMembers(true)
                                                         .hasStreamingInput(true)
                                                         .hasEventStreamingInput(true)
                                                         .hasEvent(true)
                                                         .putAdditionalMetadata(stringAttribute, "metadata-value")
                                                         .putAdditionalMetadata(mapAttribute, metadata)
                                                         .build();

        assertThat(operationInfo.requestUri()).isEqualTo("/things/{ThingId}");
        assertThat(operationInfo.httpMethod()).isEqualTo(SdkHttpMethod.PUT);
        assertThat(operationInfo.operationIdentifier()).isEqualTo("PutThing");
        assertThat(operationInfo.apiVersion()).isEqualTo("2020-01-02");
        assertThat(operationInfo.hasExplicitPayloadMember()).isTrue();
        assertThat(operationInfo.hasPayloadMembers()).isTrue();
        assertThat(operationInfo.hasImplicitPayloadMembers()).isTrue();
        assertThat(operationInfo.hasStreamingInput()).isTrue();
        assertThat(operationInfo.hasEventStreamingInput()).isTrue();
        assertThat(operationInfo.hasEvent()).isTrue();
        assertThat(operationInfo.addtionalMetadata(stringAttribute)).isEqualTo("metadata-value");
        assertThat(operationInfo.addtionalMetadata(mapAttribute)).isSameAs(metadata);
    }

    @Test
    void exceptionMetadataKeepsErrorCodeStatusAndBuilderSupplier() {
        final EmptySdkPojo pojo = new EmptySdkPojo();
        final ExceptionMetadata metadata = ExceptionMetadata.builder()
                                                            .errorCode("ModeledError")
                                                            .httpStatusCode(429)
                                                            .exceptionBuilderSupplier(() -> pojo)
                                                            .build();

        assertThat(metadata.errorCode()).isEqualTo("ModeledError");
        assertThat(metadata.httpStatusCode()).isEqualTo(429);
        assertThat(metadata.exceptionBuilderSupplier().get()).isSameAs(pojo);
    }

    @Test
    void marshallingRegistryResolvesByLocationKnownTypeCustomTypeNullAndSdkPojo() {
        final EmptySdkPojo pojo = new EmptySdkPojo();
        final TestMarshallingRegistry registry = TestMarshallingRegistry.builder()
                                                                        .registerMarshaller(MarshallLocation.PAYLOAD,
                                                                                            MarshallingType.STRING,
                                                                                            "payload-string")
                                                                        .registerMarshaller(
                                                                            MarshallLocation.HEADER,
                                                                            MarshallingType.newType(Number.class),
                                                                            "header-number")
                                                                        .registerMarshaller(MarshallLocation.PAYLOAD,
                                                                                            MarshallingType.NULL,
                                                                                            "payload-null")
                                                                        .registerMarshaller(MarshallLocation.PAYLOAD,
                                                                                            MarshallingType.SDK_POJO,
                                                                                            "payload-pojo")
                                                                        .build();

        assertThat(registry.marshallerFor(MarshallLocation.PAYLOAD, "hello")).isEqualTo("payload-string");
        assertThat(registry.marshallerFor(MarshallLocation.HEADER, BigInteger.TEN)).isEqualTo("header-number");
        assertThat(registry.marshallerFor(MarshallLocation.PAYLOAD, null)).isEqualTo("payload-null");
        assertThat(registry.marshallerFor(MarshallLocation.PAYLOAD, pojo)).isEqualTo("payload-pojo");
        assertThat(registry.marshallerFor(MarshallLocation.QUERY_PARAM, "hello")).isNull();
        assertThatThrownBy(() -> registry.marshallerFor(MarshallLocation.PAYLOAD, new Object()))
            .isInstanceOf(SdkClientException.class)
            .hasMessageContaining("MarshallingType not found");
    }

    private static SdkField<Instant> instantField(MarshallLocation location) {
        return field(MarshallingType.INSTANT, location, null);
    }

    private static SdkField<Instant> instantField(MarshallLocation location, TimestampFormatTrait.Format format) {
        return field(MarshallingType.INSTANT, location, format);
    }

    private static <T> SdkField<T> field(MarshallingType<T> type,
                                         MarshallLocation location,
                                         TimestampFormatTrait.Format format) {
        final LocationTrait locationTrait = LocationTrait.builder()
                                                         .location(location)
                                                         .locationName("Value")
                                                         .build();
        final Trait[] traits = format == null
                               ? new Trait[] {locationTrait}
                               : new Trait[] {locationTrait, TimestampFormatTrait.create(format)};
        return SdkField.builder(type)
                       .memberName("Value")
                       .traits(traits)
                       .build();
    }

    private static final class EmptySdkPojo implements SdkPojo {
        @Override
        public List<SdkField<?>> sdkFields() {
            return emptyList();
        }
    }

    private static final class TestMarshallingRegistry extends AbstractMarshallingRegistry {
        private TestMarshallingRegistry(Builder builder) {
            super(builder);
        }

        private static Builder builder() {
            return new Builder();
        }

        private Object marshallerFor(MarshallLocation location, Object value) {
            return get(location, toMarshallingType(value));
        }

        private static final class Builder extends AbstractMarshallingRegistry.Builder {
            private <T> Builder registerMarshaller(MarshallLocation location,
                                                   MarshallingType<T> marshallingType,
                                                   Object marshaller) {
                register(location, marshallingType, marshaller);
                return this;
            }

            private TestMarshallingRegistry build() {
                return new TestMarshallingRegistry(this);
            }
        }
    }
}
