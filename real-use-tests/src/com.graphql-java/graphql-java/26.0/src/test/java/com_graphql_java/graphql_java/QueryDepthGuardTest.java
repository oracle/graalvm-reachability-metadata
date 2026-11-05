/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's query-depth analysis subsystem to protect a catalog API. */
public class QueryDepthGuardTest {

    @Test
    public void acceptsNormalCatalogQueryAndRejectsOverlyNestedQuery() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult normalResult = graphQL.execute("""
                query CatalogCard {
                  catalog {
                    name
                  }
                }
                """);

        assertTrue(normalResult.getErrors().isEmpty(), () -> normalResult.getErrors().toString());
        assertEquals(Map.of("catalog", Map.of("name", "Northwind")), normalResult.getData());

        ExecutionResult nestedResult = graphQL.execute("""
                query CatalogDetails {
                  catalog {
                    featured {
                      supplier {
                        name
                      }
                    }
                  }
                }
                """);

        assertFalse(nestedResult.getErrors().isEmpty());
        assertTrue(nestedResult.getErrors().getFirst().getMessage().contains("maximum query depth"),
                () -> nestedResult.getErrors().toString());
    }

    private static GraphQL buildGraphQL() {
        String schema = """
                type Query {
                  catalog: Catalog!
                }
                type Catalog {
                  name: String!
                  featured: Product!
                }
                type Product {
                  supplier: Supplier!
                }
                type Supplier {
                  name: String!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        Catalog catalog = new Catalog();
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("catalog", environment -> catalog))
                .type("Catalog", type -> type
                        .dataFetcher("name", environment -> catalog.name())
                        .dataFetcher("featured", environment -> catalog.featured()))
                .type("Product", type -> type.dataFetcher("supplier", environment -> catalog.featured().supplier()))
                .type("Supplier", type -> type.dataFetcher("name", environment -> catalog.featured().supplier().name()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema)
                .instrumentation(new MaxQueryDepthInstrumentation(2))
                .build();
    }

    private record Catalog(String name, Product featured) {
        private Catalog() {
            this("Northwind", new Product(new Supplier("Acme")));
        }
    }

    private record Product(Supplier supplier) {
    }

    private record Supplier(String name) {
    }
}
