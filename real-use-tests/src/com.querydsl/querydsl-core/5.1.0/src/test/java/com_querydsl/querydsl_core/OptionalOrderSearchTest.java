/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.BooleanBuilder;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's BooleanBuilder subsystem to assemble an optional order search.
public class OptionalOrderSearchTest {
    @Test
    @Timeout(60)
    public void searchesWithOnlyTheCriteriaTheUserSupplied() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_optional_order_search;DB_CLOSE_DELAY=-1", "sa", "")) {
            createOrdersTable(connection);
            insertOrders(connection);

            Orders orders = new Orders();
            BooleanBuilder criteria = new BooleanBuilder();
            criteria.and(orders.status.eq("READY"));
            criteria.and(orders.total.goe(new BigDecimal("100.00")));

            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<Tuple> matchingOrders = queryFactory
                    .select(orders.customer, orders.total)
                    .from(orders)
                    .where(criteria)
                    .orderBy(orders.customer.asc())
                    .fetch();

            assertEquals(2, matchingOrders.size());
            assertEquals("Acme", matchingOrders.get(0).get(orders.customer));
            assertEquals(0, matchingOrders.get(0).get(orders.total).compareTo(new BigDecimal("275.50")));
            assertEquals("Beacon", matchingOrders.get(1).get(orders.customer));
            assertEquals(0, matchingOrders.get(1).get(orders.total).compareTo(new BigDecimal("125.00")));
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
}
