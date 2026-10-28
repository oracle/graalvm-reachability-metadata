/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeHandlerRegistryTest {
    @Test
    void constructsEnumHandlerAndRoundTripsEnumValues() throws Exception {
        TypeHandlerRegistry registry = new Configuration().getTypeHandlerRegistry();
        TypeHandler<State> handler = registry.getTypeHandler(State.class);
        registry.register(String.class, StringTypeHandler.class);
        try (Connection connection = TestSupport.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT CAST(? AS VARCHAR)")) {
            handler.setParameter(statement, 1, State.ACTIVE, JdbcType.VARCHAR);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals("ACTIVE", rows.getString(1));
                assertEquals(State.ACTIVE, handler.getResult(rows, 1));
            }
            registry.getTypeHandler(String.class).setParameter(statement, 1, "text", JdbcType.VARCHAR);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals("text", rows.getString(1));
            }
        }
    }

    public enum State {
        ACTIVE, ARCHIVED
    }
}
