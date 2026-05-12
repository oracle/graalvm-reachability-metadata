/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.msgpack.util.TemplatePrecompiler;

public class TemplatePrecompilerTest {
    @Test
    void toClassLoadsRequestedClassesWithTemplatePrecompilerClassLoader() throws Throwable {
        final List<Class<?>> classes = invokeToClass(List.of(
                String.class.getName(),
                TemplatePrecompilerTest.class.getName()));

        assertThat(classes).containsExactly(String.class, TemplatePrecompilerTest.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Class<?>> invokeToClass(final List<String> classNames) throws Throwable {
        final MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                TemplatePrecompiler.class,
                MethodHandles.lookup());
        final MethodHandle toClass = lookup.findStatic(
                TemplatePrecompiler.class,
                "toClass",
                MethodType.methodType(List.class, List.class));
        return (List<Class<?>>) toClass.invoke(classNames);
    }
}
