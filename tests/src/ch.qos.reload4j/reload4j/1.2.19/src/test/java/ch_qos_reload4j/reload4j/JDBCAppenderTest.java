/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import org.apache.log4j.jdbc.JDBCAppender;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBCAppenderTest {
    private static final String RECORDING_URL = "jdbc:reload4j-recording:test";

    @Test
    void setDriverLoadsJdbcDriverClassByName() throws SQLException {
        JDBCAppender appender = new JDBCAppender();

        appender.setDriver(RecordingDriver.class.getName());

        Driver driver = DriverManager.getDriver(RECORDING_URL);
        try {
            assertThat(driver).isInstanceOf(RecordingDriver.class);
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    public static final class RecordingDriver implements Driver {
        static {
            try {
                DriverManager.registerDriver(new RecordingDriver());
            } catch (SQLException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return RECORDING_URL.equals(url);
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
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException("No parent logger");
        }
    }
}
