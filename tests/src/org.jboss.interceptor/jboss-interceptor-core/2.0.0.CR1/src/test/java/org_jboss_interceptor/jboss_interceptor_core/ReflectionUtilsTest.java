/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_interceptor.jboss_interceptor_core;

import org.jboss.interceptor.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {

    @Test
    void loadsClassThroughThreadContextClassLoaderWhenPresent() throws Exception {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader testClassLoader = ReflectionUtilsTest.class.getClassLoader();
        Thread.currentThread().setContextClassLoader(testClassLoader);
        try {
            final Class<?> loadedClass = ReflectionUtils.classForName(String.class.getName());

            assertThat(ReflectionUtils.getThreadContextClassLoader()).isSameAs(testClassLoader);
            assertThat(loadedClass).isSameAs(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenThreadContextClassLoaderIsAbsent() throws Exception {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            final Class<?> loadedClass = ReflectionUtils.classForName(Integer.class.getName());

            assertThat(ReflectionUtils.getThreadContextClassLoader()).isNull();
            assertThat(loadedClass).isSameAs(Integer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
