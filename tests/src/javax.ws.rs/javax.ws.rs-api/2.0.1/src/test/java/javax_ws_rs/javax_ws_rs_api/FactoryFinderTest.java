/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MultivaluedHashMap;

import org.junit.jupiter.api.Test;

public class FactoryFinderTest {
    @Test
    void newBuilderUsesContextClassLoaderAndSystemPropertyProvider() {
        withProviderProperty(() -> {
            Thread currentThread = Thread.currentThread();
            ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());
            try {
                assertAbstractClientBuilderProviderFailsToInstantiate();
            } finally {
                currentThread.setContextClassLoader(previousContextClassLoader);
            }
        });
    }

    @Test
    void newBuilderFallsBackToCurrentClassLoaderWhenContextClassLoaderRejectsProvider() {
        withProviderProperty(() -> {
            Thread currentThread = Thread.currentThread();
            ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(new RejectingProviderClassLoader(previousContextClassLoader));
            try {
                assertAbstractClientBuilderProviderFailsToInstantiate();
            } finally {
                currentThread.setContextClassLoader(previousContextClassLoader);
            }
        });
    }

    @Test
    void newBuilderUsesSystemClassLoaderWhenContextClassLoaderIsUnavailable() {
        withProviderProperty(() -> {
            Thread currentThread = Thread.currentThread();
            ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
            currentThread.setContextClassLoader(null);
            try {
                assertAbstractClientBuilderProviderFailsToInstantiate();
            } finally {
                currentThread.setContextClassLoader(previousContextClassLoader);
            }
        });
    }

    @Test
    void newBuilderInstantiatesConcreteProviderClassBeforeTypeCheck() {
        withProviderProperty(MultivaluedHashMap.class.getName(), () ->
                assertThatThrownBy(ClientBuilder::newBuilder)
                        .isInstanceOf(LinkageError.class)
                        .hasMessageContaining("attempting to cast"));
    }

    private static void assertAbstractClientBuilderProviderFailsToInstantiate() {
        assertThatThrownBy(ClientBuilder::newBuilder)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(ClassNotFoundException.class)
                .hasStackTraceContaining(
                        "Provider javax.ws.rs.client.ClientBuilder could not be instantiated");
    }

    private static synchronized void withProviderProperty(Runnable action) {
        withProviderProperty(ClientBuilder.class.getName(), action);
    }

    private static synchronized void withProviderProperty(String providerClassName, Runnable action) {
        String previousProvider = System.getProperty(ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY);
        System.setProperty(ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY, providerClassName);
        try {
            action.run();
        } finally {
            if (previousProvider == null) {
                System.clearProperty(ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY);
            } else {
                System.setProperty(ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY, previousProvider);
            }
        }
    }

    private static final class RejectingProviderClassLoader extends ClassLoader {
        private RejectingProviderClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (ClientBuilder.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
