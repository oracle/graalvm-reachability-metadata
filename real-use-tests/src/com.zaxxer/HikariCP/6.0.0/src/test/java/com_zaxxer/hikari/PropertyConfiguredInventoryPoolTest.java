/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.hikari;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HikariCP's property-based DataSource construction and configuration subsystem.
/// An inventory service supplies deployment properties, including vendor DataSource settings,
/// and concurrent requests must connect to the same configured database as the configured user.
public class PropertyConfiguredInventoryPoolTest {
    @Test
    @Timeout(60)
    public void servesInventoryRequestsUsingDeploymentProperties() throws SQLException {
        Properties deployment = new Properties();
        deployment.setProperty("poolName", "inventory-service");
        deployment.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource");
        deployment.setProperty("dataSource.URL", "jdbc:h2:mem:property_configured_inventory");
        deployment.setProperty("dataSource.user", "inventory_app");
        deployment.setProperty("dataSource.password", "inventory-secret");
        deployment.setProperty("maximumPoolSize", "2");
        deployment.setProperty("minimumIdle", "0");
        deployment.setProperty("connectionTimeout", "10000");
        deployment.setProperty("validationTimeout", "10000");

        HikariConfig config = new HikariConfig(deployment);
        try (HikariDataSource pool = new HikariDataSource(config);
                        Connection receivingRequest = pool.getConnection()) {
            try (Statement schema = receivingRequest.createStatement()) {
                schema.execute("CREATE TABLE inventory (sku VARCHAR(30) PRIMARY KEY, quantity INTEGER NOT NULL)");
            }
            try (PreparedStatement receipt = receivingRequest.prepareStatement(
                            "INSERT INTO inventory (sku, quantity) VALUES (?, ?)")) {
                receipt.setString(1, "COFFEE-250G");
                receipt.setInt(2, 24);
                assertEquals(1, receipt.executeUpdate());
            }

            // Hold the first lease so this request must use a second physical connection.
            try (Connection stockRequest = pool.getConnection()) {
                assertFalse(receivingRequest.isClosed());
                try (Statement query = stockRequest.createStatement();
                                ResultSet identity = query.executeQuery("SELECT CURRENT_USER")) {
                    assertTrue(identity.next());
                    assertEquals("INVENTORY_APP", identity.getString(1));
                    assertFalse(identity.next());
                }
                try (PreparedStatement query = stockRequest.prepareStatement(
                                "SELECT quantity FROM inventory WHERE sku = ?")) {
                    query.setString(1, "COFFEE-250G");
                    try (ResultSet stock = query.executeQuery()) {
                        assertTrue(stock.next(), "Both pool connections must use the configured inventory database");
                        assertEquals(24, stock.getInt("quantity"));
                        assertFalse(stock.next());
                    }
                }
            }
        }
    }
}
