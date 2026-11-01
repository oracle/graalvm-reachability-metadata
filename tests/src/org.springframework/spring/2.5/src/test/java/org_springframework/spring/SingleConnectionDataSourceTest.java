/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SingleConnectionDataSourceTest {
    @Test
    void sharesConnectionAndSuppressesClientClose() throws Exception {
        SingleConnectionDataSource source = new SingleConnectionDataSource(TestDatabase.source().getConnection(), true);
        try {
            Connection first = source.getConnection();
            first.close();
            assertSame(first, source.getConnection());
            assertFalse(first.isClosed());
            assertEquals(42, TestDatabase.query(first));
        } finally {
            source.destroy();
        }
    }
}
