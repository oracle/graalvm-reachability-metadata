/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logging.commons_logging_jboss_logging;

import org.apache.commons.logging.impl.SimpleLog;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLogAnonymous1Test {
    private static final String SIMPLE_LOG_PROPERTIES = "simplelog.properties";
    private static final String TEST_PROPERTIES = "# supplied by the test context class loader\n";

    @Test
    void readsConfigurationThroughContextAndSystemClassLoaders() throws Throwable {
        MethodHandle resourceReader = getSimpleLogResourceReader();
        ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        SimpleLogPropertiesClassLoader propertiesClassLoader = new SimpleLogPropertiesClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(propertiesClassLoader);
            try (InputStream inputStream = (InputStream) resourceReader.invoke(SIMPLE_LOG_PROPERTIES)) {
                assertThat(inputStream).isNotNull();
                assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(TEST_PROPERTIES);
            }
            assertThat(propertiesClassLoader.requestedSimpleLogProperties()).isTrue();

            Thread.currentThread().setContextClassLoader(null);
            InputStream missingSystemResource = (InputStream) resourceReader.invoke(
                    "org_jboss_logging/commons_logging_jboss_logging/missing-simplelog.properties"
            );

            assertThat(missingSystemResource).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }

    private static MethodHandle getSimpleLogResourceReader() throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(SimpleLog.class, MethodHandles.lookup());
        return lookup.findStatic(
                SimpleLog.class,
                "getResourceAsStream",
                MethodType.methodType(InputStream.class, String.class)
        );
    }

    private static final class SimpleLogPropertiesClassLoader extends ClassLoader {
        private boolean requestedSimpleLogProperties;

        @Override
        public InputStream getResourceAsStream(String name) {
            if (SIMPLE_LOG_PROPERTIES.equals(name)) {
                requestedSimpleLogProperties = true;
                return new ByteArrayInputStream(TEST_PROPERTIES.getBytes(StandardCharsets.UTF_8));
            }
            return super.getResourceAsStream(name);
        }

        private boolean requestedSimpleLogProperties() {
            return requestedSimpleLogProperties;
        }
    }
}
