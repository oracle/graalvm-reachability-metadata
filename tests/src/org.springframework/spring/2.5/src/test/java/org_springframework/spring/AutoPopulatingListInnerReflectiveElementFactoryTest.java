/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.util.AutoPopulatingList;

import static org.junit.jupiter.api.Assertions.*;

public class AutoPopulatingListInnerReflectiveElementFactoryTest {
    @Test
    void createsAndRetainsElementsOnIndexedAccess() {
        AutoPopulatingList list = new AutoPopulatingList(TestBeans.Person.class);
        TestBeans.Person person = (TestBeans.Person) list.get(2);
        person.setName("created");
        assertEquals(3, list.size());
        assertSame(person, list.get(2));
        assertEquals("created", ((TestBeans.Person) list.get(2)).getName());
        assertNotSame(person, list.get(0));
    }
}
