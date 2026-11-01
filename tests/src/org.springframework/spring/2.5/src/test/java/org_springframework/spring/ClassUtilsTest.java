/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.util.ClassUtils;

import static org.junit.jupiter.api.Assertions.*;

public class ClassUtilsTest {
    @Test
    void resolvesClassesArraysAndPublicSignatures() throws Exception {
        assertSame(TestBeans.Person.class, ClassUtils.forName(TestBeans.Person.class.getName()));
        assertSame(String[].class, ClassUtils.forName("java.lang.String[]"));
        assertSame(String[][].class, ClassUtils.forName("[[Ljava.lang.String;"));
        assertNotNull(ClassUtils.getConstructorIfAvailable(TestBeans.Person.class, new Class<?>[0]));
        Method greeting = ClassUtils.getMethodIfAvailable(TestBeans.Greeting.class, "greet", new Class<?>[]{String.class});
        assertNotNull(greeting);
        assertSame(TestBeans.GreetingService.class,
                ClassUtils.getMostSpecificMethod(greeting, TestBeans.GreetingService.class).getDeclaringClass());
        assertEquals(1, ClassUtils.getMethodCountForName(TestBeans.Person.class, "setName"));
        assertTrue(ClassUtils.hasAtLeastOneMethodWithName(TestBeans.Person.class, "getName"));
        assertEquals("valueOf", ClassUtils.getStaticMethod(Integer.class, "valueOf", new Class<?>[]{String.class}).getName());
        Class<?> composite = ClassUtils.createCompositeInterface(
                new Class<?>[]{TestBeans.Greeting.class, Runnable.class}, getClass().getClassLoader());
        assertTrue(TestBeans.Greeting.class.isAssignableFrom(composite));
        assertTrue(Runnable.class.isAssignableFrom(composite));
    }
}
