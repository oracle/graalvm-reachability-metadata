/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.sun.mail.pop3.POP3Store;
import java.util.Properties;
import javax.mail.Session;
import org.junit.jupiter.api.Test;

public class SessionAnonymous6Test {
    @Test
    void discoversProviderWithoutThreadContextLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader original = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(null);
            Session session = Session.getInstance(new Properties());
            assertEquals("Mailbox application", session.getProvider("mailbox").getVendor());
            assertInstanceOf(POP3Store.class, session.getStore("mailbox"));
        } finally {
            thread.setContextClassLoader(original);
        }
    }
}
