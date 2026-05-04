/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsTest {
    @Test
    void createsExplicitDriverWithDefaultConstructor() throws Exception {
        try (Connection connection = JdbcUtils.getConnection(RecordingDriver.class.getName(),
                "jdbc:h2:mem:jdbcUtilsExplicitDriver", "sa", "")) {
            assertThat(connection.isValid(1)).isTrue();
            assertThat(RecordingDriver.connectCalls).isGreaterThan(0);
        }
    }

    public static final class RecordingDriver implements Driver {
        private static int connectCalls;

        public RecordingDriver() {
        }

        @Override
        public Connection connect(String url, Properties info) throws SQLException {
            connectCalls++;
            return new org.h2.Driver().connect(url, info);
        }

        @Override
        public boolean acceptsURL(String url) {
            return url.startsWith("jdbc:h2:");
        }

        @Override
        public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
            return new DriverPropertyInfo[0];
        }

        @Override
        public int getMajorVersion() {
            return 1;
        }

        @Override
        public int getMinorVersion() {
            return 0;
        }

        @Override
        public boolean jdbcCompliant() {
            return false;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }
    }
}
