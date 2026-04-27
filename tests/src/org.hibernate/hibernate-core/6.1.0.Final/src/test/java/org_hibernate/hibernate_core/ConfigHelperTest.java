/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.ConfigHelper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigHelperTest {
    private static final String RESOURCE = "config-helper-test-resource.txt";
    private static final String ABSOLUTE_RESOURCE = "/" + RESOURCE;
    private static final String MISSING_RESOURCE = "missing-config-helper-test-resource.txt";

    @Test
    public void findAsResourceUsesContextClassLoader() throws Exception {
        URL resource = withContextClassLoader(
                new LeadingSlashFilteringClassLoader(ConfigHelperTest.class.getClassLoader()),
                () -> ConfigHelper.findAsResource(RESOURCE)
        );

        assertThat(resource).isNotNull();
    }

    @Test
    public void findAsResourceFallsBackThroughHibernateAndSystemClassLoaders() throws Exception {
        URL resource = withContextClassLoader(
                new EmptyResourceClassLoader(),
                () -> ConfigHelper.findAsResource(MISSING_RESOURCE)
        );

        assertThat(resource).isNull();
    }

    @Test
    public void getResourceAsStreamUsesContextClassLoader() throws Exception {
        InputStream stream = withContextClassLoader(
                new LeadingSlashFilteringClassLoader(ConfigHelperTest.class.getClassLoader()),
                () -> ConfigHelper.getResourceAsStream(RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getResourceAsStreamFallsBackToEnvironmentClassResource() throws Exception {
        InputStream stream = withContextClassLoader(
                new EmptyResourceClassLoader(),
                () -> ConfigHelper.getResourceAsStream(ABSOLUTE_RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getResourceAsStreamFallsBackToEnvironmentClassLoader() throws Exception {
        InputStream stream = withContextClassLoader(
                new EmptyResourceClassLoader(),
                () -> ConfigHelper.getResourceAsStream(RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getUserResourceAsStreamUsesContextClassLoaderResourceName() throws Exception {
        InputStream stream = withContextClassLoader(
                new LeadingSlashFilteringClassLoader(ConfigHelperTest.class.getClassLoader()),
                () -> ConfigHelper.getUserResourceAsStream(RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getUserResourceAsStreamRetriesContextClassLoaderWithoutLeadingSlash() throws Exception {
        InputStream stream = withContextClassLoader(
                new LeadingSlashFilteringClassLoader(ConfigHelperTest.class.getClassLoader()),
                () -> ConfigHelper.getUserResourceAsStream(ABSOLUTE_RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getUserResourceAsStreamFallsBackToEnvironmentClassLoaderResourceName() throws Exception {
        InputStream stream = withContextClassLoader(
                new EmptyResourceClassLoader(),
                () -> ConfigHelper.getUserResourceAsStream(RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    @Test
    public void getUserResourceAsStreamFallsBackToEnvironmentClassLoaderWithoutLeadingSlash() throws Exception {
        InputStream stream = withContextClassLoader(
                new EmptyResourceClassLoader(),
                () -> ConfigHelper.getUserResourceAsStream(ABSOLUTE_RESOURCE)
        );

        try (stream) {
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ResourceAction<T> action) throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            return action.run();
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private interface ResourceAction<T> {
        T run() throws Exception;
    }

    private static final class EmptyResourceClassLoader extends ClassLoader {
        private EmptyResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return null;
        }
    }

    private static final class LeadingSlashFilteringClassLoader extends ClassLoader {
        private LeadingSlashFilteringClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (name.startsWith("/")) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }
}
