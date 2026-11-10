/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.cache.decorators.SerializedCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.javassist.tools.reflect.Metaobject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class MetaobjectTest {
    @Test
    void dispatchesFieldAccessAndInstanceMethods() throws Throwable {
        ReflectivePerson person = new ReflectivePerson("Ada");
        Metaobject metaobject = person._getMetaobject();
        assertEquals("Ada", metaobject.trapFieldRead("name"));
        metaobject.trapFieldWrite("name", "Grace");
        assertEquals("Grace", person.name);
        assertEquals("hello Grace", person.label("hello "));
        assertEquals("label", metaobject.getMethodName(0));
        assertArrayEquals(new Class<?>[]{String.class}, metaobject.getParameterTypes(0));
        assertEquals(String.class, metaobject.getReturnType(0));
    }

    @Test
    void restoresTheBaseObjectAndRebindsTheMetaobject() throws Throwable {
        ReflectivePerson original = new ReflectivePerson("Ada");
        SerializedCache cache = new SerializedCache(new PerpetualCache("instance-metaobjects"));
        cache.putObject("person", original._getMetaobject());
        Metaobject restored = (Metaobject) cache.getObject("person");
        ReflectivePerson copy = (ReflectivePerson) restored.getObject();
        assertNotSame(original, copy);
        assertEquals("welcome Ada", restored.trapMethodcall(0, new Object[]{"welcome "}));
        restored.trapFieldWrite("name", "Grace");
        assertEquals("Ada", original.name);
        assertEquals("Grace", copy.name);

        ReflectivePerson replacement = new ReflectivePerson("Lin");
        restored.setObject(replacement);
        assertSame(restored, replacement._getMetaobject());
        assertSame(replacement._getClass(), restored.getClassMetaobject());
        assertEquals("hello Lin", replacement.label("hello "));
    }
}
