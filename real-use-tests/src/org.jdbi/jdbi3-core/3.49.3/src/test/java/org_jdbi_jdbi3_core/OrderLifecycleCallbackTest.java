/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Jdbi's transaction callback subsystem for publishing order lifecycle events.
public class OrderLifecycleCallbackTest {
    @Test
    void publishesEventsOnlyAfterTransactionOutcome() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:order_lifecycle_callbacks");
        List<String> events = new ArrayList<>();

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE order_status (
                        order_id VARCHAR(32) PRIMARY KEY,
                        status VARCHAR(32) NOT NULL
                    )
                    """);

            handle.useTransaction(transaction -> {
                insertOrder(transaction, "ORD-1042", "READY");
                transaction.afterCommit(() -> events.add("committed:ORD-1042"));
                transaction.afterRollback(() -> events.add("rolled-back:ORD-1042"));
            });

            assertEquals(List.of("committed:ORD-1042"), events);
            assertEquals(List.of("ORD-1042"), orderIds(handle));

            assertThrows(IllegalStateException.class, () -> handle.useTransaction(transaction -> {
                insertOrder(transaction, "ORD-1043", "CANCELLED");
                transaction.afterCommit(() -> events.add("committed:ORD-1043"));
                transaction.afterRollback(() -> events.add("rolled-back:ORD-1043"));
                throw new IllegalStateException("payment was declined");
            }));

            assertEquals(List.of("committed:ORD-1042", "rolled-back:ORD-1043"), events);
            assertEquals(List.of("ORD-1042"), orderIds(handle));
        });
    }

    private static void insertOrder(Handle handle, String orderId, String status) {
        handle.createUpdate("""
                INSERT INTO order_status (order_id, status)
                VALUES (:orderId, :status)
                """)
                .bind("orderId", orderId)
                .bind("status", status)
                .execute();
    }

    private static List<String> orderIds(Handle handle) {
        return handle.createQuery("""
                SELECT order_id
                FROM order_status
                ORDER BY order_id
                """)
                .mapTo(String.class)
                .list();
    }
}
