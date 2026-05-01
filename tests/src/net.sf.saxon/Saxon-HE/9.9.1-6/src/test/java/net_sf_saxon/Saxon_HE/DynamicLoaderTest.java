/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.sf.saxon.Configuration;
import net.sf.saxon.trans.DynamicLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynamicLoaderTest {
    private static final String RESOURCE_NAME = "dynamic-loader-resource.txt";
    private static final byte[] RESOURCE_BYTES = "loaded by DynamicLoader".getBytes(StandardCharsets.UTF_8);

    @Test
    void getClassFallsBackToClassForNameWhenSuppliedClassLoaderCannotLoadClass() throws Exception {
        DynamicLoader dynamicLoader = new DynamicLoader();
        FailingClassLoader classLoader = new FailingClassLoader();

        Class<?> loadedClass = dynamicLoader.getClass(String.class.getName(), null, classLoader);

        assertThat(loadedClass).isSameAs(String.class);
        assertThat(classLoader.loadedClassNames).containsExactly(String.class.getName());
    }

    @Test
    void getInstanceCreatesKnownClassWithTwoArgumentOverload() throws Exception {
        DynamicLoader dynamicLoader = new DynamicLoader();

        Object instance = dynamicLoader.getInstance(Configuration.class.getName(), null);

        assertThat(instance).isExactlyInstanceOf(Configuration.class);
    }

    @Test
    void getInstanceCreatesKnownClassWithTracingOverload() throws Exception {
        DynamicLoader dynamicLoader = new DynamicLoader();

        Object instance = dynamicLoader.getInstance(Configuration.class.getName(), null, null);

        assertThat(instance).isExactlyInstanceOf(Configuration.class);
    }

    @Test
    void getResourceAsStreamReadsFromConfiguredClassLoader() throws Exception {
        DynamicLoader dynamicLoader = new DynamicLoader();
        InMemoryResourceClassLoader classLoader = new InMemoryResourceClassLoader();
        dynamicLoader.setClassLoader(classLoader);

        try (InputStream input = dynamicLoader.getResourceAsStream(RESOURCE_NAME)) {
            assertThat(input).isNotNull();
            assertThat(input.readAllBytes()).isEqualTo(RESOURCE_BYTES);
        }
        assertThat(classLoader.resourceNames).containsExactly(RESOURCE_NAME);
    }

    private static final class FailingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        private FailingClassLoader() {
            super(null);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            loadedClassNames.add(name);
            throw new ClassNotFoundException(name);
        }
    }

    private static final class InMemoryResourceClassLoader extends ClassLoader {
        private final List<String> resourceNames = new ArrayList<>();

        private InMemoryResourceClassLoader() {
            super(null);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            resourceNames.add(name);
            if (RESOURCE_NAME.equals(name)) {
                return new ByteArrayInputStream(RESOURCE_BYTES);
            }
            return null;
        }
    }
}
