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
import graphql.execution.preparsed.persisted.ApolloPersistedQuerySupport;
import graphql.execution.preparsed.persisted.InMemoryPersistedQueryCache;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's persisted-query subsystem for an allowlisted catalog API. */
public class PersistedCatalogQueryTest {

    private static final String CATALOG_QUERY = """
            query CatalogSummary {
              catalog {
                name
                itemCount
              }
            }
            """;
    private static final String CATALOG_QUERY_HASH =
            "144bc8d6cbb9af93cdf9d8106b0ccca6c72dc651fdd46e62a4f8e87b947fd973";

    @Test
    public void executesKnownPersistedQueryAndRejectsUnknownQuery() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult knownQueryResult = graphQL.execute(persistedQueryInput(CATALOG_QUERY_HASH));
        assertTrue(knownQueryResult.getErrors().isEmpty(), () -> knownQueryResult.getErrors().toString());
        assertEquals(Map.of("catalog", Map.of("name", "Northwind", "itemCount", 42)),
                knownQueryResult.getData());

        ExecutionResult unknownQueryResult = graphQL.execute(persistedQueryInput("unknown-query-hash"));
        assertTrue(unknownQueryResult.getData() == null);
        assertEquals("PersistedQueryNotFound", unknownQueryResult.getErrors().getFirst().getErrorType().toString());
    }

    private static ExecutionInput persistedQueryInput(String queryHash) {
        return ExecutionInput.newExecutionInput()
                .query(ExecutionInput.PERSISTED_QUERY_MARKER)
                .extensions(Map.of("persistedQuery", Map.of("version", 1, "sha256Hash", queryHash)))
                .build();
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  catalog: Catalog!
                }
                type Catalog {
                  name: String!
                  itemCount: Int!
                }
                """);
        Catalog catalog = new Catalog("Northwind", 42);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("catalog", environment -> catalog))
                .type("Catalog", type -> type
                        .dataFetcher("name", environment -> catalog.name())
                        .dataFetcher("itemCount", environment -> catalog.itemCount()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        InMemoryPersistedQueryCache cache = InMemoryPersistedQueryCache.newInMemoryPersistedQueryCache()
                .addQuery(CATALOG_QUERY_HASH, CATALOG_QUERY)
                .build();
        return GraphQL.newGraphQL(schema)
                .preparsedDocumentProvider(new ApolloPersistedQuerySupport(cache))
                .build();
    }

    private record Catalog(String name, int itemCount) {
    }
}
