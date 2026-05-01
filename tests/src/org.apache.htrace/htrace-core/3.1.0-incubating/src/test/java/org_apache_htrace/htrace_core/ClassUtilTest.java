/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilTest {
    @Test
    void findClassUsesThreadContextClassLoaderWhenPresent() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        ClassLoader testClassLoader = ClassUtilTest.class.getClassLoader();

        currentThread.setContextClassLoader(testClassLoader);
        try {
            Class<?> resolvedClass = ClassUtil.findClass(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void findClassFallsBackWhenNoThreadContextClassLoaderIsAvailable() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();

        currentThread.setContextClassLoader(null);
        try {
            Class<?> resolvedClass = ClassUtil.findClass(Integer.class.getName());

            assertThat(resolvedClass).isSameAs(Integer.class);
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void createInstanceUsesDefaultConstructor() {
        DefaultConstructibleBean createdBean = ClassUtil.createInstance(DefaultConstructibleBean.class, false);

        assertThat(createdBean.value()).isEqualTo("created");
    }

    public static class DefaultConstructibleBean {
        private final String value;

        public DefaultConstructibleBean() {
            this.value = "created";
        }

        public String value() {
            return value;
        }
    }
}
