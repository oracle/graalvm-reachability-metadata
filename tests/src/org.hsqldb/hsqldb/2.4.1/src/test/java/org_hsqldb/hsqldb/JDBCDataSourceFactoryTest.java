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
import javax.naming.StringRefAddr;
import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCCommonDataSource;
import org.hsqldb.jdbc.JDBCDataSourceFactory;
import org.junit.jupiter.api.Test;

public class JDBCDataSourceFactoryTest {
    private static final String DATA_SOURCE_CLASS_NAME = "org.hsqldb.jdbc.JDBCDataSource";

    @Test
    void createDataSourceBuildsConfiguredDataSourceFromProperties() throws Exception {
        String database = inMemoryDatabase("factory_create");
        Properties properties = new Properties();

        properties.setProperty("database", database);
        properties.setProperty("user", "SA");
        properties.setProperty("password", "");
        properties.setProperty("loginTimeout", "3");

        DataSource dataSource = JDBCDataSourceFactory.createDataSource(properties);

        assertThat(dataSource.getClass().getName()).isEqualTo(DATA_SOURCE_CLASS_NAME);
        JDBCCommonDataSource jdbcDataSource = (JDBCCommonDataSource) dataSource;
        assertThat(jdbcDataSource.getDatabase()).isEqualTo(database);
        assertThat(jdbcDataSource.getUser()).isEqualTo("SA");
        assertThat(jdbcDataSource.getLoginTimeout()).isEqualTo(3);
    }

    @Test
    void getObjectInstanceBuildsConfiguredDataSourceFromJndiReference() throws Exception {
        String database = inMemoryDatabase("factory_reference");
        Reference reference = new Reference(DATA_SOURCE_CLASS_NAME, JDBCDataSourceFactory.class.getName(), null);

        reference.add(new StringRefAddr("database", database));
        reference.add(new StringRefAddr("user", "SA"));
        reference.add(new StringRefAddr("password", ""));
        reference.add(new StringRefAddr("loginTimeout", "4"));

        Object object = new JDBCDataSourceFactory().getObjectInstance(reference, null, null, null);

        assertThat(object.getClass().getName()).isEqualTo(DATA_SOURCE_CLASS_NAME);
        assertThat(object).isInstanceOf(JDBCCommonDataSource.class);
        JDBCCommonDataSource dataSource = (JDBCCommonDataSource) object;
        assertThat(dataSource.getDatabase()).isEqualTo(database);
        assertThat(dataSource.getUser()).isEqualTo("SA");
        assertThat(dataSource.getLoginTimeout()).isEqualTo(4);
    }

    private static String inMemoryDatabase(String prefix) {
        return "mem:" + prefix + "_" + Long.toUnsignedString(System.nanoTime()) + ";shutdown=true";
    }
}
