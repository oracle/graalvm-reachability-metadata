/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MapperProxyTest {
    @Test
    void invokesDefaultMapperMethodAndObjectMethods() {
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            Mapper mapper = session.getMapper(Mapper.class);
            assertEquals("total=14", mapper.description());
            assertEquals("total=14", mapper.description());
            assertNotNull(mapper.toString());
        }
    }

    public interface Mapper {
        @Select("SELECT 14")
        int total();

        default String description() {
            return "total=" + total();
        }
    }
}
