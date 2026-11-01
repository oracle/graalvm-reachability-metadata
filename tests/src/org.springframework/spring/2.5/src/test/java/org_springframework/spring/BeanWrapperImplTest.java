/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

import static org.junit.jupiter.api.Assertions.*;

public class BeanWrapperImplTest {
    @Test
    void bindsNestedIndexedAndConvertedProperties() {
        TestBeans.Person person = new TestBeans.Person();
        person.setChild(new TestBeans.Person());
        BeanWrapperImpl wrapper = new BeanWrapperImpl(person);
        wrapper.setExtractOldValueForEditor(true);
        wrapper.setPropertyValue("name", "Grace");
        wrapper.setPropertyValue("age", "42");
        wrapper.setPropertyValue("child.name", "Junior");
        wrapper.setPropertyValue("tags[0]", "primary");
        wrapper.setPropertyValue("scores[math]", 99);
        assertEquals("Grace", wrapper.getPropertyValue("name"));
        assertEquals(42, person.getAge());
        assertEquals("Junior", person.getChild().getName());
        assertEquals("primary", wrapper.getPropertyValue("tags[0]"));
        assertEquals(99, person.getScores().get("math"));
    }
}
