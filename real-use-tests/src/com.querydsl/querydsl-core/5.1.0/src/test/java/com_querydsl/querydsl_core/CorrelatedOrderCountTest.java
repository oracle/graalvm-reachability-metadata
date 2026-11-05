/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLExpressions;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLQueryFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's correlated subquery subsystem to show each customer's order count.
public class CorrelatedOrderCountTest {
    @Test
    @Timeout(60)
    public void showsCustomersWithTheirOrderCounts() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_correlated_order_counts;DB_CLOSE_DELAY=-1", "sa", "")) {
            createTables(connection);
            insertData(connection);

            Customers customers = new Customers();
            Orders orders = new Orders();
            SQLQuery<Long> orderCount = SQLExpressions
                    .select(orders.id.count())
                    .from(orders)
                    .where(orders.customerId.eq(customers.id));
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<Tuple> customerSummaries = queryFactory
                    .select(customers.name, orderCount.as("order_count"))
                    .from(customers)
                    .orderBy(customers.id.asc())
                    .fetch();

            assertEquals(3, customerSummaries.size());
            assertEquals("Acme", customerSummaries.get(0).get(customers.name));
            assertEquals(2L, customerSummaries.get(0).get(orderCount.as("order_count")));
            assertEquals("Beacon", customerSummaries.get(1).get(customers.name));
            assertEquals(1L, customerSummaries.get(1).get(orderCount.as("order_count")));
            assertEquals("Cobalt", customerSummaries.get(2).get(customers.name));
            assertEquals(0L, customerSummaries.get(2).get(orderCount.as("order_count")));
        }
    }

    private static void createTables(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE customers (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                )
                """);
        connection.createStatement().executeUpdate("""
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    customer_id BIGINT NOT NULL,
                    total INTEGER NOT NULL
                )
                """);
    }

    private static void insertData(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO customers (id, name) VALUES
                    (1, 'Acme'),
                    (2, 'Beacon'),
                    (3, 'Cobalt')
                """);
        connection.createStatement().executeUpdate("""
                INSERT INTO orders (id, customer_id, total) VALUES
                    (101, 1, 125),
                    (102, 1, 80),
                    (201, 2, 210)
                """);
    }

    private static final class Customers extends RelationalPathBase<CustomerRow> {
        private final NumberPath<Long> id = createNumber("id", Long.class);
        private final StringPath name = createString("name");

        private Customers() {
            super(CustomerRow.class, "customer", null, "customers");
        }
    }

    private static final class Orders extends RelationalPathBase<OrderRow> {
        private final NumberPath<Long> id = createNumber("id", Long.class);
        private final NumberPath<Long> customerId = createNumber("customer_id", Long.class);
        private final NumberPath<Integer> total = createNumber("total", Integer.class);

        private Orders() {
            super(OrderRow.class, "order", null, "orders");
        }
    }

    private static final class CustomerRow {
    }

    private static final class OrderRow {
    }
}
