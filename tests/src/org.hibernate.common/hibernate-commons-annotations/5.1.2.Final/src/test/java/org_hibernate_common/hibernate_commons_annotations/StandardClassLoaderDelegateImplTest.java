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
    public void classForNameUsesContextClassLoaderWhenAvailable() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(StandardClassLoaderDelegateImplTest.class.getClassLoader());
        try {
            Class<?> resolvedClass = StandardClassLoaderDelegateImpl.INSTANCE.classForName(
                    StandardClassLoaderDelegateImpl.class.getName());

            assertThat(resolvedClass).isEqualTo(StandardClassLoaderDelegateImpl.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    public void classForNameFallsBackToHibernateCommonsAnnotationsClassLoaderWithoutContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            Class<?> resolvedClass = StandardClassLoaderDelegateImpl.INSTANCE.classForName(
                    StandardClassLoaderDelegateImpl.class.getName());

            assertThat(resolvedClass).isEqualTo(StandardClassLoaderDelegateImpl.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
