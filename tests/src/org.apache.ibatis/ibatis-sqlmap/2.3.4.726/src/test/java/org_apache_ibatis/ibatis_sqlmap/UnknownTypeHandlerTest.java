/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ibatis.ibatis_sqlmap;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import com.ibatis.sqlmap.engine.type.TypeHandler;
import com.ibatis.sqlmap.engine.type.TypeHandlerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class UnknownTypeHandlerTest {
    @Test
    void resolvesRuntimeParameterTypesAndReadsJdbcValues() throws Exception {
        TypeHandler handler = new TypeHandlerFactory().getUnkownTypeHandler();
        try (Connection connection = JdbcTestSupport.openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        SELECT CAST(? AS VARCHAR) AS title, CAST(? AS INTEGER) AS quantity,
                               CAST(NULL AS VARCHAR) AS absent
                        """)) {
            handler.setParameter(statement, 1, "notebook", "VARCHAR");
            handler.setParameter(statement, 2, 12, "INTEGER");
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(handler.getResult(result, "title")).isEqualTo("notebook");
                assertThat(handler.getResult(result, 2)).isEqualTo(12);
                assertThat(handler.getResult(result, "absent")).isNull();
                assertThat(handler.getResult(result, 3)).isNull();
            }
            try (CallableStatement call = connection.prepareCall("{? = call ABS(?)}")) {
                call.registerOutParameter(1, Types.INTEGER);
                handler.setParameter(call, 2, -12, "INTEGER");
                call.execute();
                assertThat(handler.getResult(call, 1)).isEqualTo(12);
            }
        }
        assertThat(handler.equals(12, "12")).isTrue();
        assertThat(handler.equals(12, "13")).isFalse();
        assertThat(handler.valueOf("text")).isEqualTo("text");
    }
}
