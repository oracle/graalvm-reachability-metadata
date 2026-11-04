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
import com.querydsl.sql.SQLQueryFactory;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's relational join subsystem to build a customer's order history.
public class CustomerOrderJoinTest {
    @Test
    @Timeout(60)
    public void joinsCustomerAndOrderDataForTheOrderHistoryPage() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_customer_orders;DB_CLOSE_DELAY=-1", "sa", "")) {
            createTables(connection);
            insertData(connection);

            Customers customers = new Customers();
            Orders orders = new Orders();
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<Tuple> orderHistory = queryFactory
                    .select(customers.name, orders.orderNumber, orders.total)
                    .from(orders)
                    .innerJoin(customers)
                    .on(orders.customerId.eq(customers.id))
                    .where(orders.status.eq("READY"))
                    .orderBy(customers.name.asc(), orders.orderNumber.asc())
                    .fetch();

            assertEquals(3, orderHistory.size());
            assertEquals("Acme", orderHistory.get(0).get(customers.name));
            assertEquals("A-100", orderHistory.get(0).get(orders.orderNumber));
            assertEquals(0, orderHistory.get(0).get(orders.total)
                    .compareTo(new BigDecimal("125.00")));
            assertEquals("Acme", orderHistory.get(1).get(customers.name));
            assertEquals("A-101", orderHistory.get(1).get(orders.orderNumber));
            assertEquals(0, orderHistory.get(1).get(orders.total)
                    .compareTo(new BigDecimal("80.00")));
            assertEquals("Beacon", orderHistory.get(2).get(customers.name));
            assertEquals("B-200", orderHistory.get(2).get(orders.orderNumber));
            assertEquals(0, orderHistory.get(2).get(orders.total)
                    .compareTo(new BigDecimal("210.00")));
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
                    order_number VARCHAR(20) NOT NULL,
                    total DECIMAL(10, 2) NOT NULL,
                    status VARCHAR(20) NOT NULL
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
                INSERT INTO orders (id, customer_id, order_number, total, status) VALUES
                    (1, 1, 'A-100', 125.00, 'READY'),
                    (2, 1, 'A-101', 80.00, 'READY'),
                    (3, 2, 'B-200', 210.00, 'READY'),
                    (4, 3, 'C-300', 450.00, 'HELD')
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
        private final NumberPath<Long> customerId = createNumber("customer_id", Long.class);
        private final StringPath orderNumber = createString("order_number");
        private final NumberPath<BigDecimal> total = createNumber("total", BigDecimal.class);
        private final StringPath status = createString("status");

        private Orders() {
            super(OrderRow.class, "order", null, "orders");
        }
    }

    private static final class CustomerRow {
    }

    private static final class OrderRow {
    }
}
