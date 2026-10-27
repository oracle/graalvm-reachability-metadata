/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.javax_ws_rs_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExtFactoryFinderTest extends RuntimeDelegate {
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/"
            + RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String TEST_RUNTIME_DELEGATE_CLASS = ExtFactoryFinderTest.class.getName();

    private ClassLoader originalContextClassLoader;
    private String originalRuntimeDelegateProperty;

    @BeforeEach
    void resetRuntimeDelegate() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalRuntimeDelegateProperty = System.getProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        RuntimeDelegate.setInstance(null);
        System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
    }

    @AfterEach
    void restoreRuntimeDelegateState() {
        RuntimeDelegate.setInstance(null);
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void loadsProviderWithSystemLookupWhenContextClassLoaderIsNull() throws Exception {
        runWithContextClassLoader(null, () -> {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, TEST_RUNTIME_DELEGATE_CLASS);

            RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

            assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
        });
    }

    @Test
    void loadsProviderFromContextClassLoaderServiceResource() {
        Thread.currentThread().setContextClassLoader(ExtFactoryFinderTest.class.getClassLoader());

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
    }

    @Test
    void retriesWithCurrentClassLoaderWhenContextClassLoaderCannotLoadProviderClass() throws Exception {
        runWithContextClassLoader(new RejectingProviderClassLoader(TEST_RUNTIME_DELEGATE_CLASS), () -> {
            RuntimeDelegate.setInstance(null);

            RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

            assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
        });
    }

    private static void runWithContextClassLoader(ClassLoader contextClassLoader, ThrowingRunnable action)
            throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread("ext-factory-finder-context-loader") {
            @Override
            public ClassLoader getContextClassLoader() {
                return contextClassLoader;
            }

            @Override
            public void run() {
                try {
                    action.run();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }
        };

        worker.start();
        worker.join(10_000L);
        if (worker.isAlive()) {
            worker.interrupt();
        }
        assertThat(worker.isAlive()).isFalse();

        Throwable throwable = failure.get();
        if (throwable instanceof Error error) {
            throw error;
        }
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable != null) {
            throw new AssertionError(throwable);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Override
    public UriBuilder createUriBuilder() {
        return null;
    }

    @Override
    public ResponseBuilder createResponseBuilder() {
        return null;
    }

    @Override
    public VariantListBuilder createVariantListBuilder() {
        return null;
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) {
        return null;
    }

    @Override
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        return null;
    }

    @Override
    public Link.Builder createLinkBuilder() {
        return null;
    }

    private static final class RejectingProviderClassLoader extends ClassLoader {
        private final String providerClassName;

        private RejectingProviderClassLoader(String providerClassName) {
            super(ExtFactoryFinderTest.class.getClassLoader());
            this.providerClassName = providerClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (providerClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!RUNTIME_DELEGATE_SERVICE.equals(name)) {
                return null;
            }
            return new ByteArrayInputStream((providerClassName + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }
}
