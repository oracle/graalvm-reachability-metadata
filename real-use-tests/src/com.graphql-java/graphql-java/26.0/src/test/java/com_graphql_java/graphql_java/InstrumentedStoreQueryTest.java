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
import graphql.execution.instrumentation.InstrumentationState;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's field instrumentation subsystem for an application query. */
public class InstrumentedStoreQueryTest {

    @Test
    public void recordsFieldsFetchedWhileLoadingStoreSummary() throws Exception {
        Store store = new Store();
        FieldAudit audit = new FieldAudit();
        GraphQL graphQL = buildGraphQL(store, audit);

        ExecutionInput input = ExecutionInput.newExecutionInput()
                .query("""
                        query StoreSummary {
                          store {
                            name
                            productCount
                          }
                        }
                        """)
                .build();
        ExecutionResult result = graphQL.executeAsync(input).get(30, TimeUnit.SECONDS);

        assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
        assertEquals(Map.of("store", Map.of("name", "Northwind", "productCount", 42)), result.getData());
        assertEquals(List.of("store", "name", "productCount"), audit.fields);
    }

    private static GraphQL buildGraphQL(Store store, FieldAudit audit) {
        String schema = """
                type Query {
                  store: Store!
                }
                type Store {
                  name: String!
                  productCount: Int!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("store", environment -> store))
                .type("Store", type -> type
                        .dataFetcher("name", environment -> store.name)
                        .dataFetcher("productCount", environment -> store.productCount))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema)
                .instrumentation(new StoreInstrumentation(audit))
                .build();
    }

    private static final class StoreInstrumentation extends SimpleInstrumentation {
        private final FieldAudit audit;

        private StoreInstrumentation(FieldAudit audit) {
            this.audit = audit;
        }

        @Override
        public DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher,
                InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
            return environment -> {
                audit.fields.add(environment.getField().getName());
                return dataFetcher.get(environment);
            };
        }
    }

    private static final class FieldAudit {
        private final List<String> fields = new ArrayList<>();
    }

    private static final class Store {
        private final String name = "Northwind";
        private final int productCount = 42;
    }
}
