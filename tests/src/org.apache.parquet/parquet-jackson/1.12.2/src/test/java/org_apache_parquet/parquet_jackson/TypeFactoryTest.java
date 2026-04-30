/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.type.TypeFactory;

public class TypeFactoryTest {
    @Test
    void findsClassThroughConfiguredClassLoader() throws Exception {
        ClassLoader testClassLoader = TypeFactoryTest.class.getClassLoader();
        TypeFactory typeFactory = TypeFactory.defaultInstance().withClassLoader(testClassLoader);

        Class<?> loadedClass = typeFactory.findClass(ArrayList.class.getName());

        assertThat(loadedClass).isEqualTo(ArrayList.class);
    }

    @Test
    void fallsBackToSystemClassLoadingWhenNoClassLoaderIsAvailable() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        TypeFactory typeFactory = TypeFactory.defaultInstance().withClassLoader(null);

        currentThread.setContextClassLoader(null);
        try {
            Class<?> loadedClass = typeFactory.findClass(HashMap.class.getName());

            assertThat(loadedClass).isEqualTo(HashMap.class);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }
}
