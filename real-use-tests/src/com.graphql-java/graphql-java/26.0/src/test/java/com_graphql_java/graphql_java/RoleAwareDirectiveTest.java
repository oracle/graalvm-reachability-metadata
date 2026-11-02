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
import graphql.schema.DataFetcherFactories;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaDirectiveWiring;
import graphql.schema.idl.SchemaDirectiveWiringEnvironment;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's schema directive wiring subsystem for role-aware field redaction. */
public class RoleAwareDirectiveTest {

    @Test
    public void redactsProtectedFieldForGuestAndRevealsItForStaff() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult guestResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("""
                        query ProductDetails {
                          product {
                            name
                            internalCode
                          }
                        }
                        """)
                .graphQLContext(builder -> builder.of("role", "guest"))
                .build());
        ExecutionResult staffResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query("""
                        query ProductDetails {
                          product {
                            name
                            internalCode
                          }
                        }
                        """)
                .graphQLContext(builder -> builder.of("role", "staff"))
                .build());

        assertTrue(guestResult.getErrors().isEmpty(), () -> guestResult.getErrors().toString());
        assertTrue(staffResult.getErrors().isEmpty(), () -> staffResult.getErrors().toString());
        assertEquals(Map.of("product", Map.of("name", "Desk Lamp", "internalCode", "REDACTED")),
                guestResult.getData());
        assertEquals(Map.of("product", Map.of("name", "Desk Lamp", "internalCode", "DL-42")),
                staffResult.getData());
    }

    private static GraphQL buildGraphQL() {
        String schema = """
                directive @staffOnly on FIELD_DEFINITION
                type Query {
                  product: Product!
                }
                type Product {
                  name: String!
                  internalCode: String! @staffOnly
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        Product product = new Product("Desk Lamp", "DL-42");
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("product", environment -> product))
                .type("Product", type -> type
                        .dataFetcher("name", environment -> product.name())
                        .dataFetcher("internalCode", environment -> product.internalCode()))
                .directive("staffOnly", new StaffOnlyDirective())
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private record Product(String name, String internalCode) {
    }

    private static final class StaffOnlyDirective implements SchemaDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(SchemaDirectiveWiringEnvironment<GraphQLFieldDefinition> environment) {
            environment.setFieldDataFetcher(DataFetcherFactories.wrapDataFetcher(
                    environment.getFieldDataFetcher(),
                    (dataFetchingEnvironment, value) -> isStaff(dataFetchingEnvironment)
                            ? value
                            : "REDACTED"));
            return environment.getElement();
        }

        private static boolean isStaff(DataFetchingEnvironment environment) {
            String role = environment.getGraphQlContext().getOrDefault("role", "guest");
            return "staff".equals(role.toLowerCase(Locale.ROOT));
        }
    }
}
