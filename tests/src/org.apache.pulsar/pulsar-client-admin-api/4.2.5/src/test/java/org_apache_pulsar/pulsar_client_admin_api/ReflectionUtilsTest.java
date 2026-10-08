/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_admin_api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.client.admin.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    private static final String CONTEXT_ONLY_BUILDER_CLASS_NAME = "example.ContextOnlyPulsarBuilder";

    @Test
    void newBuilderInvokesStaticBuilderLoadedFromDefaultClassLoader() {
        final TestBuilder builder = ReflectionUtils.newBuilder(DefaultVisibleBuilder.class.getName());

        assertThat(builder.getSource()).isEqualTo("default");
    }

    @Test
    void newBuilderFallsBackToContextClassLoader() {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(new ContextOnlyBuilderClassLoader(previousContextClassLoader));
        try {
            final TestBuilder builder = ReflectionUtils.newBuilder(CONTEXT_ONLY_BUILDER_CLASS_NAME);

            assertThat(builder.getSource()).isEqualTo("context");
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
        }
    }

    public static final class DefaultVisibleBuilder {
        private DefaultVisibleBuilder() {
        }

        public static TestBuilder builder() {
            return new TestBuilder("default");
        }
    }

    public static final class ContextOnlyBuilder {
        private ContextOnlyBuilder() {
        }

        public static TestBuilder builder() {
            return new TestBuilder("context");
        }
    }

    public static final class TestBuilder {
        private final String source;

        public TestBuilder(final String source) {
            this.source = source;
        }

        public String getSource() {
            return source;
        }
    }

    private static final class ContextOnlyBuilderClassLoader extends ClassLoader {
        ContextOnlyBuilderClassLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            if (CONTEXT_ONLY_BUILDER_CLASS_NAME.equals(name)) {
                return ContextOnlyBuilder.class;
            }
            return super.loadClass(name);
        }
    }
}
