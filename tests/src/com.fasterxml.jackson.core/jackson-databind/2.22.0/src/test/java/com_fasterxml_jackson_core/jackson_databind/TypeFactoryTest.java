/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.lang.reflect.Type;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeFactoryTest {

    @Test
    void findsClassWithConfiguredClassLoader() throws Exception {
        TypeFactory typeFactory = TypeFactory.defaultInstance()
                .withClassLoader(TypeFactoryTest.class.getClassLoader());

        Class<?> foundClass = typeFactory.findClass(ClassLoaderTarget.class.getName());

        assertThat(foundClass).isSameAs(ClassLoaderTarget.class);
    }

    @Test
    void fallsBackToDefaultClassLookupWhenNoClassLoaderIsAvailable() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(null);

            Class<?> foundClass = TypeFactory.defaultInstance().findClass(DefaultLookupTarget.class.getName());

            assertThat(foundClass).isSameAs(DefaultLookupTarget.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void resolvesGenericArrayTypeToRawArrayClass() {
        Type genericArrayType = new TypeReference<List<String>[]>() { }.getType();

        Class<?> rawClass = TypeFactory.rawClass(genericArrayType);

        assertThat(rawClass).isSameAs(List[].class);
    }

    public static final class ClassLoaderTarget {
    }

    public static final class DefaultLookupTarget {
    }
}
