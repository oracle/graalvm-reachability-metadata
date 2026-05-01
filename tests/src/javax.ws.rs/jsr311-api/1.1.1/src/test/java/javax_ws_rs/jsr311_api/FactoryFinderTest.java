/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.jsr311_api;

import java.net.URL;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

    @BeforeEach
    void resetRuntimeDelegate() {
        RuntimeDelegate.setInstance(null);
    }

    @AfterEach
    void restoreThreadState() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void loadsProviderFromSystemResourceWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);
        System.clearProperty(RUNTIME_DELEGATE_PROPERTY);

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(SystemResourceRuntimeDelegate.class);
    }

    @Test
    void loadsProviderFromContextClassLoaderResource() {
        currentThread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());
        System.clearProperty(RUNTIME_DELEGATE_PROPERTY);

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(SystemResourceRuntimeDelegate.class);
    }

    @Test
    void fallsBackToApplicationClassLoaderWhenContextClassLoaderCannotLoadSystemPropertyProvider() {
        currentThread.setContextClassLoader(new IsolatedClassLoader());
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, SystemPropertyRuntimeDelegate.class.getName());

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(SystemPropertyRuntimeDelegate.class);
    }

    public static final class SystemResourceRuntimeDelegate extends BaseRuntimeDelegate {
    }

    public static final class SystemPropertyRuntimeDelegate extends BaseRuntimeDelegate {
    }

    public abstract static class BaseRuntimeDelegate extends RuntimeDelegate {
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
    }

    private static final class IsolatedClassLoader extends ClassLoader {
        private IsolatedClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }
}
