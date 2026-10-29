/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.mail.imap.IMAPStore;
import com.sun.mail.pop3.POP3Store;
import com.sun.mail.smtp.SMTPTransport;
import java.util.Properties;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;

public class SessionTest {
    @Test
    void resolvesBuiltInStoresAndTransports() throws Exception {
        Session session = Session.getInstance(new Properties());
        for (String protocol : new String[] {"imap", "imaps"}) {
            Store store = session.getStore(protocol);
            assertInstanceOf(IMAPStore.class, store);
            assertEquals(protocol, store.getURLName().getProtocol());
        }
        for (String protocol : new String[] {"pop3", "pop3s"}) {
            Store store = session.getStore(protocol);
            assertInstanceOf(POP3Store.class, store);
            assertEquals(protocol, store.getURLName().getProtocol());
        }
        for (String protocol : new String[] {"smtp", "smtps"}) {
            Transport transport = session.getTransport(protocol);
            assertInstanceOf(SMTPTransport.class, transport);
            assertEquals(protocol, transport.getURLName().getProtocol());
        }
        assertInstanceOf(SMTPTransport.class, session.getTransport(new InternetAddress("reader@example.org")));
    }

    @Test
    void resolvesProviderWhenContextClassLoaderDeniesAccess() throws Exception {
        Session session = Session.getInstance(new Properties());
        String providerClassName = session.getProvider("imap").getClassName();
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        DenyingProviderClassLoader restricted = new DenyingProviderClassLoader(original, providerClassName);
        try {
            thread.setContextClassLoader(restricted);
            Store store = session.getStore("imap");
            assertTrue(restricted.providerAccessDenied);
            assertInstanceOf(IMAPStore.class, store);
            assertEquals("imap", store.getURLName().getProtocol());
            assertFalse(store.isConnected());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    @Test
    void resolvesProviderWithoutContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            Properties properties = new Properties();
            properties.setProperty("mail.store.protocol", "imap");
            Store store = Session.getInstance(properties).getStore();
            assertInstanceOf(IMAPStore.class, store);
            assertEquals("imap", store.getURLName().getProtocol());
        } finally {
            thread.setContextClassLoader(original);
        }
    }

    private static final class DenyingProviderClassLoader extends ClassLoader {
        private final String providerClassName;
        private boolean providerAccessDenied;

        private DenyingProviderClassLoader(ClassLoader parent, String providerClassName) {
            super(parent);
            this.providerClassName = providerClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (providerClassName.equals(name)) {
                providerAccessDenied = true;
                throw new SecurityException("Context loader denies access to " + name);
            }
            return super.loadClass(name);
        }
    }
}
