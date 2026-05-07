/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.Variant.VariantListBuilder;
import javax.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtFactoryFinderTest extends RuntimeDelegate {
    private static final String RUNTIME_DELEGATE_PROPERTY = RuntimeDelegate.JAXRS_RUNTIME_DELEGATE_PROPERTY;
    private static final String TEST_RUNTIME_DELEGATE_CLASS = ExtFactoryFinderTest.class.getName();

    @Test
    public void getInstanceInstantiatesProviderWithCurrentClassLoaderWhenContextClassLoaderIsNull() {
        String previousProvider = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, TEST_RUNTIME_DELEGATE_CLASS);
            Thread.currentThread().setContextClassLoader(null);

            RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ExtFactoryFinderTest.class);
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void getInstanceInstantiatesProviderWithContextClassLoader() {
        String previousProvider = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, TEST_RUNTIME_DELEGATE_CLASS);
            Thread.currentThread().setContextClassLoader(ExtFactoryFinderTest.class.getClassLoader());

            RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ExtFactoryFinderTest.class);
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void getInstanceRetriesWithCurrentClassLoaderWhenContextClassLoaderCannotLoadProvider() {
        String previousProvider = System.getProperty(RUNTIME_DELEGATE_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            RuntimeDelegate.setInstance(null);
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, TEST_RUNTIME_DELEGATE_CLASS);
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());

            RuntimeDelegate delegate = RuntimeDelegate.getInstance();

            assertThat(delegate).isInstanceOf(ExtFactoryFinderTest.class);
        } finally {
            RuntimeDelegate.setInstance(null);
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(RUNTIME_DELEGATE_PROPERTY);
        } else {
            System.setProperty(RUNTIME_DELEGATE_PROPERTY, previousProvider);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
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
}
