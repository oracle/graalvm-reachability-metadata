/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.builder.annotation.ProviderContext;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProviderSqlSourceTest {
    @Test
    void invokesInstanceAndStaticSqlProviders() {
        try (SqlSession session = TestSupport.factory(ProviderMapper.class).openSession()) {
            ProviderMapper mapper = session.getMapper(ProviderMapper.class);
            assertEquals(12, mapper.instance(12));
            assertEquals(17, mapper.staticValue(17));
        }
    }

    public interface ProviderMapper {
        @SelectProvider(type = SqlProvider.class, method = "instanceSql")
        int instance(@Param("value") int value);

        @SelectProvider(value = SqlProvider.class, method = "staticSql")
        int staticValue(@Param("value") int value);
    }

    public static class SqlProvider {
        public String instanceSql(ProviderContext context) {
            assertEquals("instance", context.getMapperMethod().getName());
            return "SELECT #{value}";
        }

        public static String staticSql() {
            return "SELECT #{value}";
        }
    }
}
