/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.types.Projections;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's DTO projection subsystem to build order summaries for an application response.
public class OrderSummaryProjectionTest {
    @Test
    @Timeout(60)
    public void projectsMatchingOrdersIntoApplicationDtos() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_order_summaries;DB_CLOSE_DELAY=-1", "sa", "")) {
            createOrdersTable(connection);
            insertOrders(connection);

            Orders orders = new Orders();
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<OrderSummary> summaries = queryFactory
                    .select(Projections.constructor(
                            OrderSummary.class, orders.customer, orders.total))
                    .from(orders)
                    .where(orders.status.eq("READY")
                            .and(orders.total.goe(new BigDecimal("100.00"))))
                    .orderBy(orders.total.desc())
                    .fetch();

            assertEquals(List.of(
                    new OrderSummary("Acme", new BigDecimal("275.50")),
                    new OrderSummary("Beacon", new BigDecimal("125.00"))), summaries);
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
                    (4, 'Delta', 80.00, 'READY')
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

    public static final class OrderSummary {
        private final String customer;
        private final BigDecimal total;

        public OrderSummary(String customer, BigDecimal total) {
            this.customer = customer;
            this.total = total;
        }

        public String getCustomer() {
            return customer;
        }

        public BigDecimal getTotal() {
            return total;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof OrderSummary summary)) {
                return false;
            }
            return customer.equals(summary.customer) && total.compareTo(summary.total) == 0;
        }

        @Override
        public int hashCode() {
            return 31 * customer.hashCode() + total.stripTrailingZeros().hashCode();
        }
    }
}
