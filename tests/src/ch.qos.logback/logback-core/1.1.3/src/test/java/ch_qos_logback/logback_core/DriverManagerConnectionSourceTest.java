/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback.logback_core;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.core.ContextBase;
import ch.qos.logback.core.db.DriverManagerConnectionSource;
import ch.qos.logback.core.db.dialect.SQLDialectCode;
import java.sql.Connection;
import org.junit.jupiter.api.Test;

public class DriverManagerConnectionSourceTest {

    private static final String JDBC_URL =
            "jdbc:h2:mem:logback_core_driver_manager_connection_source;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";

    @Test
    void loadsTheConfiguredDriverClassAndDiscoversJdbcCapabilities() throws Exception {
        DriverManagerConnectionSource connectionSource = new DriverManagerConnectionSource();
        connectionSource.setContext(new ContextBase());
        connectionSource.setDriverClass("org.h2.Driver");
        connectionSource.setUrl(JDBC_URL);

        connectionSource.start();

        try (Connection connection = connectionSource.getConnection()) {
            assertThat(connection).isNotNull();
        }

        assertThat(connectionSource.getSQLDialectCode()).isEqualTo(SQLDialectCode.H2_DIALECT);
        assertThat(connectionSource.supportsBatchUpdates()).isTrue();
        assertThat(connectionSource.supportsGetGeneratedKeys()).isTrue();
    }
}
