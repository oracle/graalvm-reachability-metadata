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
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's conditional field execution subsystem for a product offer API. */
public class ConditionalOfferQueryTest {

    @Test
    public void includesOptionalOfferDetailsOnlyForTheRequestedAudience() {
        GraphQL graphQL = buildGraphQL();
        String query = """
                query ProductOffer($showPrice: Boolean!, $hideInternal: Boolean!) {
                  product {
                    name
                    price @include(if: $showPrice)
                    internalCode @skip(if: $hideInternal)
                  }
                }
                """;

        ExecutionResult customerResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .variables(Map.of("showPrice", true, "hideInternal", true))
                .build());
        ExecutionResult staffResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(query)
                .variables(Map.of("showPrice", false, "hideInternal", false))
                .build());

        assertTrue(customerResult.getErrors().isEmpty(), () -> customerResult.getErrors().toString());
        assertTrue(staffResult.getErrors().isEmpty(), () -> staffResult.getErrors().toString());
        assertEquals(Map.of("product", Map.of(
                "name", "Desk Lamp", "price", 29.99)), customerResult.getData());
        assertEquals(Map.of("product", Map.of(
                "name", "Desk Lamp", "internalCode", "DL-42")), staffResult.getData());
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  product: Product!
                }
                type Product {
                  name: String!
                  price: Float!
                  internalCode: String!
                }
                """);
        Product product = new Product("Desk Lamp", 29.99, "DL-42");
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("product", environment -> product))
                .type("Product", type -> type
                        .dataFetcher("name", environment -> product.name())
                        .dataFetcher("price", environment -> product.price())
                        .dataFetcher("internalCode", environment -> product.internalCode()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private record Product(String name, double price, String internalCode) {
    }
}
