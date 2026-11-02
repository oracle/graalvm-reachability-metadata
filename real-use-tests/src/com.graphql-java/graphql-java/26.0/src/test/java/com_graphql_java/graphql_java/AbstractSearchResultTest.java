/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's abstract type resolution subsystem for a product search API. */
public class AbstractSearchResultTest {

    @Test
    public void resolvesDifferentSearchResultTypesAndReturnsTheirSpecificFields() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult result = graphQL.execute("""
                query ProductSearch {
                  search {
                    id
                    label
                    ... on Product {
                      price
                    }
                    ... on Brand {
                      category
                    }
                  }
                }
                """);

        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
        assertEquals(Map.of("search", List.of(
                Map.of("id", "product-1", "label", "Desk Lamp", "price", 29.99),
                Map.of("id", "brand-1", "label", "Luma Home", "category", "Lighting"))),
                result.getData());
    }

    private static GraphQL buildGraphQL() {
        String schema = """
                interface SearchResult {
                  id: ID!
                  label: String!
                }
                type Product implements SearchResult {
                  id: ID!
                  label: String!
                  price: Float!
                }
                type Brand implements SearchResult {
                  id: ID!
                  label: String!
                  category: String!
                }
                type Query {
                  search: [SearchResult!]!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        SearchCatalog catalog = new SearchCatalog();
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("search", environment -> catalog.results()))
                .type("SearchResult", type -> type.typeResolver(environment -> {
                    Object result = environment.getObject();
                    if (result instanceof Product) {
                        return environment.getSchema().getObjectType("Product");
                    }
                    if (result instanceof Brand) {
                        return environment.getSchema().getObjectType("Brand");
                    }
                    return null;
                }))
                .type("Product", type -> type
                        .dataFetcher("id", environment -> ((Product) environment.getSource()).id())
                        .dataFetcher("label", environment -> ((Product) environment.getSource()).label())
                        .dataFetcher("price", environment -> ((Product) environment.getSource()).price()))
                .type("Brand", type -> type
                        .dataFetcher("id", environment -> ((Brand) environment.getSource()).id())
                        .dataFetcher("label", environment -> ((Brand) environment.getSource()).label())
                        .dataFetcher("category", environment -> ((Brand) environment.getSource()).category()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private record Product(String id, String label, double price) {
    }

    private record Brand(String id, String label, String category) {
    }

    private static final class SearchCatalog {
        private List<Object> results() {
            return List.of(
                    new Product("product-1", "Desk Lamp", 29.99),
                    new Brand("brand-1", "Luma Home", "Lighting"));
        }
    }
}
