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

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class InvocationTest {
    @Test
    void proceedsWithRealParameterBindingAndDelegatesOtherMethods() throws Exception {
        Configuration configuration = new Configuration();
        PluginTest.CountingInterceptor interceptor = new PluginTest.CountingInterceptor();
        configuration.addInterceptor(interceptor);
        SqlSource source = new XMLLanguageDriver().createSqlSource(configuration, "SELECT #{value}", Integer.class);
        MappedStatement mapped = new MappedStatement.Builder(configuration, "parameter", source, SqlCommandType.SELECT).build();
        BoundSql sql = source.getBoundSql(34);
        ParameterHandler handler = configuration.newParameterHandler(mapped, 34, sql);
        assertEquals(34, handler.getParameterObject());
        try (Connection connection = TestSupport.dataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.getSql())) {
            handler.setParameters(statement);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                assertEquals(34, rows.getInt(1));
            }
        }
        assertEquals(1, interceptor.calls);
    }
}
