/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_ws_rs.jsr311_api;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import javax.ws.rs.ext.RuntimeDelegate.HeaderDelegate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest extends RuntimeDelegate {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String PROVIDER_CLASS_NAME = FactoryFinderTest.class.getName();

    private final Thread currentThread = Thread.currentThread();
    private final ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
    private final String originalRuntimeDelegateProperty = System.getProperty(RUNTIME_DELEGATE_PROPERTY);

    @AfterEach
    void restoreRuntimeDelegateLookupState() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(originalContextClassLoader);
        if (originalRuntimeDelegateProperty == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, originalRuntimeDelegateProperty);
        }
    }

    @Test
    void loadsProviderAfterCheckingSystemResourcesWhenContextClassLoaderIsNull() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(null);
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, PROVIDER_CLASS_NAME);

        RuntimeDelegate delegate = RuntimeDelegate.getInstance();

        assertThat(delegate).isInstanceOf(FactoryFinderTest.class);
    }

    @Test
    void loadsProviderThroughTheContextClassLoader() {
        RuntimeDelegate.setInstance(null);
        currentThread.setContextClassLoader(FactoryFinderTest.class.getClassLoader());
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, PROVIDER_CLASS_NAME);

        RuntimeDelegate delegate = RuntimeDelegate.getInstance();

        assertThat(delegate).isInstanceOf(FactoryFinderTest.class);
    }

    @Test
    void fallsBackToApplicationClassLoaderWhenContextClassLoaderCannotLoadProvider() {
        RuntimeDelegate.setInstance(null);
        RejectingProviderClassLoader rejectingClassLoader = new RejectingProviderClassLoader(
                FactoryFinderTest.class.getClassLoader(), PROVIDER_CLASS_NAME);
        currentThread.setContextClassLoader(rejectingClassLoader);
        System.setProperty(RUNTIME_DELEGATE_PROPERTY, PROVIDER_CLASS_NAME);

        RuntimeDelegate delegate = RuntimeDelegate.getInstance();

        assertThat(rejectingClassLoader.rejectedProviderLoad).isTrue();
        assertThat(delegate).isInstanceOf(FactoryFinderTest.class);
    }

    @Override
    public UriBuilder createUriBuilder() {
        throw new UnsupportedOperationException("Not used in tests");
    }

    @Override
    public ResponseBuilder createResponseBuilder() {
        throw new UnsupportedOperationException("Not used in tests");
    }

    @Override
    public VariantListBuilder createVariantListBuilder() {
        throw new UnsupportedOperationException("Not used in tests");
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) {
        throw new UnsupportedOperationException("Not used in tests");
    }

    @Override
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
        throw new UnsupportedOperationException("Not used in tests");
    }

    private static final class RejectingProviderClassLoader extends ClassLoader {
        private final String rejectedClassName;
        private boolean rejectedProviderLoad;

        private RejectingProviderClassLoader(ClassLoader parent, String rejectedClassName) {
            super(parent);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                rejectedProviderLoad = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
