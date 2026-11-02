/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises GraphQL Java's data-fetcher result propagation subsystem for checkout details. */
public class DataFetcherResultCheckoutTest {

    @Test
    public void returnsPartialCheckoutDataAndAFieldErrorWithApplicationContext() {
        GraphQL graphQL = buildGraphQL();

        ExecutionResult result = graphQL.execute("""
                query Checkout {
                  checkout {
                    orderId
                    auditLabel
                    discountCode
                  }
                }
                """);

        Map<String, Object> expectedCheckout = new LinkedHashMap<>();
        expectedCheckout.put("orderId", "ORDER-17");
        expectedCheckout.put("auditLabel", "checkout-request-17");
        expectedCheckout.put("discountCode", null);
        assertEquals(Map.of("checkout", expectedCheckout), result.getData());
        assertEquals(1, result.getErrors().size());
        GraphQLError error = result.getErrors().getFirst();
        assertEquals("Discount code is no longer available", error.getMessage());
        assertEquals(List.of("checkout", "discountCode"), error.getPath());
        assertEquals(ErrorType.DataFetchingException, error.getErrorType());
        assertEquals(Map.of("reason", "promotion-expired"), error.getExtensions());
    }

    private static GraphQL buildGraphQL() {
        TypeDefinitionRegistry registry = new SchemaParser().parse("""
                type Query {
                  checkout: Checkout
                }
                type Checkout {
                  orderId: ID!
                  auditLabel: String!
                  discountCode: String
                }
                """);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("checkout", environment ->
                        DataFetcherResult.newResult(new Checkout("ORDER-17"))
                                .localContext("checkout-request-17")
                                .build()))
                .type("Checkout", type -> type
                        .dataFetcher("orderId", environment -> ((Checkout) environment.getSource()).orderId())
                        .dataFetcher("auditLabel", environment -> environment.getLocalContext())
                        .dataFetcher("discountCode", environment -> DataFetcherResult.newResult()
                                .error(GraphqlErrorBuilder.newError(environment)
                                        .message("Discount code is no longer available")
                                        .errorType(ErrorType.DataFetchingException)
                                        .extensions(Map.of("reason", "promotion-expired"))
                                        .build())
                                .build()))
                .build();
        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private record Checkout(String orderId) {
    }
}
