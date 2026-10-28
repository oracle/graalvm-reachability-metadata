/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResultMapInnerBuilderTest {
    @Test
    void mapsNamedConstructorArgumentsInDeclaredOrder() {
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            Person person = session.getMapper(Mapper.class).person();
            assertEquals(12, person.id);
            assertEquals("Grace", person.name);
        }
    }

    public interface Mapper {
        @Select("SELECT 12 AS id, 'Grace' AS name")
        @ConstructorArgs({
                @Arg(name = "name", column = "name", javaType = String.class),
                @Arg(name = "id", column = "id", javaType = int.class)
        })
        Person person();
    }

    public static class Person {
        private final int id;
        private final String name;

        public Person(@Param("id") int id, @Param("name") String name) {
            this.id = id;
            this.name = name;
        }
    }
}
