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
import graphql.execution.ResultPath;
import graphql.execution.instrumentation.fieldvalidation.FieldAndArguments;
import graphql.execution.instrumentation.fieldvalidation.FieldValidationEnvironment;
import graphql.execution.instrumentation.fieldvalidation.FieldValidationInstrumentation;
import graphql.execution.instrumentation.fieldvalidation.SimpleFieldValidation;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's field argument validation subsystem for a catalog search API. */
public class SearchInputValidationTest {

    @Test
    public void acceptsBoundedSearchAndRejectsAnOverlyLargePageBeforeFetching() {
        SearchCatalog catalog = new SearchCatalog();
        GraphQL graphQL = buildGraphQL(catalog);
        String query = """
                query SearchCatalog($term: String!, $limit: Int!) {
                  search(term: $term, limit: $limit) {
                    id
                    title
                  }
                }
                """;

        ExecutionResult validResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .variables(Map.of("term", "lamp", "limit", 2))
                .build());

        assertTrue(validResult.getErrors().isEmpty(), () -> validResult.getErrors().toString());
        assertEquals(Map.of("search", java.util.List.of(
                Map.of("id", "product-1", "title", "Desk Lamp"))), validResult.getData());

        ExecutionResult invalidResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .variables(Map.of("term", "lamp", "limit", 101))
                .build());

        assertEquals(1, invalidResult.getErrors().size());
        assertEquals("Search limit must be at most 50", invalidResult.getErrors().getFirst().getMessage());
        assertEquals(1, catalog.fetchCount);
    }

    private static GraphQL buildGraphQL(SearchCatalog catalog) {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  search(term: String!, limit: Int!): [Product!]!
                }
                type Product {
                  id: ID!
                  title: String!
                }
                """);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("search", environment -> {
                    catalog.fetchCount++;
                    return catalog.search(environment.getArgument("term"), environment.getArgument("limit"));
                }))
                .type("Product", type -> type
                        .dataFetcher("id", environment -> ((Product) environment.getSource()).id())
                        .dataFetcher("title", environment -> ((Product) environment.getSource()).title()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        SimpleFieldValidation validation = new SimpleFieldValidation()
                .addRule(ResultPath.rootPath().segment("search"), SearchInputValidationTest::validateSearch);
        return GraphQL.newGraphQL(schema)
                .instrumentation(new FieldValidationInstrumentation(validation))
                .build();
    }

    private static Optional<graphql.GraphQLError> validateSearch(
            FieldAndArguments field, FieldValidationEnvironment environment) {
        Integer limit = field.getArgumentValue("limit");
        if (limit != null && limit > 50) {
            return Optional.of(environment.mkError("Search limit must be at most 50", field));
        }
        return Optional.empty();
    }

    private record Product(String id, String title) {
    }

    private static final class SearchCatalog {
        private int fetchCount;

        private java.util.List<Product> search(String term, int limit) {
            if ("lamp".equals(term) && limit == 2) {
                return java.util.List.of(new Product("product-1", "Desk Lamp"));
            }
            return java.util.List.of();
        }
    }
}
