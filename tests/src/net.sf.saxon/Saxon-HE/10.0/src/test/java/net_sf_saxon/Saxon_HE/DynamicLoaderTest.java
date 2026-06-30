/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.trans.DynamicLoader;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicLoaderTest {
    private static final String STANDARD_LOGGER_CLASS_NAME = "net.sf.saxon.lib.StandardLogger";
    private static final String SAXON_DATA_RESOURCE = "net/sf/saxon/data/profile.xsl";

    @Test
    void loadsSaxonClassUsingClassForNameFallbackWhenProvidedClassLoaderRejectsIt() throws Exception {
        DynamicLoader loader = new DynamicLoader();

        Class<?> loadedClass = loader.getClass(
                STANDARD_LOGGER_CLASS_NAME,
                null,
                new RejectingClassLoader());

        assertThat(loadedClass).isEqualTo(StandardLogger.class);
    }

    @Test
    void instantiatesSaxonClassWithoutTracing() throws Exception {
        DynamicLoader loader = new DynamicLoader();

        Object instance = loader.getInstance(STANDARD_LOGGER_CLASS_NAME, StandardLogger.class.getClassLoader());

        assertThat(instance).isInstanceOf(StandardLogger.class);
    }

    @Test
    void instantiatesSaxonClassWithTracing() throws Exception {
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        StandardLogger traceLogger = new StandardLogger(new PrintStream(log, true, StandardCharsets.UTF_8));
        DynamicLoader loader = new DynamicLoader();

        Object instance = loader.getInstance(
                STANDARD_LOGGER_CLASS_NAME,
                traceLogger,
                StandardLogger.class.getClassLoader());

        assertThat(instance).isInstanceOf(StandardLogger.class);
        assertThat(log.toString(StandardCharsets.UTF_8)).contains("Loading " + STANDARD_LOGGER_CLASS_NAME);
    }

    @Test
    void opensSaxonResourceThroughConfiguredClassLoader() throws Exception {
        DynamicLoader loader = new DynamicLoader();
        loader.setClassLoader(StandardLogger.class.getClassLoader());

        try (InputStream resource = loader.getResourceAsStream(SAXON_DATA_RESOURCE)) {
            assertThat(resource).isNotNull();
            assertThat(resource.read()).isNotEqualTo(-1);
        }
    }

    private static final class RejectingClassLoader extends ClassLoader {
        private RejectingClassLoader() {
            super(StandardLogger.class.getClassLoader());
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
