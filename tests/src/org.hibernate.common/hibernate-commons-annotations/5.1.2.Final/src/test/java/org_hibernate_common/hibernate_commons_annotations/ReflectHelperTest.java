/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.common.util.ReflectHelper;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class ReflectHelperTest {
    @Test
    public void resolvesClassWithContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = originalClassLoader != null ? originalClassLoader : ReflectHelperTest.class.getClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class<?> resolvedClass = ReflectHelper.classForName(String.class.getName(), ReflectHelperTest.class);

            assertThat(resolvedClass).isSameAs(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void fallsBackToCallerClassLoaderWhenContextClassLoaderIsMissing() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            Class<?> resolvedClass = ReflectHelper.classForName(Integer.class.getName(), ReflectHelperTest.class);

            assertThat(resolvedClass).isSameAs(Integer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
