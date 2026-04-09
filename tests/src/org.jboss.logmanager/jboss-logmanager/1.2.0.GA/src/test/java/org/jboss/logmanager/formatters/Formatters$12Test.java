/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager.formatters;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.logging.Level;

import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

class Formatters$12Test {

    @Test
    void extendedExceptionFormattingUsesAllClassResolutionFallbacks() throws Exception {
        final Thread thread = Thread.currentThread();
        final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        final ClassLoader contextClassLoader = new ResourceFreeClassLoader(
                ContextLoadedFrame.class.getName(),
                readClassBytes(ContextLoadedFrame.class));
        final Throwable thrown = new IllegalStateException("boom");
        final StackTraceElement[] stackTrace = {
                new StackTraceElement(ContextLoadedFrame.class.getName(), "invoke", "ContextLoadedFrame.java", 12),
                new StackTraceElement("org.jboss.logmanager.formatters.MissingFrame", "invoke", "MissingFrame.java", 24)
        };
        final ExtLogRecord record = new ExtLogRecord(Level.SEVERE, "message", Formatters$12Test.class.getName());
        final PatternFormatter formatter = new PatternFormatter("%E");

        thrown.setStackTrace(stackTrace);
        record.setThrown(thrown);

        try {
            thread.setContextClassLoader(contextClassLoader);

            final String formatted = formatter.format(record);

            assertThat(formatted)
                    .contains("java.lang.IllegalStateException: boom")
                    .contains(ContextLoadedFrame.class.getName())
                    .contains("org.jboss.logmanager.formatters.MissingFrame");
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static byte[] readClassBytes(final Class<?> type) throws IOException {
        final String resourceName = type.getName().replace('.', '/') + ".class";
        final InputStream resourceStream = type.getClassLoader().getResourceAsStream(resourceName);

        assertThat(resourceStream).isNotNull();
        try (resourceStream) {
            return resourceStream.readAllBytes();
        }
    }

    private static final class ResourceFreeClassLoader extends ClassLoader {
        private final String className;
        private final byte[] classBytes;

        private ResourceFreeClassLoader(final String className, final byte[] classBytes) {
            super(null);
            this.className = className;
            this.classBytes = classBytes;
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if (className.equals(name)) {
                return defineClass(name, classBytes, 0, classBytes.length);
            }
            throw new ClassNotFoundException(name);
        }

        @Override
        public URL getResource(final String name) {
            return null;
        }
    }
}

final class ContextLoadedFrame {
    private ContextLoadedFrame() {
    }
}
