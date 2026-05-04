/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.jsr311_api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String RUNTIME_DELEGATE_SERVICE = "META-INF/services/"
            + RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalRuntimeDelegateProperty = System.getProperty(
            RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);

    @BeforeEach
    void resetRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
        System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
    }

    @AfterEach
    void restoreRuntimeDelegateState() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void loadsProviderFromSystemResourcesWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(RuntimeDelegateProvider.class);
    }

    @Test
    void loadsProviderFromContextClassLoaderResource() {
        currentThread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(RuntimeDelegateProvider.class);
    }

    @Test
    void fallsBackToSystemClassLookupWhenContextClassLoaderCannotLoadProviderClass() {
        currentThread.setContextClassLoader(new SystemFallbackProviderClassLoader());

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(RuntimeDelegateProvider.class);
    }

    public static final class RuntimeDelegateProvider extends RuntimeDelegate {
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
        public <T> T createEndpoint(Application application, Class<T> endpointType)
                throws IllegalArgumentException, UnsupportedOperationException {
            return null;
        }

        @Override
        public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
            return null;
        }
    }

    private static final class SystemFallbackProviderClassLoader extends ClassLoader {
        private SystemFallbackProviderClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (RUNTIME_DELEGATE_SERVICE.equals(name)) {
                byte[] providerName = RuntimeDelegateProvider.class.getName().getBytes(StandardCharsets.UTF_8);
                return new ByteArrayInputStream(providerName);
            }
            return null;
        }
    }
}
