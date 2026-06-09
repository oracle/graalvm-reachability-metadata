/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.type.TypeFactory;

public class TypeFactoryTest {
    private static final String TARGET_CLASS_NAME =
            "org_apache_parquet.parquet_jackson.TypeFactoryTestTarget";

    @Test
    void findsClassByNameWithThreadContextClassLoader() throws Exception {
        final TypeFactory typeFactory = TypeFactory.defaultInstance();

        final Class<?> targetClass = typeFactory.findClass(TARGET_CLASS_NAME);

        assertThat(targetClass).isEqualTo(TypeFactoryTestTarget.class);
    }

    @Test
    void fallsBackToDefaultClassLookupWhenThreadContextClassLoaderCannotLoadClass() throws Exception {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingClassLoader());
        try {
            final TypeFactory typeFactory = TypeFactory.defaultInstance();

            final Class<?> targetClass = typeFactory.findClass(TARGET_CLASS_NAME);

            assertThat(targetClass).isEqualTo(TypeFactoryTestTarget.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void resolvesRawClassForGenericArrayType() {
        final Class<?> rawClass = TypeFactory.rawClass(new StringGenericArrayType());

        assertThat(rawClass).isEqualTo(String[].class);
    }

    private static final class StringGenericArrayType implements GenericArrayType {
        @Override
        public Type getGenericComponentType() {
            return String.class;
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        RejectingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}

final class TypeFactoryTestTarget {
}
