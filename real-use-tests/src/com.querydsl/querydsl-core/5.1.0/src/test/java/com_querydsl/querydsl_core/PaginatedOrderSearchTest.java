/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQueryFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's paginated result subsystem to serve a stable order-search page.
public class PaginatedOrderSearchTest {
    @Test
    @Timeout(60)
    public void returnsPageItemsAndTheTotalMatchingOrderCount() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_paginated_orders;DB_CLOSE_DELAY=-1", "sa", "")) {
            createOrdersTable(connection);
            insertOrders(connection);

            Orders orders = new Orders();
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            QueryResults<Tuple> page = queryFactory
                    .select(orders.customer, orders.total)
                    .from(orders)
                    .where(orders.status.eq("READY"))
                    .orderBy(orders.total.desc())
                    .limit(2)
                    .offset(1)
                    .fetchResults();

            assertEquals(2, page.getResults().size());
            assertEquals("Beacon", page.getResults().get(0).get(orders.customer));
            assertEquals(0, page.getResults().get(0).get(orders.total)
                    .compareTo(new BigDecimal("125.00")));
            assertEquals("Echo", page.getResults().get(1).get(orders.customer));
            assertEquals(0, page.getResults().get(1).get(orders.total)
                    .compareTo(new BigDecimal("95.00")));
            assertEquals(4, page.getTotal());
            assertEquals(2, page.getLimit());
            assertEquals(1, page.getOffset());
        }
    }

    private static void createOrdersTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    customer VARCHAR(100) NOT NULL,
                    total DECIMAL(10, 2) NOT NULL,
                    status VARCHAR(20) NOT NULL
                )
                """);
    }

    private static void insertOrders(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO orders (id, customer, total, status) VALUES
                    (1, 'Acme', 275.50, 'READY'),
                    (2, 'Beacon', 125.00, 'READY'),
                    (3, 'Cobalt', 410.00, 'HELD'),
                    (4, 'Delta', 80.00, 'READY'),
                    (5, 'Echo', 95.00, 'READY')
                """);
    }

    private static final class Orders extends RelationalPathBase<OrderRow> {
        private final StringPath customer = createString("customer");
        private final NumberPath<BigDecimal> total = createNumber("total", BigDecimal.class);
        private final StringPath status = createString("status");

        private Orders() {
            super(OrderRow.class, "order", null, "orders");
        }
    }

    private static final class OrderRow {
    }
}
