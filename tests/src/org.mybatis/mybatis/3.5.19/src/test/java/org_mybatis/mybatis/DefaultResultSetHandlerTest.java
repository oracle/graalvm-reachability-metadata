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

public class DefaultResultSetHandlerTest {
    @Test
    void mapsQueryColumnsToImmutableConstructor() {
        try (SqlSession session = TestSupport.factory(Mapper.class).openSession()) {
            Person person = session.getMapper(Mapper.class).person();
            assertEquals(10, person.id);
            assertEquals("Ada", person.name);
        }
    }

    public interface Mapper {
        @Select("SELECT 10 AS id, 'Ada' AS name")
        Person person();
    }

    public static class Person {
        private final int id;
        private final String name;

        public Person(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
