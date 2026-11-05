/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanUtilsTest {
    @Test
    void constructsAndCopiesJavaBeanProperties() {
        TestBeans.Person source = new TestBeans.Person();
        source.setName("Ada");
        source.setAge(37);
        TestBeans.Person target = (TestBeans.Person) BeanUtils.instantiateClass(TestBeans.Person.class);
        BeanUtils.copyProperties(source, target);
        assertEquals("Ada", target.getName());
        assertEquals(37, target.getAge());
        assertEquals("setName", BeanUtils.findMethod(TestBeans.Person.class, "setName", new Class<?>[]{String.class}).getName());
        assertEquals("getAge", BeanUtils.findDeclaredMethod(TestBeans.Person.class, "getAge", new Class<?>[0]).getName());
        assertEquals(0, BeanUtils.findMethodWithMinimalParameters(TestBeans.Person.class, "getName").getParameterCount());
        assertEquals(1, BeanUtils.findDeclaredMethodWithMinimalParameters(TestBeans.Person.class, "setAge").getParameterCount());
    }
}
