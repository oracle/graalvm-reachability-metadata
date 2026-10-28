/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class UnpooledDataSourceTest {
    @Test
    void configuresDriverAndConnectionTransactionProperties() throws Exception {
        UnpooledDataSource dataSource = TestSupport.dataSource();
        dataSource.setDriverClassLoader(getClass().getClassLoader());
        dataSource.setAutoCommit(false);
        dataSource.setDefaultTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT 11")) {
            assertFalse(connection.getAutoCommit());
            assertEquals(Connection.TRANSACTION_SERIALIZABLE, connection.getTransactionIsolation());
            rows.next();
            assertEquals(11, rows.getInt(1));
        }
    }
}
