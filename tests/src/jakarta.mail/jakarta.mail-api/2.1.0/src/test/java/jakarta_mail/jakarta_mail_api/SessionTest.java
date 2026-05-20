/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.util.Properties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SessionTest {
    @Test
    void getTransportLoadsProviderWithContextClassLoader() {
        Session session = newSession();
        Provider provider = transportProvider("context-loader", Transport.class.getName());
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(SessionTest.class.getClassLoader());
            NoSuchProviderException exception = assertThrows(
                    NoSuchProviderException.class,
                    () -> session.getTransport(provider));

            assertEquals(provider.getProtocol(), exception.getMessage());
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void getTransportFallsBackToSessionClassLoaderWhenContextClassLoaderIsUnavailable() {
        Session session = newSession();
        Provider provider = transportProvider("session-loader", Transport.class.getName());
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(null);
            NoSuchProviderException exception = assertThrows(
                    NoSuchProviderException.class,
                    () -> session.getTransport(provider));

            assertEquals(provider.getProtocol(), exception.getMessage());
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void getTransportFallsBackToSystemClassLoaderAfterTypeMismatch() {
        Session session = newSession();
        Provider provider = transportProvider("mismatched-type", String.class.getName());
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();

        try {
            thread.setContextClassLoader(SessionTest.class.getClassLoader());
            NoSuchProviderException exception = assertThrows(
                    NoSuchProviderException.class,
                    () -> session.getTransport(provider));

            assertEquals(provider.getProtocol(), exception.getMessage());
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static Session newSession() {
        return Session.getInstance(new Properties());
    }

    private static Provider transportProvider(String protocol, String className) {
        return new Provider(Provider.Type.TRANSPORT, protocol, className, "GraalVM", null);
    }
}
