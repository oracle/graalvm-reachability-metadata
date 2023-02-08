/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;

@SuppressWarnings("unused")
public class TesterConnectionFactory implements ConnectionFactory {

    private final String connectionString;
    private final Driver driver;
    private final Properties properties;

    public TesterConnectionFactory(final Driver driver, final String connectString, final Properties properties) {
        this.driver = driver;
        this.connectionString = connectString;
        this.properties = properties;
    }

    @Override
    public Connection createConnection() throws SQLException {
        final Connection conn = driver.connect(connectionString, properties);
        doSomething(conn);
        return conn;
    }

    private void doSomething(final Connection conn) {

    }

    public String getConnectionString() {
        return connectionString;
    }

    public Driver getDriver() {
        return driver;
    }

    public Properties getProperties() {
        return properties;
    }

    @Override
    public String toString() {
        return this.getClass().getName() + " [" + driver + ";" + connectionString + ";" + properties + "]";
    }
}
