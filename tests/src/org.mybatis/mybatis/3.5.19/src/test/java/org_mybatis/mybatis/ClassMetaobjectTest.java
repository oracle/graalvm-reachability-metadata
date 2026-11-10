/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.javassist.tools.reflect.ClassMetaobject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassMetaobjectTest {
    @Test
    void constructsObjectsAndAccessesSharedFields() throws Exception {
        ClassMetaobject metaobject = new ClassMetaobject(new String[]{Value.class.getName()});
        Value value = (Value) metaobject.newInstance(new Object[]{"Ada"});
        assertEquals("Ada", value.name);
        assertTrue(metaobject.isInstance(value));
        String previous = Value.category;
        try {
            metaobject.trapFieldWrite("category", "people");
            assertEquals("people", Value.category);
            assertEquals("people", metaobject.trapFieldRead("category"));
        } finally {
            Value.category = previous;
        }
    }

    @Test
    void restoresConstructorInformationFromASerializedMetaobject() throws Exception {
        boolean previous = ClassMetaobject.useContextClassLoader;
        try {
            ClassMetaobject.useContextClassLoader = true;
            ClassMetaobject original = new ClassMetaobject(new String[]{Value.class.getName()});
            SerializedCache cache = new SerializedCache(new PerpetualCache("class-metaobjects"));
            cache.putObject("value", original);
            ClassMetaobject restored = (ClassMetaobject) cache.getObject("value");
            Value value = (Value) restored.newInstance(new Object[]{"Grace"});
            assertEquals("Grace", value.name);
            assertEquals(Value.class.getName(), restored.getName());
        } finally {
            ClassMetaobject.useContextClassLoader = previous;
        }
    }

    @Test
    void dispatchesStaticMethodsAndInstancesWithoutAMetaobject() throws Throwable {
        ReflectivePerson person = new ReflectivePerson("Ada");
        ClassMetaobject metaobject = person._getClass();
        int instanceMethod = metaobject.getMethodIndex("label", new Class<?>[]{String.class});
        int staticMethod = metaobject.getMethodIndex("category", new Class<?>[]{String.class});
        assertEquals(0, instanceMethod);
        assertEquals(1, staticMethod);
        assertEquals("category", metaobject.getMethodName(staticMethod));
        assertArrayEquals(new Class<?>[]{String.class}, metaobject.getParameterTypes(staticMethod));
        assertEquals(String.class, metaobject.getReturnType(staticMethod));
        assertEquals("kind: person", ReflectivePerson.category("kind: "));

        person._setMetaobject(null);
        assertEquals("hello Ada", person.label("hello "));
        person.name = "Grace";
        assertEquals("hello Grace", person.label("hello "));
    }

    public static class Value {
        public static String category = "default";
        public final String name;

        public Value(String name) {
            this.name = name;
        }
    }
}
