/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.google.protobuf.util;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonSyntaxException;
import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.FloatValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.ListValue;
import com.google.protobuf.Message;
import com.google.protobuf.NullValue;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.UInt32Value;
import com.google.protobuf.UInt64Value;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat.TypeRegistry;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestAllTypes;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestAllTypes.AliasedEnum;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestAllTypes.NestedEnum;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestAllTypes.NestedMessage;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestAny;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestCustomJsonName;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestDuration;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestFieldMask;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestMap;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestOneof;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestRecursive;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestStruct;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestTimestamp;
import com_google_protobuf.protobuf_java_util.JsonTestProto.TestWrappers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

@SuppressWarnings({"UnnecessaryUnicodeEscape", "SameParameterValue", "UnpredictableBigDecimalConstructorCall"})
public class JsonFormatTest {
    private static Locale originalLocale;

    @BeforeClass
    public static void setLocale() {
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("hi-IN"));
    }

    @AfterClass
    public static void resetLocale() {
        Locale.setDefault(originalLocale);
    }

    private void setAllFields(TestAllTypes.Builder builder) {
        builder.setOptionalInt32(1234);
        builder.setOptionalInt64(1234567890123456789L);
        builder.setOptionalUint32(5678);
        builder.setOptionalUint64(2345678901234567890L);
        builder.setOptionalSint32(9012);
        builder.setOptionalSint64(3456789012345678901L);
        builder.setOptionalFixed32(3456);
        builder.setOptionalFixed64(4567890123456789012L);
        builder.setOptionalSfixed32(7890);
        builder.setOptionalSfixed64(5678901234567890123L);
        builder.setOptionalFloat(1.5f);
        builder.setOptionalDouble(1.25);
        builder.setOptionalBool(true);
        builder.setOptionalString("Hello world!");
        builder.setOptionalBytes(ByteString.copyFrom(new byte[]{0, 1, 2}));
        builder.setOptionalNestedEnum(NestedEnum.BAR);
        builder.getOptionalNestedMessageBuilder().setValue(100);
        builder.addRepeatedInt32(1234);
        builder.addRepeatedInt64(1234567890123456789L);
        builder.addRepeatedUint32(5678);
        builder.addRepeatedUint64(2345678901234567890L);
        builder.addRepeatedSint32(9012);
        builder.addRepeatedSint64(3456789012345678901L);
        builder.addRepeatedFixed32(3456);
        builder.addRepeatedFixed64(4567890123456789012L);
        builder.addRepeatedSfixed32(7890);
        builder.addRepeatedSfixed64(5678901234567890123L);
        builder.addRepeatedFloat(1.5f);
        builder.addRepeatedDouble(1.25);
        builder.addRepeatedBool(true);
        builder.addRepeatedString("Hello world!");
        builder.addRepeatedBytes(ByteString.copyFrom(new byte[]{0, 1, 2}));
        builder.addRepeatedNestedEnum(NestedEnum.BAR);
        builder.addRepeatedNestedMessageBuilder().setValue(100);
        builder.addRepeatedInt32(234);
        builder.addRepeatedInt64(234567890123456789L);
        builder.addRepeatedUint32(678);
        builder.addRepeatedUint64(345678901234567890L);
        builder.addRepeatedSint32(10);
        builder.addRepeatedSint64(456789012345678901L);
        builder.addRepeatedFixed32(456);
        builder.addRepeatedFixed64(567890123456789012L);
        builder.addRepeatedSfixed32(890);
        builder.addRepeatedSfixed64(678901234567890123L);
        builder.addRepeatedFloat(11.5f);
        builder.addRepeatedDouble(11.25);
        builder.addRepeatedBool(true);
        builder.addRepeatedString("ello world!");
        builder.addRepeatedBytes(ByteString.copyFrom(new byte[]{1, 2}));
        builder.addRepeatedNestedEnum(NestedEnum.BAZ);
        builder.addRepeatedNestedMessageBuilder().setValue(200);
    }

    private void assertRoundTripEquals(Message message) throws Exception {
        assertRoundTripEquals(message, TypeRegistry.getEmptyTypeRegistry());
    }

    private void assertRoundTripEquals(Message message, TypeRegistry registry) throws Exception {
        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry);
        JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
        Message.Builder builder = message.newBuilderForType();
        parser.merge(printer.print(message), builder);
        Message parsedMessage = builder.build();
        assertThat(parsedMessage.toString()).isEqualTo(message.toString());
    }

    private void assertRoundTripEquals(Message message, com.google.protobuf.TypeRegistry registry) throws Exception {
        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry);
        JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
        Message.Builder builder = message.newBuilderForType();
        parser.merge(printer.print(message), builder);
        Message parsedMessage = builder.build();
        assertThat(parsedMessage.toString()).isEqualTo(message.toString());
    }

    private String toJsonString(Message message) throws IOException {
        return JsonFormat.printer().print(message);
    }

    private String toCompactJsonString(Message message) throws IOException {
        return JsonFormat.printer().omittingInsignificantWhitespace().print(message);
    }

    private String toSortedJsonString(Message message) throws IOException {
        return JsonFormat.printer().sortingMapKeys().print(message);
    }

    private void mergeFromJson(String json, Message.Builder builder) throws IOException {
        JsonFormat.parser().merge(json, builder);
    }

    private void mergeFromJsonIgnoringUnknownFields(String json, Message.Builder builder) throws IOException {
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    }

    @Test
    public void testAllFields() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        setAllFields(builder);
        TestAllTypes message = builder.build();
        assertThat(toJsonString(message)).isEqualTo(
                """
                        {
                          "optionalInt32": 1234,
                          "optionalInt64": "1234567890123456789",
                          "optionalUint32": 5678,
                          "optionalUint64": "2345678901234567890",
                          "optionalSint32": 9012,
                          "optionalSint64": "3456789012345678901",
                          "optionalFixed32": 3456,
                          "optionalFixed64": "4567890123456789012",
                          "optionalSfixed32": 7890,
                          "optionalSfixed64": "5678901234567890123",
                          "optionalFloat": 1.5,
                          "optionalDouble": 1.25,
                          "optionalBool": true,
                          "optionalString": "Hello world!",
                          "optionalBytes": "AAEC",
                          "optionalNestedMessage": {
                            "value": 100
                          },
                          "optionalNestedEnum": "BAR",
                          "repeatedInt32": [1234, 234],
                          "repeatedInt64": ["1234567890123456789", "234567890123456789"],
                          "repeatedUint32": [5678, 678],
                          "repeatedUint64": ["2345678901234567890", "345678901234567890"],
                          "repeatedSint32": [9012, 10],
                          "repeatedSint64": ["3456789012345678901", "456789012345678901"],
                          "repeatedFixed32": [3456, 456],
                          "repeatedFixed64": ["4567890123456789012", "567890123456789012"],
                          "repeatedSfixed32": [7890, 890],
                          "repeatedSfixed64": ["5678901234567890123", "678901234567890123"],
                          "repeatedFloat": [1.5, 11.5],
                          "repeatedDouble": [1.25, 11.25],
                          "repeatedBool": [true, true],
                          "repeatedString": ["Hello world!", "ello world!"],
                          "repeatedBytes": ["AAEC", "AQI="],
                          "repeatedNestedMessage": [{
                            "value": 100
                          }, {
                            "value": 200
                          }],
                          "repeatedNestedEnum": ["BAR", "BAZ"]
                        }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testUnknownEnumValues() throws Exception {
        TestAllTypes message =
                TestAllTypes.newBuilder()
                        .setOptionalNestedEnumValue(12345)
                        .addRepeatedNestedEnumValue(12345)
                        .addRepeatedNestedEnumValue(0)
                        .build();
        assertThat(toJsonString(message)).isEqualTo(
                """
                        {
                          "optionalNestedEnum": 12345,
                          "repeatedNestedEnum": [12345, "FOO"]
                        }""");
        assertRoundTripEquals(message);
        TestMap.Builder mapBuilder = TestMap.newBuilder();
        mapBuilder.putInt32ToEnumMapValue(1, 0);
        mapBuilder.putInt32ToEnumMapValue(2, 12345);
        TestMap mapMessage = mapBuilder.build();
        assertThat(toJsonString(mapMessage)).isEqualTo(
                """
                        {
                          "int32ToEnumMap": {
                            "1": "FOO",
                            "2": 12345
                          }
                        }""");
        assertRoundTripEquals(mapMessage);
    }

    @Test
    public void testSpecialFloatValues() throws Exception {
        TestAllTypes message =
                TestAllTypes.newBuilder()
                        .addRepeatedFloat(Float.NaN)
                        .addRepeatedFloat(Float.POSITIVE_INFINITY)
                        .addRepeatedFloat(Float.NEGATIVE_INFINITY)
                        .addRepeatedDouble(Double.NaN)
                        .addRepeatedDouble(Double.POSITIVE_INFINITY)
                        .addRepeatedDouble(Double.NEGATIVE_INFINITY)
                        .build();
        assertThat(toJsonString(message))
                .isEqualTo(
                        """
                                {
                                  "repeatedFloat": ["NaN", "Infinity", "-Infinity"],
                                  "repeatedDouble": ["NaN", "Infinity", "-Infinity"]
                                }""");

        assertRoundTripEquals(message);
    }

    @Test
    public void testParserAcceptStringForNumericField() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        mergeFromJson(
                """
                        {
                          "optionalInt32": "1234",
                          "optionalUint32": "5678",
                          "optionalSint32": "9012",
                          "optionalFixed32": "3456",
                          "optionalSfixed32": "7890",
                          "optionalFloat": "1.5",
                          "optionalDouble": "1.25",
                          "optionalBool": "true"
                        }""",
                builder);
        TestAllTypes message = builder.build();
        assertThat(message.getOptionalInt32()).isEqualTo(1234);
        assertThat(message.getOptionalUint32()).isEqualTo(5678);
        assertThat(message.getOptionalSint32()).isEqualTo(9012);
        assertThat(message.getOptionalFixed32()).isEqualTo(3456);
        assertThat(message.getOptionalSfixed32()).isEqualTo(7890);
        assertThat(message.getOptionalFloat()).isEqualTo(1.5f);
        assertThat(message.getOptionalDouble()).isEqualTo(1.25);
        assertThat(message.getOptionalBool()).isTrue();
    }

    @Test
    public void testParserAcceptFloatingPointValueForIntegerField() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        mergeFromJson(
                """
                        {
                          "repeatedInt32": [1.000, 1e5, "1.000", "1e5"],
                          "repeatedUint32": [1.000, 1e5, "1.000", "1e5"],
                          "repeatedInt64": [1.000, 1e5, "1.000", "1e5"],
                          "repeatedUint64": [1.000, 1e5, "1.000", "1e5"]
                        }""",
                builder);
        int[] expectedValues = new int[]{1, 100000, 1, 100000};
        assertThat(builder.getRepeatedInt32Count()).isEqualTo(4);
        assertThat(builder.getRepeatedUint32Count()).isEqualTo(4);
        assertThat(builder.getRepeatedInt64Count()).isEqualTo(4);
        assertThat(builder.getRepeatedUint64Count()).isEqualTo(4);
        for (int i = 0; i < 4; ++i) {
            assertThat(builder.getRepeatedInt32(i)).isEqualTo(expectedValues[i]);
            assertThat(builder.getRepeatedUint32(i)).isEqualTo(expectedValues[i]);
            assertThat(builder.getRepeatedInt64(i)).isEqualTo(expectedValues[i]);
            assertThat(builder.getRepeatedUint64(i)).isEqualTo(expectedValues[i]);
        }
    }

    @Test
    public void testRejectTooLargeFloat() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        double tooLarge = 2.0 * Float.MAX_VALUE;
        try {
            mergeFromJson("{\"" + "optionalFloat" + "\":" + tooLarge + "}", builder);
            assertWithMessage("InvalidProtocolBufferException expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo("Out of range float value: " + tooLarge);
        }
    }

    @Test
    public void testRejectMalformedFloat() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"optionalFloat\":3.5aa}", builder);
            assertWithMessage("InvalidProtocolBufferException expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testRejectFractionalInt64() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + "optionalInt64" + "\":" + "1.5" + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo("Not an int64 value: 1.5");
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testRejectFractionalInt32() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + "optionalInt32" + "\":" + "1.5" + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo("Not an int32 value: 1.5");
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testRejectFractionalUnsignedInt32() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + "optionalUint32" + "\":" + "1.5" + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo("Not an uint32 value: 1.5");
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testRejectFractionalUnsignedInt64() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + "optionalUint64" + "\":" + "1.5" + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo("Not an uint64 value: 1.5");
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    private void assertRejects(String name, String value) throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + name + "\":" + value + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().contains(value);
        }
        try {
            mergeFromJson("{\"" + name + "\":\"" + value + "\"}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().contains(value);
        }
    }

    private void assertAccepts(String name, String value) throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        mergeFromJson("{\"" + name + "\":" + value + "}", builder);
        builder.clear();
        mergeFromJson("{\"" + name + "\":\"" + value + "\"}", builder);
    }

    @Test
    public void testParserRejectOutOfRangeNumericValues() throws Exception {
        assertAccepts("optionalInt32", String.valueOf(Integer.MAX_VALUE));
        assertAccepts("optionalInt32", String.valueOf(Integer.MIN_VALUE));
        assertRejects("optionalInt32", String.valueOf(Integer.MAX_VALUE + 1L));
        assertRejects("optionalInt32", String.valueOf(Integer.MIN_VALUE - 1L));
        assertAccepts("optionalUint32", String.valueOf(Integer.MAX_VALUE + 1L));
        assertRejects("optionalUint32", "123456789012345");
        assertRejects("optionalUint32", "-1");
        BigInteger one = BigInteger.ONE;
        BigInteger maxLong = new BigInteger(String.valueOf(Long.MAX_VALUE));
        BigInteger minLong = new BigInteger(String.valueOf(Long.MIN_VALUE));
        assertAccepts("optionalInt64", maxLong.toString());
        assertAccepts("optionalInt64", minLong.toString());
        assertRejects("optionalInt64", maxLong.add(one).toString());
        assertRejects("optionalInt64", minLong.subtract(one).toString());
        assertAccepts("optionalUint64", maxLong.add(one).toString());
        assertRejects("optionalUint64", "1234567890123456789012345");
        assertRejects("optionalUint64", "-1");
        assertAccepts("optionalBool", "true");
        assertRejects("optionalBool", "1");
        assertRejects("optionalBool", "0");
        assertAccepts("optionalFloat", String.valueOf(Float.MAX_VALUE));
        assertAccepts("optionalFloat", String.valueOf(-Float.MAX_VALUE));
        assertRejects("optionalFloat", String.valueOf(Double.MAX_VALUE));
        assertRejects("optionalFloat", String.valueOf(-Double.MAX_VALUE));
        BigDecimal moreThanOne = new BigDecimal("1.000001");
        BigDecimal maxDouble = new BigDecimal(Double.MAX_VALUE);
        BigDecimal minDouble = new BigDecimal(-Double.MAX_VALUE);
        assertAccepts("optionalDouble", maxDouble.toString());
        assertAccepts("optionalDouble", minDouble.toString());
        assertRejects("optionalDouble", maxDouble.multiply(moreThanOne).toString());
        assertRejects("optionalDouble", minDouble.multiply(moreThanOne).toString());
    }

    @Test
    public void testParserAcceptNull() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        mergeFromJson(
                """
                        {
                          "optionalInt32": null,
                          "optionalInt64": null,
                          "optionalUint32": null,
                          "optionalUint64": null,
                          "optionalSint32": null,
                          "optionalSint64": null,
                          "optionalFixed32": null,
                          "optionalFixed64": null,
                          "optionalSfixed32": null,
                          "optionalSfixed64": null,
                          "optionalFloat": null,
                          "optionalDouble": null,
                          "optionalBool": null,
                          "optionalString": null,
                          "optionalBytes": null,
                          "optionalNestedMessage": null,
                          "optionalNestedEnum": null,
                          "repeatedInt32": null,
                          "repeatedInt64": null,
                          "repeatedUint32": null,
                          "repeatedUint64": null,
                          "repeatedSint32": null,
                          "repeatedSint64": null,
                          "repeatedFixed32": null,
                          "repeatedFixed64": null,
                          "repeatedSfixed32": null,
                          "repeatedSfixed64": null,
                          "repeatedFloat": null,
                          "repeatedDouble": null,
                          "repeatedBool": null,
                          "repeatedString": null,
                          "repeatedBytes": null,
                          "repeatedNestedMessage": null,
                          "repeatedNestedEnum": null
                        }""",
                builder);
        TestAllTypes message = builder.build();
        assertThat(message).isEqualTo(TestAllTypes.getDefaultInstance());
        try {
            builder = TestAllTypes.newBuilder();
            mergeFromJson("""
                    {
                      "repeatedInt32": [null, null],
                    }""", builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
        try {
            builder = TestAllTypes.newBuilder();
            mergeFromJson("""
                    {
                      "repeatedNestedMessage": [null, null],
                    }""", builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testNullInOneof() throws Exception {
        TestOneof.Builder builder = TestOneof.newBuilder();
        mergeFromJson("""
                {
                  "oneofNullValue": null\s
                }""", builder);
        TestOneof message = builder.build();
        assertThat(message.getOneofFieldCase()).isEqualTo(TestOneof.OneofFieldCase.ONEOF_NULL_VALUE);
        assertThat(message.getOneofNullValue()).isEqualTo(NullValue.NULL_VALUE);
    }

    @Test
    public void testNullFirstInDuplicateOneof() throws Exception {
        TestOneof.Builder builder = TestOneof.newBuilder();
        mergeFromJson("{\"oneofNestedMessage\": null, \"oneofInt32\": 1}", builder);
        TestOneof message = builder.build();
        assertThat(message.getOneofInt32()).isEqualTo(1);
    }

    @Test
    public void testNullLastInDuplicateOneof() throws Exception {
        TestOneof.Builder builder = TestOneof.newBuilder();
        mergeFromJson("{\"oneofInt32\": 1, \"oneofNestedMessage\": null}", builder);
        TestOneof message = builder.build();
        assertThat(message.getOneofInt32()).isEqualTo(1);
    }

    @Test
    public void testParserRejectDuplicatedFields() throws Exception {
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            mergeFromJson(
                    """
                            {
                              "optionalNestedMessage": {},
                              "optional_nested_message": {}
                            }""",
                    builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }

        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            mergeFromJson(
                    """
                            {
                              "repeatedInt32": [1, 2],
                              "repeated_int32": [5, 6]
                            }""",
                    builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
        try {
            TestOneof.Builder builder = TestOneof.newBuilder();
            mergeFromJson("""
                    {
                      "oneofInt32": 1,
                      "oneof_int32": 2
                    }""", builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
        try {
            TestOneof.Builder builder = TestOneof.newBuilder();
            mergeFromJson(
                    """
                            {
                              "oneofInt32": 1,
                              "oneofNullValue": null
                            }""", builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testMapFields() throws Exception {
        TestMap.Builder builder = TestMap.newBuilder();
        builder.putInt32ToInt32Map(1, 10);
        builder.putInt64ToInt32Map(1234567890123456789L, 10);
        builder.putUint32ToInt32Map(2, 20);
        builder.putUint64ToInt32Map(2234567890123456789L, 20);
        builder.putSint32ToInt32Map(3, 30);
        builder.putSint64ToInt32Map(3234567890123456789L, 30);
        builder.putFixed32ToInt32Map(4, 40);
        builder.putFixed64ToInt32Map(4234567890123456789L, 40);
        builder.putSfixed32ToInt32Map(5, 50);
        builder.putSfixed64ToInt32Map(5234567890123456789L, 50);
        builder.putBoolToInt32Map(false, 6);
        builder.putStringToInt32Map("Hello", 10);
        builder.putInt32ToInt64Map(1, 1234567890123456789L);
        builder.putInt32ToUint32Map(2, 20);
        builder.putInt32ToUint64Map(2, 2234567890123456789L);
        builder.putInt32ToSint32Map(3, 30);
        builder.putInt32ToSint64Map(3, 3234567890123456789L);
        builder.putInt32ToFixed32Map(4, 40);
        builder.putInt32ToFixed64Map(4, 4234567890123456789L);
        builder.putInt32ToSfixed32Map(5, 50);
        builder.putInt32ToSfixed64Map(5, 5234567890123456789L);
        builder.putInt32ToFloatMap(6, 1.5f);
        builder.putInt32ToDoubleMap(6, 1.25);
        builder.putInt32ToBoolMap(7, false);
        builder.putInt32ToStringMap(7, "World");
        builder.putInt32ToBytesMap(8, ByteString.copyFrom(new byte[]{1, 2, 3}));
        builder.putInt32ToMessageMap(8, NestedMessage.newBuilder().setValue(1234).build());
        builder.putInt32ToEnumMap(9, NestedEnum.BAR);
        TestMap message = builder.build();
        assertThat(toJsonString(message)).isEqualTo(
                """
                        {
                          "int32ToInt32Map": {
                            "1": 10
                          },
                          "int64ToInt32Map": {
                            "1234567890123456789": 10
                          },
                          "uint32ToInt32Map": {
                            "2": 20
                          },
                          "uint64ToInt32Map": {
                            "2234567890123456789": 20
                          },
                          "sint32ToInt32Map": {
                            "3": 30
                          },
                          "sint64ToInt32Map": {
                            "3234567890123456789": 30
                          },
                          "fixed32ToInt32Map": {
                            "4": 40
                          },
                          "fixed64ToInt32Map": {
                            "4234567890123456789": 40
                          },
                          "sfixed32ToInt32Map": {
                            "5": 50
                          },
                          "sfixed64ToInt32Map": {
                            "5234567890123456789": 50
                          },
                          "boolToInt32Map": {
                            "false": 6
                          },
                          "stringToInt32Map": {
                            "Hello": 10
                          },
                          "int32ToInt64Map": {
                            "1": "1234567890123456789"
                          },
                          "int32ToUint32Map": {
                            "2": 20
                          },
                          "int32ToUint64Map": {
                            "2": "2234567890123456789"
                          },
                          "int32ToSint32Map": {
                            "3": 30
                          },
                          "int32ToSint64Map": {
                            "3": "3234567890123456789"
                          },
                          "int32ToFixed32Map": {
                            "4": 40
                          },
                          "int32ToFixed64Map": {
                            "4": "4234567890123456789"
                          },
                          "int32ToSfixed32Map": {
                            "5": 50
                          },
                          "int32ToSfixed64Map": {
                            "5": "5234567890123456789"
                          },
                          "int32ToFloatMap": {
                            "6": 1.5
                          },
                          "int32ToDoubleMap": {
                            "6": 1.25
                          },
                          "int32ToBoolMap": {
                            "7": false
                          },
                          "int32ToStringMap": {
                            "7": "World"
                          },
                          "int32ToBytesMap": {
                            "8": "AQID"
                          },
                          "int32ToMessageMap": {
                            "8": {
                              "value": 1234
                            }
                          },
                          "int32ToEnumMap": {
                            "9": "BAR"
                          }
                        }""");
        assertRoundTripEquals(message);
        builder = TestMap.newBuilder();
        builder.putInt32ToInt32Map(1, 2);
        builder.putInt32ToInt32Map(3, 4);
        message = builder.build();
        assertThat(toJsonString(message))
                .isEqualTo(
                        """
                                {
                                  "int32ToInt32Map": {
                                    "1": 2,
                                    "3": 4
                                  }
                                }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testMapNullValueIsRejected() throws Exception {
        try {
            TestMap.Builder builder = TestMap.newBuilder();
            mergeFromJson(
                    """
                            {
                              "int32ToInt32Map": {null: 1},
                              "int32ToMessageMap": {null: 2}
                            }""",
                    builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }

        try {
            TestMap.Builder builder = TestMap.newBuilder();
            mergeFromJson(
                    """
                            {
                              "int32ToInt32Map": {"1": null},
                              "int32ToMessageMap": {"2": null}
                            }""",
                    builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testMapEnumNullValueIsIgnored() throws Exception {
        TestMap.Builder builder = TestMap.newBuilder();
        mergeFromJsonIgnoringUnknownFields(
                """
                        {
                          "int32ToEnumMap": {"1": null}
                        }""", builder);
        TestMap map = builder.build();
        assertThat(map.getInt32ToEnumMapMap()).isEmpty();
    }

    @Test
    public void testArrayTypeMismatch() throws IOException {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson(
                    """
                            {
                              "repeated_int32": 5
                            }""",
                    builder);
            assertWithMessage("should have thrown exception for incorrect type").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat()
                    .isEqualTo("Expected an array for repeated_int32 but found 5");
        }
    }

    @Test
    public void testParserAcceptNonQuotedObjectKey() throws Exception {
        TestMap.Builder builder = TestMap.newBuilder();
        mergeFromJson(
                """
                        {
                          int32ToInt32Map: {1: 2},
                          stringToInt32Map: {hello: 3}
                        }""", builder);
        TestMap message = builder.build();
        assertThat(message.getInt32ToInt32MapMap().get(1)).isEqualTo(2);
        assertThat(message.getStringToInt32MapMap().get("hello")).isEqualTo(3);
    }

    @Test
    public void testWrappers() throws Exception {
        TestWrappers.Builder builder = TestWrappers.newBuilder();
        builder.getBoolValueBuilder().setValue(false);
        builder.getInt32ValueBuilder().setValue(0);
        builder.getInt64ValueBuilder().setValue(0);
        builder.getUint32ValueBuilder().setValue(0);
        builder.getUint64ValueBuilder().setValue(0);
        builder.getFloatValueBuilder().setValue(0.0f);
        builder.getDoubleValueBuilder().setValue(0.0);
        builder.getStringValueBuilder().setValue("");
        builder.getBytesValueBuilder().setValue(ByteString.EMPTY);
        TestWrappers message = builder.build();

        assertThat(toJsonString(message))
                .isEqualTo(
                        """
                                {
                                  "int32Value": 0,
                                  "uint32Value": 0,
                                  "int64Value": "0",
                                  "uint64Value": "0",
                                  "floatValue": 0.0,
                                  "doubleValue": 0.0,
                                  "boolValue": false,
                                  "stringValue": "",
                                  "bytesValue": ""
                                }""");
        assertRoundTripEquals(message);

        builder = TestWrappers.newBuilder();
        builder.getBoolValueBuilder().setValue(true);
        builder.getInt32ValueBuilder().setValue(1);
        builder.getInt64ValueBuilder().setValue(2);
        builder.getUint32ValueBuilder().setValue(3);
        builder.getUint64ValueBuilder().setValue(4);
        builder.getFloatValueBuilder().setValue(5.0f);
        builder.getDoubleValueBuilder().setValue(6.0);
        builder.getStringValueBuilder().setValue("7");
        builder.getBytesValueBuilder().setValue(ByteString.copyFrom(new byte[]{8}));
        message = builder.build();

        assertThat(toJsonString(message))
                .isEqualTo(
                        """
                                {
                                  "int32Value": 1,
                                  "uint32Value": 3,
                                  "int64Value": "2",
                                  "uint64Value": "4",
                                  "floatValue": 5.0,
                                  "doubleValue": 6.0,
                                  "boolValue": true,
                                  "stringValue": "7",
                                  "bytesValue": "CA=="
                                }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testTimestamp() throws Exception {
        TestTimestamp message =
                TestTimestamp.newBuilder()
                        .setTimestampValue(Timestamps.parse("1970-01-01T00:00:00Z"))
                        .build();

        assertThat(toJsonString(message))
                .isEqualTo("""
                        {
                          "timestampValue": "1970-01-01T00:00:00Z"
                        }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testTimestampMergeError() throws Exception {
        final String incorrectTimestampString = "{\"seconds\":1800,\"nanos\":0}";
        try {
            TestTimestamp.Builder builder = TestTimestamp.newBuilder();
            mergeFromJson(String.format("{\"timestamp_value\": %s}", incorrectTimestampString), builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException e) {
            assertThat(e)
                    .hasMessageThat()
                    .isEqualTo("Failed to parse timestamp: " + incorrectTimestampString);
            assertThat(e).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testDuration() throws Exception {
        TestDuration message =
                TestDuration.newBuilder().setDurationValue(Durations.parse("12345s")).build();

        assertThat(toJsonString(message)).isEqualTo("""
                {
                  "durationValue": "12345s"
                }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testDurationMergeError() throws Exception {
        final String incorrectDurationString = "{\"seconds\":10,\"nanos\":500}";
        try {
            TestDuration.Builder builder = TestDuration.newBuilder();
            mergeFromJson(String.format("{\"duration_value\": %s}", incorrectDurationString), builder);
            assertWithMessage("expected exception").fail();
        } catch (InvalidProtocolBufferException e) {
            assertThat(e)
                    .hasMessageThat()
                    .isEqualTo("Failed to parse duration: " + incorrectDurationString);
            assertThat(e).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testFieldMask() throws Exception {
        TestFieldMask message =
                TestFieldMask.newBuilder()
                        .setFieldMaskValue(FieldMaskUtil.fromString("foo.bar,baz,foo_bar.baz"))
                        .build();

        assertThat(toJsonString(message))
                .isEqualTo("""
                        {
                          "fieldMaskValue": "foo.bar,baz,fooBar.baz"
                        }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testStruct() throws Exception {
        TestStruct.Builder builder = TestStruct.newBuilder();
        Struct.Builder structBuilder = builder.getStructValueBuilder();
        structBuilder.putFields("null_value", Value.newBuilder().setNullValueValue(0).build());
        structBuilder.putFields("number_value", Value.newBuilder().setNumberValue(1.25).build());
        structBuilder.putFields("string_value", Value.newBuilder().setStringValue("hello").build());
        Struct.Builder subStructBuilder = Struct.newBuilder();
        subStructBuilder.putFields("number_value", Value.newBuilder().setNumberValue(1234).build());
        structBuilder.putFields(
                "struct_value", Value.newBuilder().setStructValue(subStructBuilder.build()).build());
        ListValue.Builder listBuilder = ListValue.newBuilder();
        listBuilder.addValues(Value.newBuilder().setNumberValue(1.125).build());
        listBuilder.addValues(Value.newBuilder().setNullValueValue(0).build());
        structBuilder.putFields(
                "list_value", Value.newBuilder().setListValue(listBuilder.build()).build());
        TestStruct message = builder.build();

        assertThat(toJsonString(message))
                .isEqualTo(
                        """
                                {
                                  "structValue": {
                                    "null_value": null,
                                    "number_value": 1.25,
                                    "string_value": "hello",
                                    "struct_value": {
                                      "number_value": 1234.0
                                    },
                                    "list_value": [1.125, null]
                                  }
                                }""");
        assertRoundTripEquals(message);

        builder = TestStruct.newBuilder();
        builder.setValue(Value.newBuilder().setNullValueValue(0).build());
        message = builder.build();
        assertThat(toJsonString(message)).isEqualTo("""
                {
                  "value": null
                }""");
        assertRoundTripEquals(message);

        builder = TestStruct.newBuilder();
        listBuilder = builder.getListValueBuilder();
        listBuilder.addValues(Value.newBuilder().setNumberValue(31831.125).build());
        listBuilder.addValues(Value.newBuilder().setNullValueValue(0).build());
        message = builder.build();
        assertThat(toJsonString(message))
                .isEqualTo("""
                        {
                          "listValue": [31831.125, null]
                        }""");
        assertRoundTripEquals(message);
    }


    @Test
    public void testAnyFieldsWithCustomAddedTypeRegistry() throws Exception {
        TestAllTypes content = TestAllTypes.newBuilder().setOptionalInt32(1234).build();
        TestAny message = TestAny.newBuilder().setAnyValue(Any.pack(content)).build();
        com.google.protobuf.TypeRegistry registry = com.google.protobuf.TypeRegistry.newBuilder().add(content.getDescriptorForType()).build();
        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry);
        assertThat(printer.print(message)).isEqualTo(
                """
                        {
                          "anyValue": {
                            "@type": "type.googleapis.com/json_test.TestAllTypes",
                            "optionalInt32": 1234
                          }
                        }""");
        assertRoundTripEquals(message, registry);
        TestAny messageWithDefaultAnyValue = TestAny.newBuilder().setAnyValue(Any.getDefaultInstance()).build();
        assertThat(printer.print(messageWithDefaultAnyValue))
                .isEqualTo("""
                        {
                          "anyValue": {}
                        }""");
        assertRoundTripEquals(messageWithDefaultAnyValue, registry);
        Any anyMessage = Any.pack(Any.pack(content));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Any",
                                  "value": {
                                    "@type": "type.googleapis.com/json_test.TestAllTypes",
                                    "optionalInt32": 1234
                                  }
                                }""");
        assertRoundTripEquals(anyMessage, registry);
    }

    @Test
    public void testAnyFields() throws Exception {
        TestAllTypes content = TestAllTypes.newBuilder().setOptionalInt32(1234).build();
        TestAny message = TestAny.newBuilder().setAnyValue(Any.pack(content)).build();
        try {
            toJsonString(message);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasMessageThat().isEqualTo(
                    "Cannot find type for url: type.googleapis.com/json_test.TestAllTypes");
        }
        TypeRegistry registry = TypeRegistry.newBuilder().add(TestAllTypes.getDescriptor()).build();
        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry);
        assertThat(printer.print(message)).isEqualTo(
                """
                        {
                          "anyValue": {
                            "@type": "type.googleapis.com/json_test.TestAllTypes",
                            "optionalInt32": 1234
                          }
                        }""");
        assertRoundTripEquals(message, registry);
        TestAny messageWithDefaultAnyValue = TestAny.newBuilder().setAnyValue(Any.getDefaultInstance()).build();
        assertThat(printer.print(messageWithDefaultAnyValue))
                .isEqualTo("""
                        {
                          "anyValue": {}
                        }""");
        assertRoundTripEquals(messageWithDefaultAnyValue, registry);
        Any anyMessage = Any.pack(Any.pack(content));
        assertThat(printer.print(anyMessage)).isEqualTo(
                """
                        {
                          "@type": "type.googleapis.com/google.protobuf.Any",
                          "value": {
                            "@type": "type.googleapis.com/json_test.TestAllTypes",
                            "optionalInt32": 1234
                          }
                        }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(Int32Value.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Int32Value",
                                  "value": 12345
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(UInt32Value.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.UInt32Value",
                                  "value": 12345
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(Int64Value.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Int64Value",
                                  "value": "12345"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(UInt64Value.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.UInt64Value",
                                  "value": "12345"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(FloatValue.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.FloatValue",
                                  "value": 12345.0
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(DoubleValue.of(12345));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.DoubleValue",
                                  "value": 12345.0
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(BoolValue.of(true));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.BoolValue",
                                  "value": true
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(StringValue.of("Hello"));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.StringValue",
                                  "value": "Hello"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(BytesValue.of(ByteString.copyFrom(new byte[]{1, 2})));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.BytesValue",
                                  "value": "AQI="
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(Timestamps.parse("1969-12-31T23:59:59Z"));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Timestamp",
                                  "value": "1969-12-31T23:59:59Z"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(Durations.parse("12345.10s"));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Duration",
                                  "value": "12345.100s"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(FieldMaskUtil.fromString("foo.bar,baz"));
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.FieldMask",
                                  "value": "foo.bar,baz"
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        Struct.Builder structBuilder = Struct.newBuilder();
        structBuilder.putFields("number", Value.newBuilder().setNumberValue(1.125).build());
        anyMessage = Any.pack(structBuilder.build());
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Struct",
                                  "value": {
                                    "number": 1.125
                                  }
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        Value.Builder valueBuilder = Value.newBuilder();
        valueBuilder.setNumberValue(1);
        anyMessage = Any.pack(valueBuilder.build());
        assertThat(printer.print(anyMessage))
                .isEqualTo(
                        """
                                {
                                  "@type": "type.googleapis.com/google.protobuf.Value",
                                  "value": 1.0
                                }""");
        assertRoundTripEquals(anyMessage, registry);
        anyMessage = Any.pack(Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build());
        assertThat(printer.print(anyMessage)).isEqualTo(
                """
                        {
                          "@type": "type.googleapis.com/google.protobuf.Value",
                          "value": null
                        }""");
        assertRoundTripEquals(anyMessage, registry);
    }

    @Test
    public void testAnyInMaps() throws Exception {
        TypeRegistry registry =
                TypeRegistry.newBuilder().add(TestAllTypes.getDescriptor()).build();
        JsonFormat.Printer printer = JsonFormat.printer().usingTypeRegistry(registry);

        TestAny.Builder testAny = TestAny.newBuilder();
        testAny.putAnyMap("int32_wrapper", Any.pack(Int32Value.of(123)));
        testAny.putAnyMap("int64_wrapper", Any.pack(Int64Value.of(456)));
        testAny.putAnyMap("timestamp", Any.pack(Timestamps.parse("1969-12-31T23:59:59Z")));
        testAny.putAnyMap("duration", Any.pack(Durations.parse("12345.1s")));
        testAny.putAnyMap("field_mask", Any.pack(FieldMaskUtil.fromString("foo.bar,baz")));
        Value numberValue = Value.newBuilder().setNumberValue(1.125).build();
        Struct.Builder struct = Struct.newBuilder();
        struct.putFields("number", numberValue);
        testAny.putAnyMap("struct", Any.pack(struct.build()));
        Value nullValue = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        testAny.putAnyMap(
                "list_value",
                Any.pack(ListValue.newBuilder().addValues(numberValue).addValues(nullValue).build()));
        testAny.putAnyMap("number_value", Any.pack(numberValue));
        testAny.putAnyMap("any_value_number", Any.pack(Any.pack(numberValue)));
        testAny.putAnyMap("any_value_default", Any.pack(Any.getDefaultInstance()));
        testAny.putAnyMap("default", Any.getDefaultInstance());

        assertThat(printer.print(testAny.build()))
                .isEqualTo(
                        """
                                {
                                  "anyMap": {
                                    "int32_wrapper": {
                                      "@type": "type.googleapis.com/google.protobuf.Int32Value",
                                      "value": 123
                                    },
                                    "int64_wrapper": {
                                      "@type": "type.googleapis.com/google.protobuf.Int64Value",
                                      "value": "456"
                                    },
                                    "timestamp": {
                                      "@type": "type.googleapis.com/google.protobuf.Timestamp",
                                      "value": "1969-12-31T23:59:59Z"
                                    },
                                    "duration": {
                                      "@type": "type.googleapis.com/google.protobuf.Duration",
                                      "value": "12345.100s"
                                    },
                                    "field_mask": {
                                      "@type": "type.googleapis.com/google.protobuf.FieldMask",
                                      "value": "foo.bar,baz"
                                    },
                                    "struct": {
                                      "@type": "type.googleapis.com/google.protobuf.Struct",
                                      "value": {
                                        "number": 1.125
                                      }
                                    },
                                    "list_value": {
                                      "@type": "type.googleapis.com/google.protobuf.ListValue",
                                      "value": [1.125, null]
                                    },
                                    "number_value": {
                                      "@type": "type.googleapis.com/google.protobuf.Value",
                                      "value": 1.125
                                    },
                                    "any_value_number": {
                                      "@type": "type.googleapis.com/google.protobuf.Any",
                                      "value": {
                                        "@type": "type.googleapis.com/google.protobuf.Value",
                                        "value": 1.125
                                      }
                                    },
                                    "any_value_default": {
                                      "@type": "type.googleapis.com/google.protobuf.Any",
                                      "value": {}
                                    },
                                    "default": {}
                                  }
                                }""");
        assertRoundTripEquals(testAny.build(), registry);
    }

    @Test
    public void testParserMissingTypeUrl() {
        try {
            Any.Builder builder = Any.newBuilder();
            mergeFromJson("""
                    {
                      "optionalInt32": 1234
                    }""", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (IOException ignored) {

        }
    }

    @Test
    public void testParserUnexpectedTypeUrl() {
        try {
            Any.Builder builder = Any.newBuilder();
            mergeFromJson(
                    """
                            {
                              "@type": "type.googleapis.com/json_test.UnexpectedTypes",
                              "optionalInt32": 12345
                            }""",
                    builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (IOException ignored) {

        }
    }

    @Test
    public void testParserRejectTrailingComma() {
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            mergeFromJson("""
                    {
                      "optionalInt32": 12345,
                    }""", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (IOException ignored) {
        }
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            mergeFromJson(
                    """
                            {
                              "repeatedInt32": [12345,]
                            }""", builder);
            assertWithMessage("IOException expected.").fail();
        } catch (IOException ignored) {
        }
    }

    @Test
    public void testParserRejectInvalidBase64() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson("{\"" + "optionalBytes" + "\":" + "!@#$" + "}", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException expected) {
            assertThat(expected).hasCauseThat().isNotNull();
        }
    }

    @Test
    public void testParserAcceptBase64Variants() throws Exception {
        assertAccepts("optionalBytes", "AQI");
        assertAccepts("optionalBytes", "-_w");
    }

    @Test
    public void testParserRejectInvalidEnumValue() throws Exception {
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            mergeFromJson("""
                    {
                      "optionalNestedEnum": "XXX"
                    }""", builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testParserUnknownFields() {
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            String json = """
                    {
                      "unknownField": "XXX"
                    }""";
            JsonFormat.parser().merge(json, builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testParserIgnoringUnknownFields() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        String json = """
                {
                  "unknownField": "XXX"
                }""";
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
    }

    @Test
    public void testParserIgnoringUnknownEnums() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        String json = """
                {
                  "optionalNestedEnum": "XXX"
                }""";
        JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
        assertThat(builder.getOptionalNestedEnumValue()).isEqualTo(0);
    }

    @Test
    public void testParserSupportAliasEnums() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        String json = """
                {
                  "optionalAliasedEnum": "QUX"
                }""";
        JsonFormat.parser().merge(json, builder);
        assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);

        builder = TestAllTypes.newBuilder();
        json = """
                {
                  "optionalAliasedEnum": "qux"
                }""";
        JsonFormat.parser().merge(json, builder);
        assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);

        builder = TestAllTypes.newBuilder();
        json = """
                {
                  "optionalAliasedEnum": "bAz"
                }""";
        JsonFormat.parser().merge(json, builder);
        assertThat(builder.getOptionalAliasedEnum()).isEqualTo(AliasedEnum.ALIAS_BAZ);
    }

    @Test
    public void testUnknownEnumMap() throws Exception {
        TestMap.Builder builder = TestMap.newBuilder();
        JsonFormat.parser()
                .ignoringUnknownFields()
                .merge("{\n" + "  \"int32ToEnumMap\": {1: XXX, 2: FOO}" + "}", builder);

        assertThat(builder.getInt32ToEnumMapMap()).containsEntry(2, NestedEnum.FOO);
        assertThat(builder.getInt32ToEnumMapMap()).hasSize(1);
    }

    @Test
    public void testRepeatedUnknownEnum() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        JsonFormat.parser()
                .ignoringUnknownFields()
                .merge("{\n" + "  \"repeatedNestedEnum\": [XXX, FOO, BAR, BAZ]" + "}", builder);

        assertThat(builder.getRepeatedNestedEnum(0)).isEqualTo(NestedEnum.FOO);
        assertThat(builder.getRepeatedNestedEnum(1)).isEqualTo(NestedEnum.BAR);
        assertThat(builder.getRepeatedNestedEnum(2)).isEqualTo(NestedEnum.BAZ);
        assertThat(builder.getRepeatedNestedEnumList()).hasSize(3);
    }

    @Test
    public void testParserIntegerEnumValue() throws Exception {
        TestAllTypes.Builder actualBuilder = TestAllTypes.newBuilder();
        mergeFromJson("""
                {
                  "optionalNestedEnum": 2
                }""", actualBuilder);

        TestAllTypes expected = TestAllTypes.newBuilder().setOptionalNestedEnum(NestedEnum.BAZ).build();
        assertThat(actualBuilder.build()).isEqualTo(expected);
    }

    @Test
    public void testCustomJsonName() throws Exception {
        TestCustomJsonName message = TestCustomJsonName.newBuilder().setValue(12345).build();
        assertThat(JsonFormat.printer().print(message))
                .isEqualTo("""
                        {
                          "@value": 12345
                        }""");
        assertRoundTripEquals(message);
    }

    @Test
    public void testHtmlEscape() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalString("</script>").build();
        assertThat(toJsonString(message))
                .isEqualTo("{\n  \"optionalString\": \"\\u003c/script\\u003e\"\n}");

        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        JsonFormat.parser().merge(toJsonString(message), builder);
        assertThat(builder.getOptionalString()).isEqualTo(message.getOptionalString());
    }

    @Test
    public void testIncludingDefaultValueFields() throws Exception {
        TestAllTypes message = TestAllTypes.getDefaultInstance();
        assertThat(JsonFormat.printer().print(message)).isEqualTo("{\n}");
        assertThat(JsonFormat.printer().includingDefaultValueFields().print(message))
                .isEqualTo(
                        """
                                {
                                  "optionalInt32": 0,
                                  "optionalInt64": "0",
                                  "optionalUint32": 0,
                                  "optionalUint64": "0",
                                  "optionalSint32": 0,
                                  "optionalSint64": "0",
                                  "optionalFixed32": 0,
                                  "optionalFixed64": "0",
                                  "optionalSfixed32": 0,
                                  "optionalSfixed64": "0",
                                  "optionalFloat": 0.0,
                                  "optionalDouble": 0.0,
                                  "optionalBool": false,
                                  "optionalString": "",
                                  "optionalBytes": "",
                                  "optionalNestedEnum": "FOO",
                                  "repeatedInt32": [],
                                  "repeatedInt64": [],
                                  "repeatedUint32": [],
                                  "repeatedUint64": [],
                                  "repeatedSint32": [],
                                  "repeatedSint64": [],
                                  "repeatedFixed32": [],
                                  "repeatedFixed64": [],
                                  "repeatedSfixed32": [],
                                  "repeatedSfixed64": [],
                                  "repeatedFloat": [],
                                  "repeatedDouble": [],
                                  "repeatedBool": [],
                                  "repeatedString": [],
                                  "repeatedBytes": [],
                                  "repeatedNestedMessage": [],
                                  "repeatedNestedEnum": [],
                                  "optionalAliasedEnum": "ALIAS_FOO"
                                }""");

        Set<FieldDescriptor> fixedFields = new HashSet<>();
        for (FieldDescriptor fieldDesc : TestAllTypes.getDescriptor().getFields()) {
            if (fieldDesc.getName().contains("_fixed")) {
                fixedFields.add(fieldDesc);
            }
        }

        assertThat(JsonFormat.printer().includingDefaultValueFields(fixedFields).print(message))
                .isEqualTo(
                        """
                                {
                                  "optionalFixed32": 0,
                                  "optionalFixed64": "0",
                                  "repeatedFixed32": [],
                                  "repeatedFixed64": []
                                }""");

        TestAllTypes messageNonDefaults =
                message.toBuilder().setOptionalInt64(1234).setOptionalFixed32(3232).build();
        assertThat(
                JsonFormat.printer().includingDefaultValueFields(fixedFields).print(messageNonDefaults))
                .isEqualTo(
                        """
                                {
                                  "optionalInt64": "1234",
                                  "optionalFixed32": 3232,
                                  "optionalFixed64": "0",
                                  "repeatedFixed32": [],
                                  "repeatedFixed64": []
                                }""");

        try {
            JsonFormat.printer().includingDefaultValueFields().includingDefaultValueFields();
            assertWithMessage("IllegalStateException is expected.").fail();
        } catch (IllegalStateException e) {

            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        try {
            JsonFormat.printer().includingDefaultValueFields().includingDefaultValueFields(fixedFields);
            assertWithMessage("IllegalStateException is expected.").fail();
        } catch (IllegalStateException e) {

            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        try {
            JsonFormat.printer().includingDefaultValueFields(fixedFields).includingDefaultValueFields();
            assertWithMessage("IllegalStateException is expected.").fail();
        } catch (IllegalStateException e) {
            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        try {
            JsonFormat.printer()
                    .includingDefaultValueFields(fixedFields)
                    .includingDefaultValueFields(fixedFields);
            assertWithMessage("IllegalStateException is expected.").fail();
        } catch (IllegalStateException e) {
            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        Set<FieldDescriptor> intFields = new HashSet<>();
        for (FieldDescriptor fieldDesc : TestAllTypes.getDescriptor().getFields()) {
            if (fieldDesc.getName().contains("_int")) {
                intFields.add(fieldDesc);
            }
        }

        try {
            JsonFormat.printer()
                    .includingDefaultValueFields(intFields)
                    .includingDefaultValueFields(fixedFields);
            assertWithMessage("IllegalStateException is expected.").fail();
        } catch (IllegalStateException e) {
            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        try {
            JsonFormat.printer().includingDefaultValueFields(null);
            assertWithMessage("IllegalArgumentException is expected.").fail();
        } catch (IllegalArgumentException e) {
            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        try {
            JsonFormat.printer().includingDefaultValueFields(Collections.emptySet());
            assertWithMessage("IllegalArgumentException is expected.").fail();
        } catch (IllegalArgumentException e) {
            assertWithMessage("Exception message should mention includingDefaultValueFields.")
                    .that(e.getMessage().contains("includingDefaultValueFields"))
                    .isTrue();
        }

        TestMap mapMessage = TestMap.getDefaultInstance();
        assertThat(JsonFormat.printer().print(mapMessage)).isEqualTo("{\n}");
        assertThat(JsonFormat.printer().includingDefaultValueFields().print(mapMessage))
                .isEqualTo(
                        """
                                {
                                  "int32ToInt32Map": {
                                  },
                                  "int64ToInt32Map": {
                                  },
                                  "uint32ToInt32Map": {
                                  },
                                  "uint64ToInt32Map": {
                                  },
                                  "sint32ToInt32Map": {
                                  },
                                  "sint64ToInt32Map": {
                                  },
                                  "fixed32ToInt32Map": {
                                  },
                                  "fixed64ToInt32Map": {
                                  },
                                  "sfixed32ToInt32Map": {
                                  },
                                  "sfixed64ToInt32Map": {
                                  },
                                  "boolToInt32Map": {
                                  },
                                  "stringToInt32Map": {
                                  },
                                  "int32ToInt64Map": {
                                  },
                                  "int32ToUint32Map": {
                                  },
                                  "int32ToUint64Map": {
                                  },
                                  "int32ToSint32Map": {
                                  },
                                  "int32ToSint64Map": {
                                  },
                                  "int32ToFixed32Map": {
                                  },
                                  "int32ToFixed64Map": {
                                  },
                                  "int32ToSfixed32Map": {
                                  },
                                  "int32ToSfixed64Map": {
                                  },
                                  "int32ToFloatMap": {
                                  },
                                  "int32ToDoubleMap": {
                                  },
                                  "int32ToBoolMap": {
                                  },
                                  "int32ToStringMap": {
                                  },
                                  "int32ToBytesMap": {
                                  },
                                  "int32ToMessageMap": {
                                  },
                                  "int32ToEnumMap": {
                                  }
                                }""");

        TestOneof oneofMessage = TestOneof.getDefaultInstance();
        assertThat(JsonFormat.printer().print(oneofMessage)).isEqualTo("{\n}");
        assertThat(JsonFormat.printer().includingDefaultValueFields().print(oneofMessage))
                .isEqualTo("{\n}");

        oneofMessage = TestOneof.newBuilder().setOneofInt32(42).build();
        assertThat(JsonFormat.printer().print(oneofMessage)).isEqualTo("{\n  \"oneofInt32\": 42\n}");
        assertThat(JsonFormat.printer().includingDefaultValueFields().print(oneofMessage))
                .isEqualTo("{\n  \"oneofInt32\": 42\n}");

        TestOneof.Builder oneofBuilder = TestOneof.newBuilder();
        mergeFromJson("""
                {
                  "oneofNullValue": null\s
                }""", oneofBuilder);
        oneofMessage = oneofBuilder.build();
        assertThat(JsonFormat.printer().print(oneofMessage))
                .isEqualTo("{\n  \"oneofNullValue\": null\n}");
        assertThat(JsonFormat.printer().includingDefaultValueFields().print(oneofMessage))
                .isEqualTo("{\n  \"oneofNullValue\": null\n}");
    }

    @Test
    public void testPreservingProtoFieldNames() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalInt32(12345).build();
        assertThat(JsonFormat.printer().print(message))
                .isEqualTo("""
                        {
                          "optionalInt32": 12345
                        }""");
        assertThat(JsonFormat.printer().preservingProtoFieldNames().print(message))
                .isEqualTo("""
                        {
                          "optional_int32": 12345
                        }""");
        TestCustomJsonName messageWithCustomJsonName =
                TestCustomJsonName.newBuilder().setValue(12345).build();
        assertThat(JsonFormat.printer().preservingProtoFieldNames().print(messageWithCustomJsonName))
                .isEqualTo("""
                        {
                          "value": 12345
                        }""");
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        JsonFormat.parser().merge("{\"optionalInt32\": 12345}", builder);
        assertThat(builder.getOptionalInt32()).isEqualTo(12345);
        builder.clear();
        JsonFormat.parser().merge("{\"optional_int32\": 54321}", builder);
        assertThat(builder.getOptionalInt32()).isEqualTo(54321);
    }

    @Test
    public void testPrintingEnumsAsInts() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalNestedEnum(NestedEnum.BAR).build();
        assertThat(JsonFormat.printer().printingEnumsAsInts().print(message))
                .isEqualTo("""
                        {
                          "optionalNestedEnum": 1
                        }""");
    }

    @Test
    public void testOmittingInsignificantWhiteSpace() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalInt32(12345).build();
        assertThat(JsonFormat.printer().omittingInsignificantWhitespace().print(message))
                .isEqualTo("{" + "\"optionalInt32\":12345" + "}");
        TestAllTypes message1 = TestAllTypes.getDefaultInstance();
        assertThat(JsonFormat.printer().omittingInsignificantWhitespace().print(message1))
                .isEqualTo("{}");
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        setAllFields(builder);
        TestAllTypes message2 = builder.build();
        assertThat(toCompactJsonString(message2))
                .isEqualTo(
                        "{"
                                + "\"optionalInt32\":1234,"
                                + "\"optionalInt64\":\"1234567890123456789\","
                                + "\"optionalUint32\":5678,"
                                + "\"optionalUint64\":\"2345678901234567890\","
                                + "\"optionalSint32\":9012,"
                                + "\"optionalSint64\":\"3456789012345678901\","
                                + "\"optionalFixed32\":3456,"
                                + "\"optionalFixed64\":\"4567890123456789012\","
                                + "\"optionalSfixed32\":7890,"
                                + "\"optionalSfixed64\":\"5678901234567890123\","
                                + "\"optionalFloat\":1.5,"
                                + "\"optionalDouble\":1.25,"
                                + "\"optionalBool\":true,"
                                + "\"optionalString\":\"Hello world!\","
                                + "\"optionalBytes\":\"AAEC\","
                                + "\"optionalNestedMessage\":{"
                                + "\"value\":100"
                                + "},"
                                + "\"optionalNestedEnum\":\"BAR\","
                                + "\"repeatedInt32\":[1234,234],"
                                + "\"repeatedInt64\":[\"1234567890123456789\",\"234567890123456789\"],"
                                + "\"repeatedUint32\":[5678,678],"
                                + "\"repeatedUint64\":[\"2345678901234567890\",\"345678901234567890\"],"
                                + "\"repeatedSint32\":[9012,10],"
                                + "\"repeatedSint64\":[\"3456789012345678901\",\"456789012345678901\"],"
                                + "\"repeatedFixed32\":[3456,456],"
                                + "\"repeatedFixed64\":[\"4567890123456789012\",\"567890123456789012\"],"
                                + "\"repeatedSfixed32\":[7890,890],"
                                + "\"repeatedSfixed64\":[\"5678901234567890123\",\"678901234567890123\"],"
                                + "\"repeatedFloat\":[1.5,11.5],"
                                + "\"repeatedDouble\":[1.25,11.25],"
                                + "\"repeatedBool\":[true,true],"
                                + "\"repeatedString\":[\"Hello world!\",\"ello world!\"],"
                                + "\"repeatedBytes\":[\"AAEC\",\"AQI=\"],"
                                + "\"repeatedNestedMessage\":[{"
                                + "\"value\":100"
                                + "},{"
                                + "\"value\":200"
                                + "}],"
                                + "\"repeatedNestedEnum\":[\"BAR\",\"BAZ\"]"
                                + "}");
    }

    @Test
    public void testEmptyWrapperTypesInAny() throws Exception {
        TypeRegistry registry =
                TypeRegistry.newBuilder().add(TestAllTypes.getDescriptor()).build();
        JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);

        Any.Builder builder = Any.newBuilder();
        parser.merge(
                """
                        {
                          "@type": "type.googleapis.com/google.protobuf.BoolValue",
                          "value": false
                        }
                        """,
                builder);
        Any any = builder.build();
        assertThat(any.getValue().size()).isEqualTo(0);
    }

    @Test
    public void testRecursionLimit() throws Exception {
        String input =
                """
                        {
                          "nested": {
                            "nested": {
                              "nested": {
                                "nested": {
                                  "value": 1234
                                }
                              }
                            }
                          }
                        }
                        """;

        JsonFormat.Parser parser = JsonFormat.parser();
        TestRecursive.Builder builder = TestRecursive.newBuilder();
        parser.merge(input, builder);
        TestRecursive message = builder.build();
        assertThat(message.getNested().getNested().getNested().getNested().getValue()).isEqualTo(1234);

        parser = JsonFormat.parser().usingRecursionLimit(3);
        builder = TestRecursive.newBuilder();
        try {
            parser.merge(input, builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testJsonException_forwardsIOException() {
        InputStream throwingInputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("12345");
            }
        };
        InputStreamReader throwingReader = new InputStreamReader(throwingInputStream);
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            JsonFormat.parser().merge(throwingReader, builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (IOException e) {
            assertThat(e).hasMessageThat().isEqualTo("12345");
        }
    }

    @Test
    public void testJsonException_forwardsJsonException() throws Exception {
        Reader invalidJsonReader = new StringReader("{ xxx - yyy }");
        try {
            TestAllTypes.Builder builder = TestAllTypes.newBuilder();
            JsonFormat.parser().merge(invalidJsonReader, builder);
            assertWithMessage("Exception is expected.").fail();
        } catch (InvalidProtocolBufferException e) {
            assertThat(e.getCause()).isInstanceOf(JsonSyntaxException.class);
        }
    }

    @Test
    public void testJsonObjectForPrimitiveField() throws Exception {
        TestAllTypes.Builder builder = TestAllTypes.newBuilder();
        try {
            mergeFromJson(
                    """
                            {
                              "optionalString": {
                                "invalidNestedString": "Hello world"
                              }
                            }
                            """,
                    builder);
        } catch (InvalidProtocolBufferException ignored) {
        }
    }

    @Test
    public void testSortedMapKeys() throws Exception {
        TestMap.Builder mapBuilder = TestMap.newBuilder();
        mapBuilder.putStringToInt32Map("\ud834\udd20", 3);
        mapBuilder.putStringToInt32Map("foo", 99);
        mapBuilder.putStringToInt32Map("xxx", 123);
        mapBuilder.putStringToInt32Map("\u20ac", 1);
        mapBuilder.putStringToInt32Map("abc", 20);
        mapBuilder.putStringToInt32Map("19", 19);
        mapBuilder.putStringToInt32Map("8", 8);
        mapBuilder.putStringToInt32Map("\ufb00", 2);
        mapBuilder.putInt32ToInt32Map(3, 3);
        mapBuilder.putInt32ToInt32Map(10, 10);
        mapBuilder.putInt32ToInt32Map(5, 5);
        mapBuilder.putInt32ToInt32Map(4, 4);
        mapBuilder.putInt32ToInt32Map(1, 1);
        mapBuilder.putInt32ToInt32Map(2, 2);
        mapBuilder.putInt32ToInt32Map(-3, -3);
        TestMap mapMessage = mapBuilder.build();
        assertThat(toSortedJsonString(mapMessage)).isEqualTo(
                """
                        {
                          "int32ToInt32Map": {
                            "-3": -3,
                            "1": 1,
                            "2": 2,
                            "3": 3,
                            "4": 4,
                            "5": 5,
                            "10": 10
                          },
                          "stringToInt32Map": {
                            "19": 19,
                            "8": 8,
                            "abc": 20,
                            "foo": 99,
                            "xxx": 123,
                            "\u20ac": 1,
                            "\ufb00": 2,
                            "\ud834\udd20": 3
                          }
                        }""");
        TestMap emptyMap = TestMap.getDefaultInstance();
        assertThat(toSortedJsonString(emptyMap)).isEqualTo("{\n}");
    }

    @Test
    public void testPrintingEnumsAsIntsChainedAfterIncludingDefaultValueFields() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalBool(false).build();
        assertThat(JsonFormat.printer().includingDefaultValueFields(ImmutableSet.of(message.getDescriptorForType().findFieldByName("optional_bool")))
                .printingEnumsAsInts().print(message))
                .isEqualTo("""
                        {
                          "optionalBool": false
                        }""");
    }

    @Test
    public void testPreservesFloatingPointNegative0() throws Exception {
        TestAllTypes message = TestAllTypes.newBuilder().setOptionalFloat(-0.0f).setOptionalDouble(-0.0).build();
        assertThat(JsonFormat.printer().print(message)).isEqualTo("{\n  \"optionalFloat\": -0.0,\n  \"optionalDouble\": -0.0\n}");
    }
}
