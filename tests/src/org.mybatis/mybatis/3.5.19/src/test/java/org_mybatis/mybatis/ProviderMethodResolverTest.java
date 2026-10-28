/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.builder.annotation.ProviderMethodResolver;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ProviderMethodResolverTest {
    @Test
    void resolvesProviderUsingMapperMethodName() {
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            assertEquals(31, session.getMapper(Mapper.class).number());
        }
    }

    public interface Mapper {
        @SelectProvider(SqlProvider.class)
        int number();
    }

    public static class SqlProvider implements ProviderMethodResolver {
        public String number() {
            return "SELECT 31";
        }
    }
}
