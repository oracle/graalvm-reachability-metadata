/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.attoparser;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClassLoaderUtilsTest {

    @Test
    void findResourceAsStreamFallsBackToTheLibraryClassLoader() throws Exception {
        String resourceName = "org/attoparser/attoparser.properties";
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader();

        currentThread.setContextClassLoader(trackingClassLoader);
        try (InputStream inputStream = ClassLoaderUtils.findResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            assertThat(trackingClassLoader.requestCount).isEqualTo(1);
            assertThat(trackingClassLoader.lastRequestedResourceName).isEqualTo(resourceName);

            Properties properties = new Properties();
            properties.load(inputStream);
            assertThat(properties.getProperty("version")).isEqualTo("2.0.5.RELEASE");
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class TrackingClassLoader extends ClassLoader {

        private int requestCount;
        private String lastRequestedResourceName;

        private TrackingClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(final String name) {
            this.requestCount++;
            this.lastRequestedResourceName = name;
            return null;
        }

    }

}
