/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_common;

import com.alibaba.nacos.common.utils.ClassUtils;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassUtilsTest {
    @Test
    void resolvesNamedClassesAndSourceStyleNestedClasses() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertSame(ArrayList.class, ClassUtils.findClassByName("java.util.ArrayList"));
        assertSame(ArrayList.class, ClassUtils.forName("java.util.ArrayList", loader));
        assertSame(AbstractMap.SimpleEntry.class, ClassUtils.forName("java.util.AbstractMap.SimpleEntry", loader));
        assertTrue(ClassUtils.isAssignableFrom(Iterable.class, ClassUtils.forName("java.util.ArrayList", null)));
    }

    @Test
    void resolvesSourceAndJvmArrayNames() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        assertSame(ArrayList[].class, ClassUtils.forName("java.util.ArrayList[]", loader));
        assertSame(ArrayList[].class, ClassUtils.forName("[Ljava.util.ArrayList;", loader));
        assertSame(ArrayList[][].class, ClassUtils.forName("[[Ljava.util.ArrayList;", loader));
        assertSame(int[][].class, ClassUtils.forName("[[I", loader));
        assertEquals("java/util/ArrayList", ClassUtils.convertClassNameToResourcePath("java.util.ArrayList"));
    }
}
