/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import static org.junit.jupiter.api.Assertions.*;

public class LazyConnectionDataSourceProxyInnerLazyConnectionInvocationHandlerTest {
    @Test
    void appliesDeferredSettingsAndDelegatesJdbcOperations() throws Exception {
        LazyConnectionDataSourceProxy source = new LazyConnectionDataSourceProxy(TestDatabase.source());
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            assertFalse(connection.getAutoCommit());
            assertEquals(42, TestDatabase.query(connection));
            assertEquals("H2", connection.getMetaData().getDatabaseProductName());
            connection.rollback();
        }
    }
}
