/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractBeanPropertyRowMapperTest {
    @Test
    void constructsAndPopulatesBeanFromResultSetColumns() {
        JdbcTemplate template = new JdbcTemplate(TestDatabase.source());
        template.setQueryTimeout(10);
        List<?> people = template.query("select 'Ada' as name, 37 as age", new BeanPropertyRowMapper(TestBeans.Person.class));
        assertEquals(1, people.size());
        TestBeans.Person person = (TestBeans.Person) people.getFirst();
        assertEquals("Ada", person.getName());
        assertEquals(37, person.getAge());
    }
}
