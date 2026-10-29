/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.sql.Connection;

import org.apache.logging.log4j.core.appender.db.jdbc.AbstractDriverManagerConnectionSource;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

public class AbstractDriverManagerConnectionSourceTest {
    @Test
    void loadsTheConfiguredJdbcDriverAndOpensAWorkingConnection() throws Exception {
        String url = "jdbc:h2:mem:driver-source";
        AbstractDriverManagerConnectionSource source = new AbstractDriverManagerConnectionSource(
                "org.h2.Driver", url, url, null, null, Property.EMPTY_ARRAY);
        try (Connection connection = source.getConnection()) {
            JdbcFixtures.assertConnected(connection);
        }
    }
}
