/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.scalars.ExtendedScalars;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's custom scalar coercion subsystem for an event scheduling API. */
public class EventScheduleScalarTest {

    @Test
    public void acceptsAndSerializesScheduleTimeFromClientVariable() {
        GraphQL graphQL = buildGraphQL();
        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("""
                        query Schedule($startsAt: DateTime!) {
                          schedule(startsAt: $startsAt) {
                            title
                            startsAt
                          }
                        }
                        """)
                .variables(Map.of("startsAt", "2026-04-15T09:30:00Z"))
                .build();

        ExecutionResult result = graphQL.execute(input);

        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
        assertEquals(Map.of("schedule", Map.of(
                "title", "Product launch",
                "startsAt", "2026-04-15T09:30:00.000Z")), result.getData());
    }

    private static GraphQL buildGraphQL() {
        String schema = """
                scalar DateTime
                type Query {
                  schedule(startsAt: DateTime!): Event!
                }
                type Event {
                  title: String!
                  startsAt: DateTime!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .scalar(ExtendedScalars.DateTime)
                .type("Query", type -> type.dataFetcher("schedule", environment -> new Event(
                        "Product launch", environment.getArgument("startsAt"))))
                .type("Event", type -> type
                        .dataFetcher("title", environment -> ((Event) environment.getSource()).title())
                        .dataFetcher("startsAt", environment -> ((Event) environment.getSource()).startsAt()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private record Event(String title, OffsetDateTime startsAt) {
    }
}
