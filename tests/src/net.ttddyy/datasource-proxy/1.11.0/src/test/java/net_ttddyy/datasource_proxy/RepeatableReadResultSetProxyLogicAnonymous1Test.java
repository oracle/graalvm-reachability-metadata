/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RepeatableReadResultSetProxyLogicAnonymous1Test {
    @Test
    void interceptsCursorAndGetterMethodsForReplay() throws Exception {
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("repeatableMethods"))
                .repeatableReadResultSet().build();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT 'parcel' AS label")) {
            assertEquals("LABEL", rows.getMetaData().getColumnLabel(1));
            assertTrue(rows.next());
            assertEquals("parcel", rows.getString(1));
            assertFalse(rows.next());
            rows.beforeFirst();
            assertTrue(rows.next());
            assertEquals("parcel", rows.getString("label"));
            assertFalse(rows.next());
        }
    }
}
