/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_jdbc;

import org.apache.tomcat.jdbc.pool.ClassLoaderUtil;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverTest {

    /**
     * The test simulates driver class loading from {@link PooledConnection#connectUsingDriver()}.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "com.mysql.cj.jdbc.Driver",
            "oracle.jdbc.driver.OracleDriver",
            "org.mariadb.jdbc.Driver",
            "org.postgresql.Driver"})
    void testDrivers(String driverClassName) throws Exception {
        java.sql.Driver driver = (java.sql.Driver)
                ClassLoaderUtil.loadClass(
                        driverClassName,
                        PooledConnection.class.getClassLoader(),
                        Thread.currentThread().getContextClassLoader()
                ).getConstructor().newInstance();
        assertThat(driver).isNotNull();
    }

}
