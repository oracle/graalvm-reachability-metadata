/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java_extended_scalars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.language.VariableReference;
import graphql.scalars.ExtendedScalars;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class Graphql_java_extended_scalarsTest {

    @Test
    void integratesCustomScalarsWithGraphQlExecution() {
        GraphQLScalarType postalCode = ExtendedScalars.newRegexScalar("PostalCode")
                .description("Matches public and internal postal codes")
                .addPatterns(Pattern.compile("\\d{5}"), Pattern.compile("HQ-\\d{4}"))
                .build();
        GraphQLScalarType orderId = ExtendedScalars.newAliasedScalar("OrderId")
                .description("An aliased UUID")
                .aliasedScalar(ExtendedScalars.UUID)
                .build();
        GraphQL graphQL = GraphQL.newGraphQL(GraphQLSchema.newSchema()
                        .query(GraphQLObjectType.newObject()
                                .name("Query")
                                .field(echoField("echoDateTime", ExtendedScalars.DateTime))
                                .field(echoField("echoJson", ExtendedScalars.Json))
                                .field(echoField("echoPostalCode", postalCode))
                                .field(echoField("echoOrderId", orderId))
                                .build())
                        .codeRegistry(GraphQLCodeRegistry.newCodeRegistry()
                                .dataFetcher(
                                        FieldCoordinates.coordinates("Query", "echoDateTime"),
                                        (DataFetcher<Object>) environment -> environment.getArgument("input"))
                                .dataFetcher(
                                        FieldCoordinates.coordinates("Query", "echoJson"),
                                        (DataFetcher<Object>) environment -> environment.getArgument("input"))
                                .dataFetcher(
                                        FieldCoordinates.coordinates("Query", "echoPostalCode"),
                                        (DataFetcher<Object>) environment -> environment.getArgument("input"))
                                .dataFetcher(
                                        FieldCoordinates.coordinates("Query", "echoOrderId"),
                                        (DataFetcher<Object>) environment -> environment.getArgument("input"))
                                .build())
                        .build())
                .build();

        Map<String, Object> jsonInput = new LinkedHashMap<>();
        jsonInput.put("name", "Pi");
        jsonInput.put("enabled", true);
        jsonInput.put("values", List.of(1, 2, 3));
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("dateTime", "2024-05-06T07:08:09.123Z");
        variables.put("json", jsonInput);
        variables.put("postalCode", "HQ-2024");
        variables.put("orderId", "123e4567-e89b-12d3-a456-426614174000");

        ExecutionResult executionResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("""
                        query($dateTime: DateTime!, $json: JSON!, $postalCode: PostalCode!, $orderId: OrderId!) {
                          echoDateTime(input: $dateTime)
                          echoJson(input: $json)
                          echoPostalCode(input: $postalCode)
                          echoOrderId(input: $orderId)
                        }
                        """)
                .variables(variables)
                .build());

        assertThat(executionResult.getErrors()).isEmpty();
        Map<String, Object> data = executionResult.getData();
        assertThat(data)
                .containsEntry("echoDateTime", "2024-05-06T07:08:09.123Z")
                .containsEntry("echoPostalCode", "HQ-2024")
                .containsEntry("echoOrderId", "123e4567-e89b-12d3-a456-426614174000");
        assertThat(data.get("echoJson")).isEqualTo(jsonInput);
    }

    @Test
    void supportsTemporalScalars() {
        OffsetDateTime dateTime = OffsetDateTime.of(2024, 5, 6, 7, 8, 9, 123_000_000, ZoneOffset.UTC);
        LocalDate date = LocalDate.of(2024, 5, 6);
        OffsetTime time = OffsetTime.of(7, 8, 9, 123_000_000, ZoneOffset.ofHours(2));
        LocalTime localTime = LocalTime.of(7, 8, 9, 123_000_000);

        assertThat(ExtendedScalars.DateTime.getCoercing().serialize(dateTime))
                .isEqualTo("2024-05-06T07:08:09.123Z");
        assertThat(ExtendedScalars.DateTime.getCoercing().parseValue("2024-05-06T07:08:09.123Z"))
                .isEqualTo(dateTime);
        assertThat(((StringValue) ExtendedScalars.DateTime.getCoercing().valueToLiteral(dateTime)).getValue())
                .isEqualTo("2024-05-06T07:08:09.123Z");
        assertThatThrownBy(() -> ExtendedScalars.DateTime.getCoercing().parseValue("2024-05-06"))
                .isInstanceOf(CoercingParseValueException.class)
                .hasMessageContaining("Invalid RFC3339 value");

        assertThat(ExtendedScalars.Date.getCoercing().serialize(date)).isEqualTo("2024-05-06");
        assertThat(ExtendedScalars.Date.getCoercing().parseLiteral(new StringValue("2024-05-06")))
                .isEqualTo(date);
        assertThatThrownBy(() -> ExtendedScalars.Date.getCoercing().parseLiteral(IntValue.of(20240506)))
                .isInstanceOf(CoercingParseLiteralException.class);

        assertThat(ExtendedScalars.Time.getCoercing().serialize(time)).isEqualTo("07:08:09.123+02:00");
        assertThat(ExtendedScalars.Time.getCoercing().parseLiteral(new StringValue("07:08:09.123+02:00")))
                .isEqualTo(time);
        assertThatThrownBy(() -> ExtendedScalars.Time.getCoercing().parseValue("07:08:09.123"))
                .isInstanceOf(CoercingParseValueException.class)
                .hasMessageContaining("Invalid RFC3339 full time value");

        assertThat(ExtendedScalars.LocalTime.getCoercing().serialize(localTime)).isEqualTo("07:08:09.123");
        assertThat(ExtendedScalars.LocalTime.getCoercing().parseValue("07:08:09.123"))
                .isEqualTo(localTime);
    }

    @Test
    void supportsIdentityNetworkAndLocaleScalars() throws Exception {
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        URL url = new URL(null, "https://example.com/graphql?query=1#fragment", new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL url) {
                throw new UnsupportedOperationException("Connection access is not part of this test");
            }
        });
        Locale locale = Locale.forLanguageTag("sr-Latn-RS");

        assertThat(ExtendedScalars.UUID.getCoercing().parseValue(uuid.toString())).isEqualTo(uuid);
        assertThat(ExtendedScalars.UUID.getCoercing().serialize(uuid)).isEqualTo(uuid.toString());
        assertThat(((StringValue) ExtendedScalars.UUID.getCoercing().valueToLiteral(uuid)).getValue())
                .isEqualTo(uuid.toString());

        assertThat(ExtendedScalars.Url.getCoercing().parseValue(url)).isEqualTo(url);
        assertThat(((StringValue) ExtendedScalars.Url.getCoercing().valueToLiteral(url)).getValue())
                .isEqualTo(url.toExternalForm());
        assertThatThrownBy(() -> ExtendedScalars.Url.getCoercing().parseLiteral(IntValue.of(1)))
                .isInstanceOf(CoercingParseLiteralException.class);

        assertThat(ExtendedScalars.Locale.getCoercing().parseValue("sr-Latn-RS")).isEqualTo(locale);
        assertThat(ExtendedScalars.Locale.getCoercing().serialize(locale)).isEqualTo("sr-Latn-RS");
        assertThat(((StringValue) ExtendedScalars.Locale.getCoercing().valueToLiteral(locale)).getValue())
                .isEqualTo("sr-Latn-RS");
        assertThatThrownBy(() -> ExtendedScalars.Locale.getCoercing().parseValue(1))
                .isInstanceOf(CoercingParseValueException.class);
    }

    @Test
    void objectScalarConvertsFloatingPointAndBigIntegerValuesToGraphQlLiterals() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("floatValue", 1.25f);
        value.put("doubleValue", 2.5d);
        value.put("decimalValue", new BigDecimal("3.1415"));
        value.put("bigIntegerValue", new BigInteger("12345678901234567890"));
        value.put("items", List.of(new BigDecimal("4.5"), new BigInteger("6")));

        Value<?> literal = ExtendedScalars.Object.getCoercing().valueToLiteral(value);

        assertThat(literal).isInstanceOf(ObjectValue.class);
        ObjectValue objectValue = (ObjectValue) literal;
        assertThat(objectValue.getObjectFields()).extracting(ObjectField::getName)
                .containsExactly("floatValue", "doubleValue", "decimalValue", "bigIntegerValue", "items");
        assertThat(((FloatValue) objectValue.getObjectFields().get(0).getValue()).getValue())
                .isEqualByComparingTo(new BigDecimal("1.25"));
        assertThat(((FloatValue) objectValue.getObjectFields().get(1).getValue()).getValue())
                .isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(((FloatValue) objectValue.getObjectFields().get(2).getValue()).getValue())
                .isEqualByComparingTo(new BigDecimal("3.1415"));
        assertThat(((IntValue) objectValue.getObjectFields().get(3).getValue()).getValue())
                .isEqualTo(new BigInteger("12345678901234567890"));
        assertThat(((FloatValue) ((ArrayValue) objectValue.getObjectFields().get(4).getValue()).getValues().get(0)).getValue())
                .isEqualByComparingTo(new BigDecimal("4.5"));
        assertThat(((IntValue) ((ArrayValue) objectValue.getObjectFields().get(4).getValue()).getValues().get(1)).getValue())
                .isEqualTo(new BigInteger("6"));
    }

    @Test
    void objectAndJsonScalarsConvertNestedStructures() {
        ObjectValue literal = new ObjectValue(List.of(
                new ObjectField("message", new StringValue("ok")),
                new ObjectField("size", new IntValue(BigInteger.valueOf(2))),
                new ObjectField("enabled", new BooleanValue(true)),
                new ObjectField("status", graphql.language.EnumValue.of("READY")),
                new ObjectField(
                        "items",
                        new ArrayValue(List.of(new StringValue("alpha"), new VariableReference("dynamicValue")))),
                new ObjectField(
                        "nested",
                        new ObjectValue(List.of(new ObjectField("pi", new FloatValue(new BigDecimal("3.14"))))))));
        Map<String, Object> variables = Map.of("dynamicValue", "beta");

        Object parsed = ExtendedScalars.Object.getCoercing().parseLiteral(literal, variables);

        assertThat(ExtendedScalars.Object.getName()).isEqualTo("Object");
        assertThat(parsed).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> parsedMap = (Map<String, Object>) parsed;
        assertThat(parsedMap)
                .containsEntry("message", "ok")
                .containsEntry("size", BigInteger.valueOf(2))
                .containsEntry("enabled", true)
                .containsEntry("status", "READY")
                .containsEntry("items", List.of("alpha", "beta"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nestedMap = (Map<String, Object>) parsedMap.get("nested");
        assertThat(nestedMap).containsEntry("pi", new BigDecimal("3.14"));

        List<Object> values = new ArrayList<>();
        values.add(1);
        values.add(null);
        values.add(true);
        Map<String, Object> jsonValue = new LinkedHashMap<>();
        jsonValue.put("name", "Pi");
        jsonValue.put("values", values);

        Value<?> jsonLiteral = ExtendedScalars.Json.getCoercing().valueToLiteral(jsonValue);

        assertThat(ExtendedScalars.Json.getName()).isEqualTo("JSON");
        assertThat(jsonLiteral).isInstanceOf(ObjectValue.class);
        ObjectValue objectValue = (ObjectValue) jsonLiteral;
        assertThat(objectValue.getObjectFields()).extracting(ObjectField::getName).containsExactly("name", "values");
        assertThat(objectValue.getObjectFields().get(0).getValue()).isInstanceOfSatisfying(
                StringValue.class, value -> assertThat(value.getValue()).isEqualTo("Pi"));
        assertThat(objectValue.getObjectFields().get(1).getValue()).isInstanceOf(ArrayValue.class);
        ArrayValue arrayValue = (ArrayValue) objectValue.getObjectFields().get(1).getValue();
        assertThat(arrayValue.getValues().get(1)).isInstanceOf(NullValue.class);
    }

    @Test
    void numericScalarsEnforceConfiguredSigns() {
        assertIntegerSignContract(ExtendedScalars.PositiveInt, 1, 0);
        assertIntegerSignContract(ExtendedScalars.NegativeInt, -1, 0);
        assertIntegerSignContract(ExtendedScalars.NonPositiveInt, 0, 1);
        assertIntegerSignContract(ExtendedScalars.NonNegativeInt, 0, -1);

        assertFloatSignContract(ExtendedScalars.PositiveFloat, 1.5, 0.0);
        assertFloatSignContract(ExtendedScalars.NegativeFloat, -1.5, 0.0);
        assertFloatSignContract(ExtendedScalars.NonPositiveFloat, 0.0, 1.5);
        assertFloatSignContract(ExtendedScalars.NonNegativeFloat, 0.0, -1.5);
    }

    @Test
    void javaPrimitiveScalarsApplyRangeChecksAndLiteralConversions() {
        assertThat(ExtendedScalars.GraphQLLong.getCoercing().parseLiteral(new IntValue(BigInteger.valueOf(Long.MAX_VALUE))))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(ExtendedScalars.GraphQLLong.getCoercing().parseLiteral(new StringValue(Long.toString(Long.MAX_VALUE))))
                .isEqualTo(Long.MAX_VALUE);
        assertThatThrownBy(() -> ExtendedScalars.GraphQLLong
                        .getCoercing()
                        .parseLiteral(new IntValue(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))))
                .isInstanceOf(CoercingParseLiteralException.class);

        assertThat(ExtendedScalars.GraphQLShort.getCoercing().parseValue("32767")).isEqualTo((short) 32767);
        assertThatThrownBy(() -> ExtendedScalars.GraphQLShort.getCoercing().parseValue("32768"))
                .isInstanceOf(CoercingParseValueException.class);

        assertThat(ExtendedScalars.GraphQLByte.getCoercing().serialize((byte) 12)).isEqualTo((byte) 12);
        assertThatThrownBy(() -> ExtendedScalars.GraphQLByte.getCoercing().parseLiteral(IntValue.of(128)))
                .isInstanceOf(CoercingParseLiteralException.class);

        assertThat(ExtendedScalars.GraphQLBigInteger.getCoercing().parseLiteral(new FloatValue(new BigDecimal("42.0"))))
                .isEqualTo(BigInteger.valueOf(42));
        assertThatThrownBy(() -> ExtendedScalars.GraphQLBigInteger
                        .getCoercing()
                        .parseLiteral(new FloatValue(new BigDecimal("42.5"))))
                .isInstanceOf(CoercingParseLiteralException.class);

        BigDecimal bigDecimal = (BigDecimal) ExtendedScalars.GraphQLBigDecimal.getCoercing()
                .parseLiteral(new StringValue("12.34"));
        assertThat(bigDecimal).isEqualByComparingTo(new BigDecimal("12.34"));
        assertThat(((FloatValue) ExtendedScalars.GraphQLBigDecimal.getCoercing().valueToLiteral(new BigDecimal("12.34")))
                        .getValue())
                .isEqualByComparingTo(new BigDecimal("12.34"));

        assertThat(ExtendedScalars.GraphQLChar.getCoercing().parseValue("\u00DF")).isEqualTo('\u00DF');
        assertThat(((StringValue) ExtendedScalars.GraphQLChar.getCoercing().valueToLiteral('\u00DF')).getValue())
                .isEqualTo("\u00DF");
        assertThatThrownBy(() -> ExtendedScalars.GraphQLChar.getCoercing().parseLiteral(new StringValue("ab")))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    @Test
    void regexScalarsAcceptNonStringInputsByStringifyingThem() {
        GraphQLScalarType zipCode = ExtendedScalars.newRegexScalar("ZipCode")
                .description("Five digit zip code")
                .addPattern(Pattern.compile("\\d{5}"))
                .build();

        assertThat(zipCode.getCoercing().parseValue(90210)).isEqualTo("90210");
        assertThat(zipCode.getCoercing().serialize(90210)).isEqualTo("90210");
        assertThat(((StringValue) zipCode.getCoercing().valueToLiteral(90210)).getValue()).isEqualTo("90210");
    }

    @Test
    void builderScalarsPreserveDescriptionsAndDelegateCoercion() {
        GraphQLScalarType postalCode = ExtendedScalars.newRegexScalar("PostalCode")
                .description("Matches public and internal postal codes")
                .addPatterns(Pattern.compile("\\d{5}"), Pattern.compile("HQ-\\d{4}"))
                .build();
        UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        GraphQLScalarType orderId = ExtendedScalars.newAliasedScalar("OrderId")
                .description("An aliased UUID")
                .aliasedScalar(ExtendedScalars.UUID)
                .build();

        assertThat(postalCode.getName()).isEqualTo("PostalCode");
        assertThat(postalCode.getDescription()).isEqualTo("Matches public and internal postal codes");
        assertThat(postalCode.getCoercing().parseValue("12345")).isEqualTo("12345");
        assertThat(postalCode.getCoercing().parseValue("HQ-2024")).isEqualTo("HQ-2024");
        assertThatThrownBy(() -> postalCode.getCoercing().parseValue("invalid"))
                .isInstanceOf(CoercingParseValueException.class)
                .hasMessageContaining("PostalCode");

        assertThat(orderId.getName()).isEqualTo("OrderId");
        assertThat(orderId.getDescription()).isEqualTo("An aliased UUID");
        assertThat(orderId.getCoercing().parseLiteral(new StringValue(uuid.toString()))).isEqualTo(uuid);
        assertThat(((StringValue) orderId.getCoercing().valueToLiteral(uuid)).getValue()).isEqualTo(uuid.toString());
    }

    private static GraphQLFieldDefinition echoField(String name, GraphQLScalarType scalarType) {
        return GraphQLFieldDefinition.newFieldDefinition()
                .name(name)
                .type(scalarType)
                .argument(GraphQLArgument.newArgument().name("input").type(scalarType).build())
                .build();
    }

    private static void assertIntegerSignContract(GraphQLScalarType scalarType, int accepted, int rejected) {
        assertThat(scalarType.getCoercing().parseLiteral(IntValue.of(accepted))).isEqualTo(accepted);
        assertThatThrownBy(() -> scalarType.getCoercing().parseLiteral(IntValue.of(rejected)))
                .isInstanceOf(CoercingParseLiteralException.class);
    }

    private static void assertFloatSignContract(GraphQLScalarType scalarType, double accepted, double rejected) {
        assertThat(scalarType.getCoercing().parseLiteral(FloatValue.of(accepted))).isEqualTo(accepted);
        assertThatThrownBy(() -> scalarType.getCoercing().parseLiteral(FloatValue.of(rejected)))
                .isInstanceOf(CoercingParseLiteralException.class);
    }
}
