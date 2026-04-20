/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_classfilewriter.jboss_classfilewriter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.classfilewriter.ClassFile;
import org.junit.jupiter.api.Test;

public class DefaultClassFactoryTest {
    private static final AtomicInteger GENERATED_CLASS_COUNTER = new AtomicInteger();

    @Test
    void defineUsesTheDefaultClassFactoryWithoutAnExplicitProtectionDomain() {
        enableDefaultClassFactoryOnCurrentJdk();

        final Class<?> generatedClass = newClassFile("ImplicitDomain", Serializable.class).define();

        assertThat(generatedClass.getName()).startsWith(generatedClassPrefix("ImplicitDomain"));
        assertThat(Serializable.class).isAssignableFrom(generatedClass);
    }

    @Test
    void defineUsesTheDefaultClassFactoryWithAnExplicitProtectionDomain() {
        enableDefaultClassFactoryOnCurrentJdk();

        final ProtectionDomain protectionDomain = DefaultClassFactoryTest.class.getProtectionDomain();
        final Class<?> generatedClass = newClassFile("ExplicitDomain", Cloneable.class).define(protectionDomain);

        assertThat(generatedClass.getName()).startsWith(generatedClassPrefix("ExplicitDomain"));
        assertThat(Cloneable.class).isAssignableFrom(generatedClass);
        assertThat(generatedClass.getProtectionDomain()).isEqualTo(protectionDomain);
    }

    private static void enableDefaultClassFactoryOnCurrentJdk() {
        if (Runtime.version().feature() < 12) {
            return;
        }
        try {
            final Field fieldFilterMapField = findHiddenField(Class.forName("jdk.internal.reflect.Reflection"), "fieldFilterMap");
            fieldFilterMapField.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<Class<?>, Set<String>> fieldFilterMap = (Map<Class<?>, Set<String>>) fieldFilterMapField.get(null);
            if (fieldFilterMap.containsKey(AccessibleObject.class)) {
                fieldFilterMap.remove(AccessibleObject.class);
                clearCachedReflectionData(AccessibleObject.class);
            }
        } catch (final ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clearCachedReflectionData(final Class<?> type) throws ReflectiveOperationException {
        final Field reflectionDataField = findHiddenField(Class.class, "reflectionData");
        reflectionDataField.setAccessible(true);
        reflectionDataField.set(type, null);
    }

    private static Field findHiddenField(final Class<?> type, final String fieldName) throws ReflectiveOperationException {
        final Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
        getDeclaredFields0.setAccessible(true);
        for (final Field field : (Field[]) getDeclaredFields0.invoke(type, false)) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @SuppressWarnings("deprecation")
    private static ClassFile newClassFile(final String suffix, final Class<?> interfaceType) {
        return new ClassFile(
            generatedClassPrefix(suffix) + GENERATED_CLASS_COUNTER.incrementAndGet(),
            Object.class.getName(),
            DefaultClassFactoryTest.class.getClassLoader(),
            interfaceType.getName()
        );
    }

    private static String generatedClassPrefix(final String suffix) {
        return DefaultClassFactoryTest.class.getPackageName() + ".DefaultClassFactory" + suffix;
    }
}
