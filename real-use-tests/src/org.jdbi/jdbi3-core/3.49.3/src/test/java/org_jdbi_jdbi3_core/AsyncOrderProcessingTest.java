/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.async.JdbiExecutor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Jdbi's asynchronous executor subsystem for processing order updates.
public class AsyncOrderProcessingTest {
    @Test
    void processesAnOrderWithoutBlockingTheCallingThread() throws Exception {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:async_orders;DB_CLOSE_DELAY=-1");
        jdbi.useHandle(handle -> handle.execute("""
                CREATE TABLE order_status (
                    order_id VARCHAR(32) PRIMARY KEY,
                    status VARCHAR(32) NOT NULL
                )
                """));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            JdbiExecutor async = JdbiExecutor.create(jdbi, executor);
            CompletionStage<List<Map<String, Object>>> processed = async.inTransaction(handle -> {
                handle.createUpdate("""
                        INSERT INTO order_status (order_id, status)
                        VALUES (:orderId, :status)
                        """)
                        .bind("orderId", "ORD-1042")
                        .bind("status", "READY")
                        .execute();
                return handle.createQuery("""
                        SELECT order_id, status
                        FROM order_status
                        WHERE order_id = :orderId
                        """)
                        .bind("orderId", "ORD-1042")
                        .mapToMap()
                        .list();
            }).thenCompose(insideTransaction -> async.withHandle(handle ->
                    handle.createQuery("""
                            SELECT order_id, status
                            FROM order_status
                            WHERE order_id = :orderId
                            """)
                            .bind("orderId", "ORD-1042")
                            .mapToMap()
                            .list()));

            assertEquals(List.of(Map.of("order_id", "ORD-1042", "status", "READY")),
                    processed.toCompletableFuture().get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
