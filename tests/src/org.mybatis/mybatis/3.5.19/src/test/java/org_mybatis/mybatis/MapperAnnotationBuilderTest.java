/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.util.List;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapperAnnotationBuilderTest {
    @Test
    void resolvesMapperResultTypeAndExecutesQuery() {
        assertEquals(String.class, MapperAnnotationBuilder.getMethodReturnType(Mapper.class.getName(), "names"));
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            assertEquals(List.of("Ada"), session.getMapper(Mapper.class).names());
        }
    }

    public interface Mapper {
        @Select("SELECT 'Ada'")
        List<String> names();
    }
}
