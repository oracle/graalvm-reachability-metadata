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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class MapperMethodTest {
    @Test
    void convertsQueryResultsToReferenceAndPrimitiveArrays() {
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            Mapper mapper = session.getMapper(Mapper.class);
            assertArrayEquals(new int[]{1, 3}, mapper.numbers());
            assertArrayEquals(new String[]{"Ada", "Grace"}, mapper.names());
        }
    }

    public interface Mapper {
        @Select("SELECT 1 UNION ALL SELECT 3")
        int[] numbers();

        @Select("SELECT 'Ada' UNION ALL SELECT 'Grace'")
        String[] names();
    }
}
