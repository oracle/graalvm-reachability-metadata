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
    void loadsProviderFromSystemServiceResourceWhenContextClassLoaderIsNull() {
        currentThread.setContextClassLoader(null);

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
    }

    @Test
    void loadsProviderFromContextClassLoaderServiceResource() {
        currentThread.setContextClassLoader(ExtFactoryFinderTest.class.getClassLoader());

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
    }

    @Test
    void retriesWithCurrentClassLoaderWhenContextClassLoaderCannotLoadProviderClass() {
        currentThread.setContextClassLoader(new ServiceOnlyClassLoader(TEST_RUNTIME_DELEGATE_CLASS));

        RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();

        assertThat(runtimeDelegate).isInstanceOf(ExtFactoryFinderTest.class);
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

    private static final class ServiceOnlyClassLoader extends ClassLoader {
        private final String providerClassName;

        private ServiceOnlyClassLoader(String providerClassName) {
            super(null);
            this.providerClassName = providerClassName;
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
