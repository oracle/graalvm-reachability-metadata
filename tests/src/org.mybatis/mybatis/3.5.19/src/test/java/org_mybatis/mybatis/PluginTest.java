/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.sql.PreparedStatement;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginTest {
    @Test
    void wrapsParameterHandlerAndInterceptsBinding() {
        CountingInterceptor interceptor = new CountingInterceptor();
        Configuration configuration = TestSupport.configuration();
        configuration.addInterceptor(interceptor);
        configuration.addMapper(Mapper.class);
        try (SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession()) {
            assertEquals(22, session.getMapper(Mapper.class).value(22));
        }
        assertEquals(1, interceptor.calls);
    }

    public interface Mapper {
        @Select("SELECT #{value}")
        int value(int value);
    }

    @Intercepts(@Signature(type = ParameterHandler.class, method = "setParameters", args = PreparedStatement.class))
    public static class CountingInterceptor implements Interceptor {
        public int calls;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            calls++;
            return invocation.proceed();
        }
    }
}
