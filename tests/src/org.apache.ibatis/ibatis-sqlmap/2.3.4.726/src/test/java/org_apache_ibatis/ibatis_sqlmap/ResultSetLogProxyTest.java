/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import com.ibatis.common.jdbc.logging.ResultSetLogProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class ResultSetLogProxyTest {
    @Test
    void readsNamedAndIndexedColumnsIncludingNullValues() throws Exception {
        try (Connection connection = JdbcTestSupport.openConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = ResultSetLogProxy.newInstance(statement.executeQuery("""
                        SELECT 1 AS id, 'visible' AS label
                        UNION ALL SELECT 2, CAST(NULL AS VARCHAR)
                        ORDER BY id
                        """))) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
            assertThat(rows.getString("label")).isEqualTo("visible");
            assertThat(rows.wasNull()).isFalse();
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt("id")).isEqualTo(2);
            assertThat(rows.getString("label")).isNull();
            assertThat(rows.wasNull()).isTrue();
            assertThat(rows.next()).isFalse();
        }
    }
}
