/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.H2Templates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQueryFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// Exercises Querydsl's template expression subsystem to normalize customer names for search.
public class TemplateExpressionSearchTest {
    @Test
    @Timeout(60)
    public void searchesCustomersWithAReusableDatabaseExpression() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:querydsl_template_search;DB_CLOSE_DELAY=-1", "sa", "")) {
            createCustomersTable(connection);
            insertCustomers(connection);

            Customers customers = new Customers();
            StringExpression normalizedName = Expressions.stringTemplate(
                    "upper({0})", customers.name);
            SQLQueryFactory queryFactory = new SQLQueryFactory(
                    new Configuration(new H2Templates()), () -> connection);
            List<Tuple> matchingCustomers = queryFactory
                    .select(customers.name, normalizedName)
                    .from(customers)
                    .where(normalizedName.eq("ACME"))
                    .fetch();

            assertEquals(1, matchingCustomers.size());
            assertEquals("Acme", matchingCustomers.get(0).get(customers.name));
            assertEquals("ACME", matchingCustomers.get(0).get(normalizedName));
        }
    }

    private static void createCustomersTable(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                CREATE TABLE customers (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL
                )
                """);
    }

    private static void insertCustomers(Connection connection) throws SQLException {
        connection.createStatement().executeUpdate("""
                INSERT INTO customers (id, name) VALUES
                    (1, 'Acme'),
                    (2, 'Beacon'),
                    (3, 'Acme Services')
                """);
    }

    private static final class Customers extends RelationalPathBase<CustomerRow> {
        private final StringPath name = createString("name");

        private Customers() {
            super(CustomerRow.class, "customer", null, "customers");
        }
    }

    private static final class CustomerRow {
    }
}
