/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLExpressions;
import com.querydsl.sql.SQLQueryFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's window function subsystem to rank each customer's orders for a report.
public class OrderRankingReportTest {
    @Test
    @Timeout(60)
    public void ranksOrdersWithinEachCustomerForAReport() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_order_ranking;DB_CLOSE_DELAY=-1", "sa", "")) {
            createOrdersTable(connection);
            insertOrders(connection);

            Orders orders = new Orders();
            Expression<Long> customerRank = SQLExpressions.rowNumber()
                    .over()
                    .partitionBy(orders.customer)
                    .orderBy(orders.total.desc(), orders.orderId.asc());
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<Tuple> rankedOrders = queryFactory
                    .select(orders.customer, orders.orderId, orders.total, customerRank)
                    .from(orders)
                    .orderBy(orders.customer.asc(), orders.total.desc(), orders.orderId.asc())
                    .fetch();

            assertEquals(4, rankedOrders.size());
            assertEquals("Acme", rankedOrders.get(0).get(orders.customer));
            assertEquals(101L, rankedOrders.get(0).get(orders.orderId));
            assertEquals(1L, rankedOrders.get(0).get(customerRank));
            assertEquals(102L, rankedOrders.get(1).get(orders.orderId));
            assertEquals(2L, rankedOrders.get(1).get(customerRank));
            assertEquals("Beacon", rankedOrders.get(2).get(orders.customer));
            assertEquals(201L, rankedOrders.get(2).get(orders.orderId));
            assertEquals(1L, rankedOrders.get(2).get(customerRank));
            assertEquals(202L, rankedOrders.get(3).get(orders.orderId));
            assertEquals(2L, rankedOrders.get(3).get(customerRank));
        }
    }

    private static void createOrdersTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE customer_orders (
                    order_id BIGINT PRIMARY KEY,
                    customer VARCHAR(100) NOT NULL,
                    total INTEGER NOT NULL
                )
                """);
    }

    private static void insertOrders(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO customer_orders (order_id, customer, total) VALUES
                    (101, 'Acme', 120),
                    (102, 'Acme', 80),
                    (201, 'Beacon', 150),
                    (202, 'Beacon', 90)
                """);
    }

    private static final class Orders extends RelationalPathBase<OrderRow> {
        private final NumberPath<Long> orderId = createNumber("order_id", Long.class);
        private final StringPath customer = createString("customer");
        private final NumberPath<Integer> total = createNumber("total", Integer.class);

        private Orders() {
            super(OrderRow.class, "order", null, "customer_orders");
        }
    }

    private static final class OrderRow {
    }
}
