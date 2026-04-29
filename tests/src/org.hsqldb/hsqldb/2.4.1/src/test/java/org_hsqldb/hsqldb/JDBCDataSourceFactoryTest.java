/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.naming.Reference;
import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSource;
import org.hsqldb.jdbc.JDBCDataSourceFactory;
import org.junit.jupiter.api.Test;

public class JDBCDataSourceFactoryTest {
    private static final String USER = "SA";
    private static final String PASSWORD = "";

    @Test
    public void createsDataSourceFromProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("database", "mem:factory-properties");
        properties.setProperty("username", USER);
        properties.setProperty("password", PASSWORD);
        properties.setProperty("loginTimeout", "7");

        DataSource dataSource = JDBCDataSourceFactory.createDataSource(properties);

        assertThat(dataSource).isInstanceOf(JDBCDataSource.class);

        JDBCDataSource jdbcDataSource = (JDBCDataSource) dataSource;
        assertThat(jdbcDataSource.getDatabase()).isEqualTo("mem:factory-properties");
        assertThat(jdbcDataSource.getUser()).isEqualTo(USER);
        assertThat(jdbcDataSource.getLoginTimeout()).isEqualTo(7);
    }

    @Test
    public void createsDataSourceFromJndiReference() throws Exception {
        JDBCDataSource originalDataSource = new JDBCDataSource();
        originalDataSource.setDatabase("mem:factory-reference");
        originalDataSource.setUser(USER);
        originalDataSource.setPassword(PASSWORD);
        originalDataSource.setLoginTimeout(3);
        Reference reference = originalDataSource.getReference();

        Object recreated = new JDBCDataSourceFactory().getObjectInstance(reference, null, null, null);

        assertThat(recreated).isInstanceOf(JDBCDataSource.class);

        JDBCDataSource recreatedDataSource = (JDBCDataSource) recreated;
        assertThat(recreatedDataSource.getDatabase()).isEqualTo(originalDataSource.getDatabase());
        assertThat(recreatedDataSource.getUser()).isEqualTo(originalDataSource.getUser());
        assertThat(recreatedDataSource.getLoginTimeout()).isEqualTo(originalDataSource.getLoginTimeout());
    }
}
