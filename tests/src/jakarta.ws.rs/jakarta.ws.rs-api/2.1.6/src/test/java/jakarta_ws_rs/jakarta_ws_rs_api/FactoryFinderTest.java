/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryFinderTest extends ClientBuilder {
    private static final String CLIENT_BUILDER_PROPERTY = ClientBuilder.JAXRS_DEFAULT_CLIENT_BUILDER_PROPERTY;
    private static final String TEST_CLIENT_BUILDER_CLASS = FactoryFinderTest.class.getName();

    @Test
    public void newBuilderInstantiatesProviderWithCurrentClassLoaderWhenContextClassLoaderIsNull() {
        String previousProvider = System.getProperty(CLIENT_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(CLIENT_BUILDER_PROPERTY, TEST_CLIENT_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(null);

            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(FactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void newBuilderInstantiatesProviderWithContextClassLoader() {
        String previousProvider = System.getProperty(CLIENT_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(CLIENT_BUILDER_PROPERTY, TEST_CLIENT_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(FactoryFinderTest.class.getClassLoader());

            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(FactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void newBuilderRetriesWithCurrentClassLoaderWhenContextClassLoaderCannotLoadProvider() {
        String previousProvider = System.getProperty(CLIENT_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(CLIENT_BUILDER_PROPERTY, TEST_CLIENT_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());

            ClientBuilder builder = ClientBuilder.newBuilder();

            assertThat(builder).isInstanceOf(FactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(CLIENT_BUILDER_PROPERTY);
        } else {
            System.setProperty(CLIENT_BUILDER_PROPERTY, previousProvider);
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
    public Configuration getConfiguration() {
        return null;
    }

    @Override
    public ClientBuilder property(String name, Object value) {
        return this;
    }

    @Override
    public ClientBuilder register(Class<?> componentClass) {
        return this;
    }

    @Override
    public ClientBuilder register(Class<?> componentClass, int priority) {
        return this;
    }

    @Override
    public ClientBuilder register(Class<?> componentClass, Class<?>... contracts) {
        return this;
    }

    @Override
    public ClientBuilder register(Class<?> componentClass, Map<Class<?>, Integer> contracts) {
        return this;
    }

    @Override
    public ClientBuilder register(Object component) {
        return this;
    }

    @Override
    public ClientBuilder register(Object component, int priority) {
        return this;
    }

    @Override
    public ClientBuilder register(Object component, Class<?>... contracts) {
        return this;
    }

    @Override
    public ClientBuilder register(Object component, Map<Class<?>, Integer> contracts) {
        return this;
    }

    @Override
    public ClientBuilder withConfig(Configuration config) {
        return this;
    }

    @Override
    public ClientBuilder sslContext(SSLContext sslContext) {
        return this;
    }

    @Override
    public ClientBuilder keyStore(KeyStore keyStore, char[] password) {
        return this;
    }

    @Override
    public ClientBuilder trustStore(KeyStore trustStore) {
        return this;
    }

    @Override
    public ClientBuilder hostnameVerifier(HostnameVerifier verifier) {
        return this;
    }

    @Override
    public ClientBuilder executorService(ExecutorService executorService) {
        return this;
    }

    @Override
    public ClientBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        return this;
    }

    @Override
    public ClientBuilder connectTimeout(long timeout, TimeUnit unit) {
        return this;
    }

    @Override
    public ClientBuilder readTimeout(long timeout, TimeUnit unit) {
        return this;
    }

    @Override
    public Client build() {
        return null;
    }
}
