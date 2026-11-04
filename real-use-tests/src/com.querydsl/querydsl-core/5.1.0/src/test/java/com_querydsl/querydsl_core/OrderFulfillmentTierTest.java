/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringExpression;
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

/// Exercises Querydsl's conditional expression subsystem to classify orders for fulfillment.
public class OrderFulfillmentTierTest {
    @Test
    @Timeout(60)
    public void classifiesOrdersWithBusinessRulesForTheFulfillmentDashboard() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_fulfillment_tiers;DB_CLOSE_DELAY=-1", "sa", "")) {
            createOrdersTable(connection);
            insertOrders(connection);

            Orders orders = new Orders();
            StringExpression fulfillmentTier = new CaseBuilder()
                    .when(orders.total.goe(new BigDecimal("250.00"))).then("priority")
                    .when(orders.total.goe(new BigDecimal("100.00"))).then("standard")
                    .otherwise("review");
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);

            List<Tuple> dashboardRows = queryFactory
                    .select(orders.customer, fulfillmentTier)
                    .from(orders)
                    .orderBy(orders.id.asc())
                    .fetch();

            assertEquals(4, dashboardRows.size());
            assertEquals("Acme", dashboardRows.get(0).get(orders.customer));
            assertEquals("priority", dashboardRows.get(0).get(fulfillmentTier));
            assertEquals("Beacon", dashboardRows.get(1).get(orders.customer));
            assertEquals("standard", dashboardRows.get(1).get(fulfillmentTier));
            assertEquals("Cobalt", dashboardRows.get(2).get(orders.customer));
            assertEquals("review", dashboardRows.get(2).get(fulfillmentTier));
            assertEquals("Delta", dashboardRows.get(3).get(orders.customer));
            assertEquals("review", dashboardRows.get(3).get(fulfillmentTier));
        }
    }

    private static void createOrdersTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    customer VARCHAR(100) NOT NULL,
                    total DECIMAL(10, 2) NOT NULL
                )
                """);
    }

    private static void insertOrders(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO orders (id, customer, total) VALUES
                    (1, 'Acme', 275.50),
                    (2, 'Beacon', 125.00),
                    (3, 'Cobalt', 80.00),
                    (4, 'Delta', 45.00)
                """);
    }

    private static final class Orders extends RelationalPathBase<OrderRow> {
        private final NumberPath<Long> id = createNumber("id", Long.class);
        private final StringPath customer = createString("customer");
        private final NumberPath<BigDecimal> total = createNumber("total", BigDecimal.class);

        private Orders() {
            super(OrderRow.class, "order", null, "orders");
        }
    }

    private static final class OrderRow {
    }
}
