/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java_extended_scalars;

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.StringValue;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Graphql_java_extended_scalarsTest {

    @Test
    void temporalAndIdentifierScalarsRoundTripThroughGraphQl() throws Exception {
        EchoResult dateTimeVariable = executeVariableEcho(ExtendedScalars.DateTime, "2024-01-02T03:04:05Z");
        assertSuccessful(dateTimeVariable);
        assertThat(dateTimeVariable.inputValue()).isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));
        assertThat(outputValue(dateTimeVariable.executionResult())).isEqualTo("2024-01-02T03:04:05.000Z");

        EchoResult dateTimeLiteral = executeLiteralEcho(ExtendedScalars.DateTime, "\"2024-01-02T03:04:05Z\"");
        assertSuccessful(dateTimeLiteral);
        assertThat(dateTimeLiteral.inputValue()).isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));
        assertThat(outputValue(dateTimeLiteral.executionResult())).isEqualTo("2024-01-02T03:04:05.000Z");

        assertThat(ExtendedScalars.DateTime.getCoercing().serialize(ZonedDateTime.parse("2024-01-02T03:04:05+02:00")))
            .isEqualTo("2024-01-02T03:04:05.000+02:00");

        EchoResult dateVariable = executeVariableEcho(ExtendedScalars.Date, "2024-01-02");
        assertSuccessful(dateVariable);
        assertThat(dateVariable.inputValue()).isEqualTo(LocalDate.parse("2024-01-02"));
        assertThat(outputValue(dateVariable.executionResult())).isEqualTo("2024-01-02");

        EchoResult timeVariable = executeVariableEcho(ExtendedScalars.Time, "03:04:05Z");
        assertSuccessful(timeVariable);
        assertThat(timeVariable.inputValue()).isEqualTo(OffsetTime.parse("03:04:05Z"));
        assertThat(outputValue(timeVariable.executionResult())).isEqualTo("03:04:05Z");

        EchoResult localTimeVariable = executeVariableEcho(ExtendedScalars.LocalTime, "03:04:05");
        assertSuccessful(localTimeVariable);
        assertThat(localTimeVariable.inputValue()).isEqualTo(LocalTime.parse("03:04:05"));
        assertThat(outputValue(localTimeVariable.executionResult())).isEqualTo("03:04:05");

        EchoResult uuidVariable = executeVariableEcho(ExtendedScalars.UUID, "123e4567-e89b-12d3-a456-426614174000");
        assertSuccessful(uuidVariable);
        assertThat(uuidVariable.inputValue()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        assertThat(outputValue(uuidVariable.executionResult())).isEqualTo("123e4567-e89b-12d3-a456-426614174000");

        EchoResult localeVariable = executeVariableEcho(ExtendedScalars.Locale, "fr-CA");
        assertSuccessful(localeVariable);
        assertThat(localeVariable.inputValue()).isEqualTo(Locale.forLanguageTag("fr-CA"));
        assertThat(outputValue(localeVariable.executionResult())).isEqualTo("fr-CA");

    }

    @Test
    void objectAndJsonScalarsPreserveNestedStructures() {
        Map<String, Object> variableInput = new LinkedHashMap<>();
        variableInput.put("count", 1);
        variableInput.put("enabled", true);
        variableInput.put("tags", List.of("alpha", "beta"));
        variableInput.put("child", Map.of("code", "A1"));

        assertStructuredScalarRoundTrip(ExtendedScalars.Object, variableInput);
        assertStructuredScalarRoundTrip(ExtendedScalars.Json, variableInput);
    }

    @Test
    void numericAndCharacterScalarsValidateBoundaries() {
        assertThat(ExtendedScalars.PositiveInt.getCoercing().parseValue(1)).isEqualTo(1);
        assertThatThrownBy(() -> ExtendedScalars.PositiveInt.getCoercing().parseValue(0))
            .hasMessageContaining("positive integer");

        assertThat(ExtendedScalars.NegativeInt.getCoercing().parseValue(-1)).isEqualTo(-1);
        assertThatThrownBy(() -> ExtendedScalars.NegativeInt.getCoercing().parseValue(0))
            .hasMessageContaining("negative integer");

        assertThat(ExtendedScalars.NonNegativeFloat.getCoercing().parseLiteral(new IntValue(BigInteger.ZERO))).isEqualTo(0.0d);
        assertThatThrownBy(() -> ExtendedScalars.NonPositiveFloat.getCoercing().parseValue(0.1d))
            .hasMessageContaining("less than or equal to zero");

        assertThat(ExtendedScalars.GraphQLByte.getCoercing().parseLiteral(new IntValue(BigInteger.valueOf(127)))).isEqualTo((byte) 127);
        assertThatThrownBy(() -> ExtendedScalars.GraphQLByte.getCoercing().parseLiteral(new IntValue(BigInteger.valueOf(128))))
            .hasMessageContaining("Byte range");

        assertThat(ExtendedScalars.GraphQLShort.getCoercing().parseLiteral(new IntValue(BigInteger.valueOf(32767))))
            .isEqualTo((short) 32767);
        assertThatThrownBy(() -> ExtendedScalars.GraphQLShort.getCoercing().parseValue(32768))
            .hasMessageContaining("Short");

        assertThat(ExtendedScalars.GraphQLLong.getCoercing().parseLiteral(new StringValue("1234567890123")))
            .isEqualTo(1_234_567_890_123L);
        assertThat(ExtendedScalars.GraphQLBigDecimal.getCoercing().parseLiteral(new FloatValue(new BigDecimal("12.34"))))
            .isEqualTo(new BigDecimal("12.34"));
        assertThat(ExtendedScalars.GraphQLBigInteger.getCoercing().parseLiteral(new StringValue("1234")))
            .isEqualTo(new BigInteger("1234"));

        assertThat(ExtendedScalars.GraphQLChar.getCoercing().parseValue("x")).isEqualTo('x');
        assertThatThrownBy(() -> ExtendedScalars.GraphQLChar.getCoercing().parseValue("xy"))
            .hasMessageContaining("Char");
    }

    @Test
    void urlScalarAcceptsUrlObjectsAndPreservesExternalForm() throws Exception {
        URL url = new URL("file:/tmp/graphql/schema.graphql");

        URL parsedValue = (URL) ExtendedScalars.Url.getCoercing().parseValue(url);
        assertThat(parsedValue.toExternalForm()).isEqualTo(url.toExternalForm());

        StringValue literal = (StringValue) ExtendedScalars.Url.getCoercing().valueToLiteral(url);
        assertThat(literal.getValue()).isEqualTo(url.toExternalForm());

        EchoResult variableResult = executeVariableEcho(ExtendedScalars.Url, url);
        assertSuccessful(variableResult);
        assertThat(variableResult.inputValue()).isInstanceOf(URL.class);
        assertThat(((URL) variableResult.inputValue()).toExternalForm()).isEqualTo(url.toExternalForm());
        assertThat(outputValue(variableResult.executionResult())).isInstanceOf(URL.class);
        assertThat(((URL) outputValue(variableResult.executionResult())).toExternalForm()).isEqualTo(url.toExternalForm());
    }

    @Test
    void urlScalarAcceptsUriAndFileInputs() throws Exception {
        Path schemaPath = Path.of("build", "tmp", "graphql", "schema.graphql");

        URI uri = schemaPath.toUri();
        assertUrlLikeInputRoundTrip(uri, uri.toURL().toExternalForm());

        File file = schemaPath.toFile();
        assertUrlLikeInputRoundTrip(file, file.toURI().toURL().toExternalForm());
    }

    @Test
    void regexAndAliasedScalarsIntegrateWithGraphQlSchemas() {
        GraphQLScalarType regexScalar = ExtendedScalars.newRegexScalar("HexColor")
            .description("Matches 6 digit hexadecimal colors")
            .addPattern(Pattern.compile("#[0-9A-F]{6}"))
            .build();

        EchoResult validRegex = executeVariableEcho(regexScalar, "#A1B2C3");
        assertSuccessful(validRegex);
        assertThat(validRegex.inputValue()).isEqualTo("#A1B2C3");
        assertThat(outputValue(validRegex.executionResult())).isEqualTo("#A1B2C3");

        EchoResult invalidRegex = executeVariableEcho(regexScalar, "blue");
        assertThat(invalidRegex.executionResult().getErrors()).isNotEmpty();
        assertThat(invalidRegex.executionResult().getErrors().get(0).getMessage()).contains("HexColor");
        assertThat(invalidRegex.inputValue()).isNull();

        GraphQLScalarType aliasedScalar = ExtendedScalars.newAliasedScalar("EpochMillis")
            .description("Alias for the built-in long scalar")
            .aliasedScalar(ExtendedScalars.GraphQLLong)
            .build();

        assertThat(aliasedScalar.getName()).isEqualTo("EpochMillis");
        assertThat(aliasedScalar.getDescription()).isEqualTo("Alias for the built-in long scalar");

        EchoResult aliasedVariable = executeVariableEcho(aliasedScalar, 42L);
        assertSuccessful(aliasedVariable);
        assertThat(aliasedVariable.inputValue()).isEqualTo(42L);
        assertThat(outputValue(aliasedVariable.executionResult())).isEqualTo(42L);

        EchoResult aliasedLiteral = executeLiteralEcho(aliasedScalar, "42");
        assertSuccessful(aliasedLiteral);
        assertThat(aliasedLiteral.inputValue()).isEqualTo(42L);
        assertThat(outputValue(aliasedLiteral.executionResult())).isEqualTo(42L);
    }

    private void assertUrlLikeInputRoundTrip(Object input, String expectedExternalForm) {
        URL parsedValue = (URL) ExtendedScalars.Url.getCoercing().parseValue(input);
        assertThat(parsedValue.toExternalForm()).isEqualTo(expectedExternalForm);

        StringValue literal = (StringValue) ExtendedScalars.Url.getCoercing().valueToLiteral(input);
        assertThat(literal.getValue()).isEqualTo(expectedExternalForm);

        EchoResult variableResult = executeVariableEcho(ExtendedScalars.Url, input);
        assertSuccessful(variableResult);
        assertThat(variableResult.inputValue()).isInstanceOf(URL.class);
        assertThat(((URL) variableResult.inputValue()).toExternalForm()).isEqualTo(expectedExternalForm);
        assertThat(outputValue(variableResult.executionResult())).isInstanceOf(URL.class);
        assertThat(((URL) outputValue(variableResult.executionResult())).toExternalForm()).isEqualTo(expectedExternalForm);
    }

    private void assertStructuredScalarRoundTrip(GraphQLScalarType scalarType, Map<String, Object> variableInput) {
        EchoResult variableResult = executeVariableEcho(scalarType, variableInput);
        assertSuccessful(variableResult);
        assertThat(variableResult.inputValue()).isInstanceOf(Map.class);
        assertCountAndFlags(asMap(variableResult.inputValue()));
        assertCountAndFlags(asMap(outputValue(variableResult.executionResult())));
        assertThat(asMap(outputValue(variableResult.executionResult())).get("tags")).isEqualTo(List.of("alpha", "beta"));

        EchoResult literalResult = executeLiteralEcho(scalarType, "{count: 1, enabled: true, tags: [\"alpha\", \"beta\"], child: {code: \"A1\"}}");
        assertSuccessful(literalResult);
        assertThat(literalResult.inputValue()).isInstanceOf(Map.class);
        assertCountAndFlags(asMap(literalResult.inputValue()));
        assertThat(asMap(outputValue(literalResult.executionResult())).get("child")).isEqualTo(Map.of("code", "A1"));
    }

    private void assertCountAndFlags(Map<String, Object> value) {
        assertThat(value).containsEntry("enabled", true);
        assertThat(value.get("count")).isInstanceOf(Number.class);
        assertThat(((Number) value.get("count")).intValue()).isEqualTo(1);
    }

    private EchoResult executeVariableEcho(GraphQLScalarType scalarType, Object variableValue) {
        AtomicReference<Object> inputValue = new AtomicReference<>();
        GraphQL graphQl = createEchoGraphQl(scalarType, inputValue);
        String query = """
            query($input: %s!) {
              echo(input: $input)
            }
            """.formatted(scalarType.getName());
        ExecutionResult executionResult = graphQl.execute(ExecutionInput.newExecutionInput(query)
            .variables(Map.of("input", variableValue))
            .build());
        return new EchoResult(executionResult, inputValue.get());
    }

    private EchoResult executeLiteralEcho(GraphQLScalarType scalarType, String literal) {
        AtomicReference<Object> inputValue = new AtomicReference<>();
        GraphQL graphQl = createEchoGraphQl(scalarType, inputValue);
        String query = """
            {
              echo(input: %s)
            }
            """.formatted(literal);
        ExecutionResult executionResult = graphQl.execute(query);
        return new EchoResult(executionResult, inputValue.get());
    }

    private GraphQL createEchoGraphQl(GraphQLScalarType scalarType, AtomicReference<Object> inputValue) {
        String typeName = "Query";
        String fieldName = "echo";

        GraphQLFieldDefinition field = GraphQLFieldDefinition.newFieldDefinition()
            .name(fieldName)
            .type(scalarType)
            .argument(GraphQLArgument.newArgument()
                .name("input")
                .type(scalarType)
                .build())
            .build();

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
            .name(typeName)
            .field(field)
            .build();

        DataFetcher<Object> echoDataFetcher = environment -> {
            Object argument = environment.getArgument("input");
            inputValue.set(argument);
            return argument;
        };

        GraphQLCodeRegistry codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
            .dataFetcher(FieldCoordinates.coordinates(typeName, fieldName), echoDataFetcher)
            .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .additionalType(scalarType)
            .codeRegistry(codeRegistry)
            .build();

        return GraphQL.newGraphQL(schema).build();
    }

    private void assertSuccessful(EchoResult echoResult) {
        assertThat(echoResult.executionResult().getErrors()).isEmpty();
    }

    private Object outputValue(ExecutionResult executionResult) {
        Map<String, Object> data = asMap(executionResult.getData());
        return data == null ? null : data.get("echo");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private record EchoResult(ExecutionResult executionResult, Object inputValue) {
    }
}
