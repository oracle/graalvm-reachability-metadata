/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.enums.EnumStrategy;
import org.jdbi.v3.core.enums.Enums;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's enum argument and column-mapping subsystem for order statuses.
public class OrderStatusEnumTest {
    @Test
    void storesAndReadsOrderStatusesByName() {
        Jdbi jdbi = Jdbi.create("jdbc:h2:mem:order_statuses");
        jdbi.configure(Enums.class, enums -> enums.setEnumStrategy(EnumStrategy.BY_NAME));

        jdbi.useHandle(handle -> {
            handle.execute("""
                    CREATE TABLE order_status (
                        order_number VARCHAR(32) PRIMARY KEY,
                        status VARCHAR(32) NOT NULL
                    )
                    """);

            handle.createUpdate("""
                    INSERT INTO order_status (order_number, status)
                    VALUES (:orderNumber, :status)
                    """)
                    .bind("orderNumber", "ORD-1042")
                    .bind("status", OrderStatus.READY)
                    .execute();
            handle.createUpdate("""
                    INSERT INTO order_status (order_number, status)
                    VALUES (:orderNumber, :status)
                    """)
                    .bind("orderNumber", "ORD-1043")
                    .bind("status", OrderStatus.SHIPPED)
                    .execute();

            assertEquals(List.of(OrderStatus.READY, OrderStatus.SHIPPED),
                    handle.createQuery("""
                            SELECT status
                            FROM order_status
                            ORDER BY order_number
                            """)
                            .mapTo(OrderStatus.class)
                            .list());
        });
    }

    private enum OrderStatus {
        READY,
        SHIPPED
    }
}
