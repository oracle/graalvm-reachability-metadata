/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class PidFactoryTest extends DatabaseTestSupport {
    @Test
    void sendsTheClientProcessIdAsAJdbcConnectionAttribute() throws Exception {
        try (Connection connection = database.connect("", new Properties());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT ATTR_VALUE FROM performance_schema.session_connect_attrs
                     WHERE PROCESSLIST_ID = CONNECTION_ID() AND ATTR_NAME = '__pid'
                     """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(Long.toString(ProcessHandle.current().pid()));
            assertThat(result.next()).isFalse();
        }
    }
}
