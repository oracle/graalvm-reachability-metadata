/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_client_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.junit.jupiter.api.Test;

public class ProxyBuilderTest {

    private static final String PROXY_BUILDER_IMPL_CLASS_NAME =
            "org.jboss.resteasy.client.jaxrs.internal.proxy.ProxyBuilderImpl";

    @Test
    void builderCreatesImplementationUsingContextClassLoader() {
        ProxyBuilder<TestResourceApi> builder = ProxyBuilder.builder(TestResourceApi.class, null);

        assertThat(builder).isNotNull();
        assertThat(builder.getClass().getName()).isEqualTo(PROXY_BUILDER_IMPL_CLASS_NAME);
    }

    @Test
    void builderFallsBackToProxyBuilderClassLoaderWhenContextClassLoaderCannotLoadImplementation() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new ProxyBuilderImplHidingClassLoader(originalContextClassLoader));

        try {
            ProxyBuilder<TestResourceApi> builder = ProxyBuilder.builder(TestResourceApi.class, null);

            assertThat(builder).isNotNull();
            assertThat(builder.getClass().getName()).isEqualTo(PROXY_BUILDER_IMPL_CLASS_NAME);
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private interface TestResourceApi {
    }

    private static final class ProxyBuilderImplHidingClassLoader extends ClassLoader {

        private final ClassLoader delegate;

        private ProxyBuilderImplHidingClassLoader(final ClassLoader delegate) {
            super(null);
            this.delegate = delegate;
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (PROXY_BUILDER_IMPL_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            if (delegate == null) {
                return ClassLoader.getSystemClassLoader().loadClass(name);
            }
            return delegate.loadClass(name);
        }
    }
}
