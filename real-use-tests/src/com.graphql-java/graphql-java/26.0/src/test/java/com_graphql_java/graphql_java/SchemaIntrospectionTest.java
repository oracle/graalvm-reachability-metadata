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

/** Exercises GraphQL Java's schema introspection subsystem for client capability discovery. */
public class SchemaIntrospectionTest {

    @Test
    public void describesQueryAndProductTypesForAClient() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult result = graphQL.execute("""
                query SchemaMetadata {
                  __schema {
                    queryType {
                      name
                    }
                    types {
                      kind
                      name
                      fields {
                        name
                      }
                    }
                  }
                }
                """);

        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
        Map<?, ?> schema = (Map<?, ?>) ((Map<?, ?>) result.getData()).get("__schema");
        assertEquals("Query", ((Map<?, ?>) schema.get("queryType")).get("name"));

        Map<?, ?> queryType = typeNamed(schema, "Query");
        assertEquals("OBJECT", queryType.get("kind"));
        assertEquals(List.of("product"), fieldNames(queryType));

        Map<?, ?> productType = typeNamed(schema, "Product");
        assertEquals("OBJECT", productType.get("kind"));
        assertEquals(List.of("id", "name", "price"), fieldNames(productType));
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  product: Product!
                }
                type Product {
                  id: ID!
                  name: String!
                  price: Float!
                }
                """);
        Product product = new Product("product-7", "Desk Lamp", 29.99);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("product", environment -> product))
                .type("Product", type -> type
                        .dataFetcher("id", environment -> product.id())
                        .dataFetcher("name", environment -> product.name())
                        .dataFetcher("price", environment -> product.price()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private static Map<?, ?> typeNamed(Map<?, ?> schema, String name) {
        return ((List<Map<?, ?>>) schema.get("types")).stream()
                .filter(type -> name.equals(type.get("name")))
                .findFirst()
                .orElseThrow();
    }

    private static List<?> fieldNames(Map<?, ?> type) {
        return ((List<Map<?, ?>>) type.get("fields")).stream()
                .map(field -> field.get("name"))
                .toList();
    }

    private record Product(String id, String name, double price) {
    }
}
