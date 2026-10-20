/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises repeatable view migrations to revise an application's revenue report with runOnChange.
@Timeout(60)
public class RepeatableReportingViewTest {
    @TempDir
    Path changelogDirectory;

    @Test
    public void replacesAnEditedReportingViewWithoutReapplyingOrderSeeds() throws Exception {
        String original = """
                <?xml version="1.0" encoding="UTF-8"?>
                <databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">
                    <changeSet id="create-orders" author="reporting-service">
                        <createTable tableName="sales_order">
                            <column name="id" type="int">
                                <constraints primaryKey="true" nullable="false"/>
                            </column>
                            <column name="status" type="varchar(20)"/>
                            <column name="total" type="decimal(12,2)"/>
                        </createTable>
                        <sql>
                            INSERT INTO sales_order VALUES (1, 'PAID', 10.00);
                            INSERT INTO sales_order VALUES (2, 'PAID', 25.00);
                            INSERT INTO sales_order VALUES (3, 'SHIPPED', 40.00);
                            INSERT INTO sales_order VALUES (4, 'CANCELLED', 99.00);
                        </sql>
                    </changeSet>
                    <changeSet id="revenue-report" author="reporting-service" runOnChange="true">
                        <createView viewName="revenue_report" replaceIfExists="true">
                            SELECT COUNT(*) AS order_count, SUM(total) AS revenue
                            FROM sales_order WHERE status = 'PAID'
                        </createView>
                    </changeSet>
                </databaseChangeLog>
                """;
        Path changelog = changelogDirectory.resolve("reporting.xml");
        Files.writeString(changelog, original);

        // The application keeps its connection open while separate deployment sessions come and go.
        try (Connection application = DriverManager.getConnection("jdbc:h2:mem:repeatable_reporting", "sa", "")) {
            deployReport();
            assertRevenue(application, 2, "35.00");

            // The next release counts shipped orders as revenue without modifying the original seed migration.
            Files.writeString(changelog, original.replace("WHERE status = 'PAID'",
                    "WHERE status IN ('PAID', 'SHIPPED')"));
            deployReport();
            assertRevenue(application, 3, "75.00");

            deployReport();
            assertRevenue(application, 3, "75.00");
            try (Statement sql = application.createStatement();
                 ResultSet rows = sql.executeQuery("SELECT COUNT(*), SUM(total) FROM sales_order")) {
                assertTrue(rows.next());
                assertEquals(4, rows.getInt(1), "Replacing a view must not duplicate or remove application orders");
                assertEquals(new BigDecimal("174.00"), rows.getBigDecimal(2));
                assertFalse(rows.next());
            }
        }
    }

    private void deployReport() throws Exception {
        try (Connection deployment = DriverManager.getConnection("jdbc:h2:mem:repeatable_reporting", "sa", "");
             DirectoryResourceAccessor resources = new DirectoryResourceAccessor(changelogDirectory)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(deployment));
            try (Liquibase liquibase = new Liquibase("reporting.xml", resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private static void assertRevenue(Connection application, int expectedCount, String expectedRevenue) throws Exception {
        try (Statement sql = application.createStatement();
             ResultSet rows = sql.executeQuery("SELECT order_count, revenue FROM revenue_report")) {
            assertTrue(rows.next());
            assertEquals(expectedCount, rows.getInt("order_count"));
            assertEquals(new BigDecimal(expectedRevenue), rows.getBigDecimal("revenue"));
            assertFalse(rows.next());
        }
    }
}
