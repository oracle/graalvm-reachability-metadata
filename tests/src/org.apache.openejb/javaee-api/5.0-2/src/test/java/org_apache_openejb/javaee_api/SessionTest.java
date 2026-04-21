/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_openejb.javaee_api;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Provider;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.URLName;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SessionTest {

    @Test
    void loadsProvidersAddressMapsAndTransportFromContextClassLoader(@TempDir Path tempDir) throws Exception {
        writeResource(tempDir, "META-INF/javamail.default.providers", """
                protocol=session-default; type=transport; class=%s; vendor=tests; version=1
                """.formatted(TestTransport.class.getName()));
        writeResource(tempDir, "META-INF/javamail.providers", """
                protocol=session-override; type=transport; class=%s; vendor=tests; version=1
                """.formatted(TestTransport.class.getName()));
        writeResource(tempDir, "META-INF/javamail.default.address.map", "session-address=session-default\n");
        writeResource(tempDir, "META-INF/javamail.address.map", "session-address=session-override\n");

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL() }, getClass().getClassLoader())) {
            Session session = withContextClassLoader(classLoader, () -> Session.getInstance(new Properties()));

            Provider[] providers = withContextClassLoader(classLoader, session::getProviders);
            Transport transport = withContextClassLoader(
                    classLoader,
                    () -> session.getTransport(new TestAddress("session-address", "native-image@example.test")));

            Assertions.assertThat(providers)
                    .extracting(Provider::getProtocol)
                    .contains("session-default", "session-override");
            Assertions.assertThat(transport).isInstanceOf(TestTransport.class);
            Assertions.assertThat(withContextClassLoader(classLoader, () -> session.getProvider("session-override").getClassName()))
                    .isEqualTo(TestTransport.class.getName());
        }
    }

    private static void writeResource(Path rootDirectory, String relativePath, String content) throws Exception {
        Path resourcePath = rootDirectory.resolve(relativePath);
        Files.createDirectories(resourcePath.getParent());
        Files.writeString(resourcePath, content, StandardCharsets.UTF_8);
    }

    private static <T> T withContextClassLoader(ClassLoader classLoader, ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(previousClassLoader);
        }
    }

    public static final class TestAddress extends Address {
        private final String type;
        private final String value;

        public TestAddress(String type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String getType() {
            return type;
        }

        @Override
        public String toString() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TestAddress testAddress)) {
                return false;
            }
            return Objects.equals(type, testAddress.type) && Objects.equals(value, testAddress.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }
    }

    public static final class TestTransport extends Transport {

        public TestTransport(Session session, URLName urlName) {
            super(session, urlName);
        }

        @Override
        public void sendMessage(Message message, Address[] addresses) {
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
