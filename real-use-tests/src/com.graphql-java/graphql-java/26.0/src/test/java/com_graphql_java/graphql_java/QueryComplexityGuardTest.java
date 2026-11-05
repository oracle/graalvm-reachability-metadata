/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.analysis.FieldComplexityCalculator;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's query-complexity analysis subsystem for a catalog API. */
public class QueryComplexityGuardTest {

    @Test
    public void acceptsAStandardCatalogRequestAndRejectsAnExpensiveSearch() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult standardResult = graphQL.execute("""
                query CatalogCard {
                  catalog {
                    name
                  }
                }
                """);

        assertTrue(standardResult.getErrors().isEmpty(), () -> standardResult.getErrors().toString());
        assertEquals(Map.of("catalog", Map.of("name", "Northwind")), standardResult.getData());

        ExecutionResult expensiveResult = graphQL.execute("""
                query CatalogSearch {
                  catalog {
                    search
                  }
                }
                """);

        assertFalse(expensiveResult.getErrors().isEmpty());
        assertTrue(expensiveResult.getErrors().getFirst().getMessage().contains("maximum query complexity"),
                () -> expensiveResult.getErrors().toString());
        assertNull(expensiveResult.getData());
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  catalog: Catalog!
                }
                type Catalog {
                  name: String!
                  search: String!
                }
                """);
        Catalog catalog = new Catalog();
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("catalog", environment -> catalog))
                .type("Catalog", type -> type
                        .dataFetcher("name", environment -> catalog.name())
                        .dataFetcher("search", environment -> catalog.search()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        FieldComplexityCalculator calculator = (environment, childComplexity) ->
                "search".equals(environment.getField().getName())
                        ? 5 + childComplexity
                        : 1 + childComplexity;
        return GraphQL.newGraphQL(schema)
                .instrumentation(new MaxQueryComplexityInstrumentation(4, calculator))
                .build();
    }

    private record Catalog(String name, String search) {
        private Catalog() {
            this("Northwind", "Search results");
        }
    }
}
