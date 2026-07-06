/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import java.util.Properties;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.NoSuchProviderException;
import jakarta.mail.Provider;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.URLName;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SessionTest {

    @Test
    void createsTransportUsingContextClassLoaderProvider() throws Exception {
        Session session = Session.getInstance(new Properties());
        Provider provider = transportProvider("context-loader-transport", TestTransport.class);

        Transport transport = withContextClassLoader(
                SessionTest.class.getClassLoader(),
                () -> session.getTransport(provider));

        assertThat(transport).isInstanceOf(TestTransport.class);
        assertThat(transport.getURLName().getProtocol()).isEqualTo("context-loader-transport");
    }

    @Test
    void createsTransportUsingSessionClassLoaderWhenContextClassLoaderIsNull() throws Exception {
        Session session = Session.getInstance(new Properties());
        Provider provider = transportProvider("session-loader-transport", TestTransport.class);

        Transport transport = withContextClassLoader(null, () -> session.getTransport(provider));

        assertThat(transport).isInstanceOf(TestTransport.class);
        assertThat(transport.getURLName().getProtocol()).isEqualTo("session-loader-transport");
    }

    @Test
    void rejectsProviderClassThatIsNotATransport() throws Exception {
        Session session = Session.getInstance(new Properties());
        Provider provider = transportProvider("not-a-transport", String.class);

        withContextClassLoader(SessionTest.class.getClassLoader(), () -> {
            assertThatThrownBy(() -> session.getTransport(provider))
                    .isInstanceOf(NoSuchProviderException.class)
                    .hasMessage("not-a-transport");
            return null;
        });
    }

    private static Provider transportProvider(String protocol, Class<?> providerClass) {
        return new Provider(Provider.Type.TRANSPORT, protocol, providerClass.getName(), "tests", "1");
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
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
