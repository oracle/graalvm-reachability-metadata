/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Test;

public class ResteasyProviderFactoryTest {
    @Test
    void newInstanceUsesContextClassLoaderToResolveProviderFactoryImplementation() {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(new ProviderFactoryClassLoader(originalContextClassLoader));

            assertThatThrownBy(ResteasyProviderFactory::newInstance)
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(InstantiationException.class);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static class ProviderFactoryClassLoader extends ClassLoader {
        ProviderFactoryClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if ("org.jboss.resteasy.core.providerfactory.ResteasyProviderFactoryImpl".equals(name)) {
                return AbstractProviderFactory.class;
            }
            return super.loadClass(name);
        }
    }

    public abstract static class AbstractProviderFactory extends ResteasyProviderFactory {
        public AbstractProviderFactory() {
        }
    }
}
