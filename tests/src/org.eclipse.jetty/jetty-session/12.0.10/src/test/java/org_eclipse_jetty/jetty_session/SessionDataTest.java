/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.session.SessionData;
import org.eclipse.jetty.util.ClassLoadingObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionDataTest {
    @Test
    void serializesAndDeserializesAttributesWithContextClassLoader() throws Exception {
        SessionData data = new SessionData("session-id", "/test", "localhost", 10L, 20L, 15L, 30_000L);
        data.setAttribute("greeting", "hello");

        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        DelegatingClassLoader contextClassLoader = new DelegatingClassLoader(originalClassLoader);
        byte[] serializedAttributes;
        try {
            Thread.currentThread().setContextClassLoader(contextClassLoader);
            serializedAttributes = serializeAttributes(data);

            assertThat(contextClassLoader.getRequestedClassNames()).contains(String.class.getName());

            SessionData restored = new SessionData("session-id", "/test", "localhost", 10L, 20L, 15L, 30_000L);
            ByteArrayInputStream bytes = new ByteArrayInputStream(serializedAttributes);
            try (ClassLoadingObjectInputStream input = new ClassLoadingObjectInputStream(bytes)) {
                SessionData.deserializeAttributes(restored, input);
            }

            assertThat(restored.getAllAttributes())
                    .containsEntry("greeting", "hello")
                    .hasSize(1);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static byte[] serializeAttributes(SessionData data) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            SessionData.serializeAttributes(data, output);
        }
        return bytes.toByteArray();
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private final List<String> requestedClassNames = new ArrayList<>();

        private DelegatingClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requestedClassNames.add(name);
            return super.loadClass(name);
        }

        private List<String> getRequestedClassNames() {
            return requestedClassNames;
        }
    }
}
