/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.sql.Connection;

import org.apache.logging.log4j.core.appender.db.jdbc.FactoryMethodConnectionSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FactoryMethodConnectionSourceTest {
    @Test
    void obtainsConnectionsFromAnApplicationDataSourceFactory() throws Exception {
        FactoryMethodConnectionSource source = FactoryMethodConnectionSource.createConnectionSource(
                JdbcFixtures.class.getName(), "dataSource");
        assertNotNull(source);
        try (Connection connection = source.getConnection()) {
            JdbcFixtures.assertConnected(connection);
        }
    }
}
