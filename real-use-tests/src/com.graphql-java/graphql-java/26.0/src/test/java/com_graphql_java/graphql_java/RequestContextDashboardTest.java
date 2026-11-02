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
import graphql.GraphQLContext;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's request-context propagation subsystem for a customer dashboard. */
public class RequestContextDashboardTest {

    @Test
    public void resolvesDashboardFieldsForTheCurrentRequest() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult editorResult = graphQL.execute(requestContext("Ada", "EDITOR"));
        ExecutionResult viewerResult = graphQL.execute(requestContext("Lin", "VIEWER"));

        assertTrue(editorResult.getErrors().isEmpty(), () -> editorResult.getErrors().toString());
        assertTrue(viewerResult.getErrors().isEmpty(), () -> viewerResult.getErrors().toString());
        assertEquals(Map.of("dashboard", Map.of(
                "customerName", "Ada",
                "plan", "business",
                "canExport", true)), editorResult.getData());
        assertEquals(Map.of("dashboard", Map.of(
                "customerName", "Lin",
                "plan", "standard",
                "canExport", false)), viewerResult.getData());
    }

    private static ExecutionInput requestContext(String customerName, String role) {
        GraphQLContext context = GraphQLContext.of(Map.of(
                "customerName", customerName,
                "role", role));
        return ExecutionInput.newExecutionInput()
                .query("""
                        query CustomerDashboard {
                          dashboard {
                            customerName
                            plan
                            canExport
                          }
                        }
                        """)
                .graphQLContext(builder -> builder.of(context))
                .build();
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  dashboard: Dashboard!
                }
                type Dashboard {
                  customerName: String!
                  plan: String!
                  canExport: Boolean!
                }
                """);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("dashboard", environment -> new Dashboard()))
                .type("Dashboard", type -> type
                        .dataFetcher("customerName", environment ->
                                environment.getGraphQlContext().get("customerName"))
                        .dataFetcher("plan", environment ->
                                environment.getGraphQlContext().get("role").equals("EDITOR")
                                        ? "business" : "standard")
                        .dataFetcher("canExport", environment ->
                                environment.getGraphQlContext().get("role").equals("EDITOR")))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private static final class Dashboard {
    }
}
