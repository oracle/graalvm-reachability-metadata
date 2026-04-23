/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.c3p0;

import com.mchange.v2.c3p0.DriverManagerDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverManagerDataSourceTest {
    @Test
    void createsConnectionsWithNamedDriverAndSurvivesSerialization() throws Exception {
        DriverManagerDataSource dataSource = C3p0TestSupport.newDriverManagerDataSource("driver-manager");
        dataSource.setDescription("driver-manager test datasource");
        dataSource.setFactoryClassLocation("test-factory-location");
        dataSource.setForceUseNamedDriverClass(true);

        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }

        DriverManagerDataSource restored = C3p0TestSupport.roundTrip(dataSource);

        assertThat(restored.getDescription()).isEqualTo("driver-manager test datasource");
        assertThat(restored.getJdbcUrl()).startsWith("jdbc:h2:mem:driver-manager-");

        try (Connection connection = restored.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }
}
