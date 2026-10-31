/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleDriver;
import oracle.jdbc.driver.HAManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OracleDatabase.class)
public class HAManagerTest {
    @Test
    @Timeout(170)
    void closesConnectionsWhenTheirServicePublishesAnUnplannedDownEvent() throws Exception {
        String previousConfig = System.getProperty("oracle.jdbc.fanONSConfig");
        try (OnsPeer peer = new OnsPeer()) {
            System.setProperty("oracle.jdbc.fanONSConfig", peer.configuration());
            Properties options = OracleDatabase.properties();
            options.setProperty(OracleConnection.CONNECTION_PROPERTY_FAN_ENABLED, "true");
            URI address = URI.create(OracleDatabase.url().substring("jdbc:oracle:thin:@".length()));
            // A distinct URL avoids sharing HA state with connections opened without ONS configuration.
            String url = """
                    jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=%s)(PORT=%d))
                      (CONNECT_DATA=(SERVER=DEDICATED)(SERVICE_NAME=%s)))
                    """.formatted(address.getHost(), address.getPort(), address.getPath().substring(1)).strip();
            try (Connection connection = new OracleDriver().connect(url, options)) {
                String service;
                String database;
                try (Statement statement = connection.createStatement()) {
                    statement.setQueryTimeout(30);
                    try (ResultSet result = statement.executeQuery("""
                            select sys_context('USERENV', 'SERVICE_NAME'),
                                   sys_context('USERENV', 'DB_UNIQUE_NAME') from dual
                            """)) {
                        assertThat(result.next()).isTrue();
                        service = result.getString(1);
                        database = result.getString(2);
                    }
                }
                assertThat(connection.isClosed()).isFalse();
                peer.serviceDown(service, database);
                long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
                while (!connection.isClosed() && System.nanoTime() < deadline) {
                    Thread.sleep(100);
                }
                assertThat(connection.isClosed()).as("FAN closes connections to the down service").isTrue();
            } finally {
                HAManager.shutdownAll();
            }
        } finally {
            if (previousConfig == null) {
                System.clearProperty("oracle.jdbc.fanONSConfig");
            } else {
                System.setProperty("oracle.jdbc.fanONSConfig", previousConfig);
            }
        }
    }
}
