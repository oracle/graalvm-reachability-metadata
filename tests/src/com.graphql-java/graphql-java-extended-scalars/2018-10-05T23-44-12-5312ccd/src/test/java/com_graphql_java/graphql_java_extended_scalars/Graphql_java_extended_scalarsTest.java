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
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.language.FloatValue;
import graphql.language.IntValue;
import graphql.language.Value;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Graphql_java_extended_scalarsTest {

    @Test
    void temporalScalarsRoundTripThroughGraphQl() {
        EchoResult dateTimeVariable = executeVariableEcho(ExtendedScalars.DateTime, "2024-01-02T03:04:05Z");
        assertSuccessful(dateTimeVariable);
        assertThat(dateTimeVariable.inputValue()).isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));
        assertThat(OffsetDateTime.parse((String) outputValue(dateTimeVariable.executionResult())))
            .isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));

        EchoResult dateTimeLiteral = executeLiteralEcho(ExtendedScalars.DateTime, "\"2024-01-02T03:04:05Z\"");
        assertSuccessful(dateTimeLiteral);
        assertThat(dateTimeLiteral.inputValue()).isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));
        assertThat(OffsetDateTime.parse((String) outputValue(dateTimeLiteral.executionResult())))
            .isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05Z"));

        Object serializedDateTime = serialize(
            ExtendedScalars.DateTime,
            ZonedDateTime.parse("2024-01-02T03:04:05+02:00"));
        assertThat(OffsetDateTime.parse((String) serializedDateTime))
            .isEqualTo(OffsetDateTime.parse("2024-01-02T03:04:05+02:00"));

        EchoResult dateVariable = executeVariableEcho(ExtendedScalars.Date, "2024-01-02");
        assertSuccessful(dateVariable);
        assertThat(dateVariable.inputValue()).isEqualTo(LocalDate.parse("2024-01-02"));
        assertThat(outputValue(dateVariable.executionResult())).isEqualTo("2024-01-02");

        EchoResult timeVariable = executeVariableEcho(ExtendedScalars.Time, "03:04:05Z");
        assertSuccessful(timeVariable);
        assertThat(timeVariable.inputValue()).isEqualTo(OffsetTime.parse("03:04:05Z"));
        assertThat(outputValue(timeVariable.executionResult())).isEqualTo("03:04:05Z");
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
    void numericScalarsValidateBoundaries() {
        assertThat(parseValue(ExtendedScalars.PositiveInt, 1)).isEqualTo(1);
        assertThatThrownBy(() -> parseValue(ExtendedScalars.PositiveInt, 0))
            .hasMessageContaining("positive integer");

        assertThat(parseValue(ExtendedScalars.NegativeInt, -1)).isEqualTo(-1);
        assertThatThrownBy(() -> parseValue(ExtendedScalars.NegativeInt, 0))
            .hasMessageContaining("negative integer");

        assertThat(parseValue(ExtendedScalars.NonPositiveInt, 0)).isEqualTo(0);
        assertThat(parseLiteral(ExtendedScalars.NonNegativeInt, new IntValue(BigInteger.ZERO))).isEqualTo(0);

        assertThat(parseValue(ExtendedScalars.PositiveFloat, 1.25d)).isEqualTo(1.25d);
        assertThat(parseValue(ExtendedScalars.NegativeFloat, -1.25d)).isEqualTo(-1.25d);
        assertThat(parseLiteral(ExtendedScalars.NonNegativeFloat, new FloatValue(new BigDecimal("12.34"))))
            .isEqualTo(12.34d);
        assertThatThrownBy(() -> parseValue(ExtendedScalars.NonPositiveFloat, 0.1d))
            .hasMessageContaining("less than or equal to zero");
    }

    @Test
    void urlScalarAcceptsUrlObjectsUriAndFileInputs() throws Exception {
        URL fileUrl = URI.create("file:/tmp/graphql/schema.graphql").toURL();

        assertUrlLikeInputRoundTrip(fileUrl, fileUrl.toExternalForm());

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

        GraphQLScalarType aliasedScalar = ExtendedScalars.newAliasedScalar("PositiveScore")
            .description("Alias for the positive integer scalar")
            .aliasedScalar(ExtendedScalars.PositiveInt)
            .build();

        assertThat(aliasedScalar.getName()).isEqualTo("PositiveScore");
        assertThat(aliasedScalar.getDescription()).isEqualTo("Alias for the positive integer scalar");

        EchoResult aliasedVariable = executeVariableEcho(aliasedScalar, 42);
        assertSuccessful(aliasedVariable);
        assertThat(aliasedVariable.inputValue()).isEqualTo(42);
        assertThat(outputValue(aliasedVariable.executionResult())).isEqualTo(42);

        EchoResult aliasedLiteral = executeLiteralEcho(aliasedScalar, "42");
        assertSuccessful(aliasedLiteral);
        assertThat(aliasedLiteral.inputValue()).isEqualTo(42);
        assertThat(outputValue(aliasedLiteral.executionResult())).isEqualTo(42);
    }

    private void assertUrlLikeInputRoundTrip(Object input, String expectedExternalForm) {
        URL parsedValue = (URL) parseValue(ExtendedScalars.Url, input);
        assertThat(parsedValue.toExternalForm()).isEqualTo(expectedExternalForm);

        URL serialized = (URL) serialize(ExtendedScalars.Url, input);
        assertThat(serialized.toExternalForm()).isEqualTo(expectedExternalForm);

        EchoResult variableResult = executeVariableEcho(ExtendedScalars.Url, input);
        assertSuccessful(variableResult);
        assertThat(variableResult.inputValue()).isInstanceOf(URL.class);
        assertThat(((URL) variableResult.inputValue()).toExternalForm()).isEqualTo(expectedExternalForm);
        assertThat(outputValue(variableResult.executionResult())).isInstanceOf(URL.class);
        URL outputUrl = (URL) outputValue(variableResult.executionResult());
        assertThat(outputUrl.toExternalForm()).isEqualTo(expectedExternalForm);
    }

    private Object serialize(GraphQLScalarType scalarType, Object input) {
        return scalarType.getCoercing().serialize(input);
    }

    private Object parseValue(GraphQLScalarType scalarType, Object input) {
        return scalarType.getCoercing().parseValue(input);
    }

    private Object parseLiteral(GraphQLScalarType scalarType, Value<?> input) {
        return scalarType.getCoercing().parseLiteral(input);
    }

    private void assertStructuredScalarRoundTrip(GraphQLScalarType scalarType, Map<String, Object> variableInput) {
        EchoResult variableResult = executeVariableEcho(scalarType, variableInput);
        assertSuccessful(variableResult);
        assertThat(variableResult.inputValue()).isInstanceOf(Map.class);
        assertCountAndFlags(asMap(variableResult.inputValue()));
        assertCountAndFlags(asMap(outputValue(variableResult.executionResult())));
        Map<String, Object> outputValue = asMap(outputValue(variableResult.executionResult()));
        assertThat(outputValue.get("tags")).isEqualTo(List.of("alpha", "beta"));

        String literalInput = "{count: 1, enabled: true, tags: [\"alpha\", \"beta\"], child: {code: \"A1\"}}";
        EchoResult literalResult = executeLiteralEcho(scalarType, literalInput);
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
        ExecutionResult executionResult = graphQl.execute(ExecutionInput.newExecutionInput()
            .query(query)
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
        DataFetcher<Object> echoDataFetcher = environment -> {
            Object argument = environment.getArgument("input");
            inputValue.set(argument);
            return argument;
        };

        GraphQLFieldDefinition field = GraphQLFieldDefinition.newFieldDefinition()
            .name("echo")
            .type(scalarType)
            .argument(GraphQLArgument.newArgument()
                .name("input")
                .type(scalarType)
                .build())
            .dataFetcher(echoDataFetcher)
            .build();

        GraphQLObjectType queryType = GraphQLObjectType.newObject()
            .name("Query")
            .field(field)
            .build();

        GraphQLSchema schema = GraphQLSchema.newSchema()
            .query(queryType)
            .additionalType(scalarType)
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
