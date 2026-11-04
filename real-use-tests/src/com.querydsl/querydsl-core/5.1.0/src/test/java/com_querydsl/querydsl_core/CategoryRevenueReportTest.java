/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.group.GroupBy;
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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's result grouping subsystem to build a category revenue report from SQL rows.
public class CategoryRevenueReportTest {
    @Test
    @Timeout(60)
    public void groupsLineItemsAndSumsRevenueByCategory() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_revenue;DB_CLOSE_DELAY=-1", "sa", "")) {
            createLineItemsTable(connection);
            insertLineItems(connection);

            LineItems lineItems = new LineItems();
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            Map<String, BigDecimal> revenueByCategory = queryFactory
                    .from(lineItems)
                    .transform(GroupBy.groupBy(lineItems.category).as(GroupBy.sum(lineItems.revenue)));

            assertEquals(Map.of(
                    "office", new BigDecimal("378.00"),
                    "kitchen", new BigDecimal("42.00")), revenueByCategory);
        }
    }

    private static void createLineItemsTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE line_items (
                    id BIGINT PRIMARY KEY,
                    category VARCHAR(100) NOT NULL,
                    revenue DECIMAL(10, 2) NOT NULL
                )
                """);
    }

    private static void insertLineItems(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO line_items (id, category, revenue) VALUES
                    (1, 'office', 249.00),
                    (2, 'office', 129.00),
                    (3, 'kitchen', 42.00)
                """);
    }

    private static final class LineItems extends RelationalPathBase<LineItem> {
        private final StringPath category = createString("category");
        private final NumberPath<BigDecimal> revenue = createNumber("revenue", BigDecimal.class);

        private LineItems() {
            super(LineItem.class, "lineItem", null, "line_items");
        }
    }

    private static final class LineItem {
    }
}
