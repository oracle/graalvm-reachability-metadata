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
import javax.mail.Provider;
import javax.mail.Session;
import org.junit.jupiter.api.Test;

public class SessionAnonymous5Test {
    @Test
    void discoversApplicationProviderResource() throws Exception {
        Session session = Session.getInstance(new Properties());
        Provider provider = session.getProvider("mailbox");
        assertEquals(Provider.Type.STORE, provider.getType());
        assertEquals("Mailbox application", provider.getVendor());
        assertInstanceOf(POP3Store.class, session.getStore("mailbox"));
    }
}
