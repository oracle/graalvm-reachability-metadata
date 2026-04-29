/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_common.hibernate_commons_annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.annotations.common.util.StandardClassLoaderDelegateImpl;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class StandardClassLoaderDelegateImplTest {
    @Test
    public void resolvesClassWithThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader contextClassLoader = ClassLoader.getPlatformClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);

            Class<?> resolvedClass = StandardClassLoaderDelegateImpl.INSTANCE.classForName(String.class.getName());

            assertThat(resolvedClass).isSameAs(String.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void fallsBackToHibernateClassLoaderWhenContextClassLoaderIsMissing() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(null);

            Class<?> resolvedClass = StandardClassLoaderDelegateImpl.INSTANCE.classForName(Integer.class.getName());

            assertThat(resolvedClass).isSameAs(Integer.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
