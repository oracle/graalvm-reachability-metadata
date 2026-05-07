/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_ws_rs.jakarta_ws_rs_api;

import java.util.concurrent.TimeUnit;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.SseEventSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SseFactoryFinderTest extends SseEventSource.Builder {
    private static final String SSE_BUILDER_PROPERTY =
            SseEventSource.Builder.JAXRS_DEFAULT_SSE_BUILDER_PROPERTY;
    private static final String TEST_SSE_BUILDER_CLASS = SseFactoryFinderTest.class.getName();

    @Test
    public void targetInstantiatesProviderWithCurrentClassLoaderWhenContextClassLoaderIsNull() {
        String previousProvider = System.getProperty(SSE_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(SSE_BUILDER_PROPERTY, TEST_SSE_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(null);

            SseEventSource.Builder builder = SseEventSource.target(null);

            assertThat(builder).isInstanceOf(SseFactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void targetInstantiatesProviderWithContextClassLoader() {
        String previousProvider = System.getProperty(SSE_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(SSE_BUILDER_PROPERTY, TEST_SSE_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(SseFactoryFinderTest.class.getClassLoader());

            SseEventSource.Builder builder = SseEventSource.target(null);

            assertThat(builder).isInstanceOf(SseFactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    @Test
    public void targetRetriesWithCurrentClassLoaderWhenContextClassLoaderCannotLoadProvider() {
        String previousProvider = System.getProperty(SSE_BUILDER_PROPERTY);
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            System.setProperty(SSE_BUILDER_PROPERTY, TEST_SSE_BUILDER_CLASS);
            Thread.currentThread().setContextClassLoader(new RejectingClassLoader());

            SseEventSource.Builder builder = SseEventSource.target(null);

            assertThat(builder).isInstanceOf(SseFactoryFinderTest.class);
        } finally {
            restoreProviderProperty(previousProvider);
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(SSE_BUILDER_PROPERTY);
        } else {
            System.setProperty(SSE_BUILDER_PROPERTY, previousProvider);
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
    protected SseEventSource.Builder target(WebTarget endpoint) {
        return this;
    }

    @Override
    public SseEventSource.Builder reconnectingEvery(long delay, TimeUnit unit) {
        return this;
    }

    @Override
    public SseEventSource build() {
        return null;
    }
}
