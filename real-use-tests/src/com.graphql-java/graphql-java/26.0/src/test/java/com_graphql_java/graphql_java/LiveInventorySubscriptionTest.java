/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_graphql_java.graphql_java;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.reactive.SingleSubscriberPublisher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises GraphQL Java's subscription execution subsystem for live inventory updates. */
public class LiveInventorySubscriptionTest {

    @Test
    public void deliversInventoryUpdatesToASubscription() throws Exception {
        SingleSubscriberPublisher<InventoryUpdate> inventoryUpdates = new SingleSubscriberPublisher<>();
        GraphQL graphQL = buildGraphQL(inventoryUpdates);

        ExecutionResult initialResult = graphQL.execute("""
                subscription InventoryUpdates {
                  inventory {
                    sku
                    available
                  }
                }
                """);

        assertTrue(initialResult.getErrors().isEmpty(), () -> initialResult.getErrors().toString());
        EventCollector collector = new EventCollector();
        Publisher<ExecutionResult> resultPublisher = initialResult.getData();
        resultPublisher.subscribe(collector);

        inventoryUpdates.offer(new InventoryUpdate("SKU-7", 12));
        inventoryUpdates.offer(new InventoryUpdate("SKU-7", 9));
        inventoryUpdates.noMoreData();

        assertTrue(collector.completed.await(30, TimeUnit.SECONDS), "subscription did not complete");
        assertTrue(collector.failure == null, () -> String.valueOf(collector.failure));
        assertEquals(List.of(
                Map.of("inventory", Map.of("sku", "SKU-7", "available", 12)),
                Map.of("inventory", Map.of("sku", "SKU-7", "available", 9))), collector.events);
    }

    private static GraphQL buildGraphQL(Publisher<InventoryUpdate> inventoryUpdates) {
        String schema = """
                type Query {
                  health: String!
                }
                type Subscription {
                  inventory: InventoryUpdate!
                }
                type InventoryUpdate {
                  sku: ID!
                  available: Int!
                }
                """;
        TypeDefinitionRegistry registry = new SchemaParser().parse(schema);
        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", type -> type.dataFetcher("health", environment -> "ok"))
                .type("Subscription", type -> type.dataFetcher("inventory", environment -> inventoryUpdates))
                .type("InventoryUpdate", type -> type
                        .dataFetcher("sku", environment -> ((InventoryUpdate) environment.getSource()).sku())
                        .dataFetcher("available", environment ->
                                ((InventoryUpdate) environment.getSource()).available()))
                .build();
        GraphQLSchema executableSchema = new SchemaGenerator().makeExecutableSchema(registry, wiring);
        return GraphQL.newGraphQL(executableSchema).build();
    }

    private record InventoryUpdate(String sku, int available) {
    }

    private static final class EventCollector implements Subscriber<ExecutionResult> {
        private final CountDownLatch completed = new CountDownLatch(1);
        private final List<Map<?, ?>> events = new ArrayList<>();
        private Throwable failure;

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ExecutionResult result) {
            if (!result.getErrors().isEmpty()) {
                failure = new AssertionError(result.getErrors().toString());
            } else {
                events.add(result.getData());
            }
        }

        @Override
        public void onError(Throwable throwable) {
            failure = throwable;
            completed.countDown();
        }

        @Override
        public void onComplete() {
            completed.countDown();
        }
    }
}
