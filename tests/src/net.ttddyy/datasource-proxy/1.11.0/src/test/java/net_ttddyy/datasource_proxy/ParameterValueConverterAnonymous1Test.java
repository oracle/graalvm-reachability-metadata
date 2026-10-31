/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_ttddyy.datasource_proxy;

import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterValueConverterAnonymous1Test {
    @Test
    void includesSqlTypeNamesWhenLoggingNullParameters() throws Exception {
        List<String> entries = new ArrayList<>();
        DefaultQueryLogEntryCreator logCreator = new DefaultQueryLogEntryCreator();
        DataSource dataSource = ProxyDataSourceBuilder.create(TestDataSources.create("nullParameterLogging"))
                .name("nullable-query")
                .afterQuery((info, queries) -> entries.add(logCreator.getLogEntry(info, queries, true, false, false)))
                .build();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement query = connection.prepareStatement("SELECT CAST(? AS VARCHAR), CAST(? AS INTEGER)")) {
            query.setNull(1, Types.VARCHAR);
            query.setNull(2, Types.INTEGER);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertNull(rows.getString(1));
                assertNull(rows.getObject(2));
            }
        }
        assertThat(entries).singleElement().asString()
                .contains("Name:nullable-query", "Success:True", "Params:[(NULL(VARCHAR),NULL(INTEGER))]");
    }
}
