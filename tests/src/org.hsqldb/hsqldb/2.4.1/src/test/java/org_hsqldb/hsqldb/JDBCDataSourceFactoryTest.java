/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.UUID;

import javax.naming.Reference;
import javax.naming.StringRefAddr;
import javax.sql.DataSource;

import org.hsqldb.jdbc.JDBCDataSourceFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JDBCDataSourceFactoryTest {
    @Test
    void connectsUsingAPropertyConfiguredDataSource() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("url", "jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=true");
        properties.setProperty("username", "SA");
        properties.setProperty("password", "");
        properties.setProperty("loginTimeout", "10");
        DataSource source = JDBCDataSourceFactory.createDataSource(properties);
        assertEquals(10, source.getLoginTimeout());
        assertQuery(source);
    }

    @Test
    void reconstructsADataSourceFromAJndiReference() throws Exception {
        Reference reference = new Reference("org.hsqldb.jdbc.JDBCDataSource");
        reference.add(new StringRefAddr("database", "jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=true"));
        reference.add(new StringRefAddr("user", "SA"));
        reference.add(new StringRefAddr("password", ""));
        reference.add(new StringRefAddr("loginTimeout", "10"));
        DataSource source = (DataSource) new JDBCDataSourceFactory().getObjectInstance(reference, null, null, null);
        assertEquals(10, source.getLoginTimeout());
        assertQuery(source);
    }

    private static void assertQuery(DataSource source) throws Exception {
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("VALUES (6 * 7)")) {
            assertTrue(result.next());
            assertEquals(42, result.getInt(1));
        }
    }
}
