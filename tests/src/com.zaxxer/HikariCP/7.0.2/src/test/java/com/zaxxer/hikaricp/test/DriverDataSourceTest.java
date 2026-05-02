/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.util.DriverDataSource;
import com.zaxxer.hikaricp.test.support.RecordingDriver;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverDataSourceTest {
    @Test
    void instantiatesDriverAndPassesConfiguredProperties() throws SQLException {
        RecordingDriver.reset();
        Properties properties = new Properties();
        properties.setProperty("applicationName", "hikari-test");

        DriverDataSource dataSource = new DriverDataSource(
                "jdbc:recording:driver",
                RecordingDriver.class.getName(),
                properties,
                "driver-user",
                "driver-password");

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection).isNotNull();
            assertThat(connection.isValid(1)).isTrue();
        }

        assertThat(RecordingDriver.lastUrl()).isEqualTo("jdbc:recording:driver");
        assertThat(RecordingDriver.lastProperties())
                .containsEntry("user", "driver-user")
                .containsEntry("password", "driver-password")
                .containsEntry("applicationName", "hikari-test");
    }
}
