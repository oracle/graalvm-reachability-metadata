/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.ReflectionUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionUtilsTest {
    @Test
    void findsMembersAndAppliesCallbacks() {
        TestBeans.Fields bean = new TestBeans.Fields();
        Field field = ReflectionUtils.findField(TestBeans.Fields.class, "count", int.class);
        ReflectionUtils.setField(field, bean, 12);
        assertEquals(12, bean.count);
        Method method = ReflectionUtils.findMethod(TestBeans.Greeting.class, "greet", new Class<?>[]{String.class});
        assertEquals("Hello visitor", ReflectionUtils.invokeMethod(method, new TestBeans.GreetingService(), new Object[]{"visitor"}));
        assertNotNull(ReflectionUtils.findMethod(TestBeans.Person.class, "getName"));
        Set<String> fields = new HashSet<>();
        ReflectionUtils.doWithFields(TestBeans.Fields.class, member -> fields.add(member.getName()));
        assertTrue(fields.containsAll(Set.of("label", "count", "CATEGORY")));
        Set<String> methods = new HashSet<>();
        ReflectionUtils.doWithMethods(TestBeans.Person.class, member -> methods.add(member.getName()));
        assertTrue(methods.containsAll(Set.of("getName", "setName", "getAge")));
    }
}
