/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

import org.apache.log4j.jdbc.JDBCAppender;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JDBCAppenderTest {

    private static final String TEST_URL = "jdbc:log4j-test";

    @Test
    void loadsConfiguredDriverClass() throws SQLException {
        JDBCAppender appender = new JDBCAppender();

        appender.setDriver(TestDriver.class.getName());

        Driver driver = DriverManager.getDriver(TEST_URL);
        try {
            assertThat(driver).isInstanceOf(TestDriver.class);
            assertThat(driver.acceptsURL(TEST_URL)).isTrue();
        } finally {
            DriverManager.deregisterDriver(driver);
        }
    }

    public static final class TestDriver implements Driver {

        static {
            try {
                DriverManager.registerDriver(new TestDriver());
            } catch (SQLException ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }

        @Override
        public Connection connect(String url, Properties info) {
            return null;
        }

        @Override
        public boolean acceptsURL(String url) {
            return TEST_URL.equals(url);
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
