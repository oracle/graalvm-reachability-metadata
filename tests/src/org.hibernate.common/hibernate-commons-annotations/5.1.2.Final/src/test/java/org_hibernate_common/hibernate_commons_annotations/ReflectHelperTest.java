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
    public void classForNameUsesContextClassLoaderWhenAvailable() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ReflectHelperTest.class.getClassLoader());
        try {
            Class<?> resolvedClass = ReflectHelper.classForName(String.class.getName(), ReflectHelperTest.class);

            assertThat(resolvedClass).isEqualTo(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void classForNameFallsBackToCallerClassLoaderWithoutContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Class<?> resolvedClass = ReflectHelper.classForName(String.class.getName(), ReflectHelperTest.class);

            assertThat(resolvedClass).isEqualTo(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
