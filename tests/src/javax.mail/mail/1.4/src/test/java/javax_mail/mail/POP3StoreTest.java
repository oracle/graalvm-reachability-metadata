/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import com.sun.mail.pop3.POP3Store;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.URLName;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;

public class POP3StoreTest {
    @Test
    void getStoreLoadsConfiguredPop3MessageClass() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", TestPOP3Message.class.getName());
        Session session = Session.getInstance(properties);

        Store store = session.getStore("pop3");

        assertThat(store).isInstanceOf(POP3Store.class);
        store.close();
    }

    @Test
    void getStoreFallsBackToClassForNameForMissingConfiguredPop3MessageClass() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", "example.missing.Pop3Message");
        Session session = Session.getInstance(properties);

        Store store = session.getStore(new URLName("pop3://localhost"));

        assertThat(store).isInstanceOf(POP3Store.class);
        store.close();
    }

    public static final class TestPOP3Message extends MimeMessage {
        public TestPOP3Message(Folder folder, int messageNumber) {
            super(folder, messageNumber);
        }
    }
}
