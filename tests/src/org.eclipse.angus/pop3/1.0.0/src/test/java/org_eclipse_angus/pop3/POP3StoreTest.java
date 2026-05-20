/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import java.util.Properties;

import com.sun.mail.pop3.POP3Message;
import com.sun.mail.pop3.POP3Store;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class POP3StoreTest {
    @Test
    void loadsConfiguredPop3MessageClassConstructor() {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", POP3Message.class.getName());
        Session session = Session.getInstance(properties);

        POP3Store store = new POP3Store(session, pop3Url());

        assertThat(store.isConnected()).isFalse();
    }

    @Test
    void handlesMissingConfiguredMessageClass() {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", "com.sun.mail.pop3.NoSuchMessage");
        Session session = Session.getInstance(properties);

        POP3Store store = new POP3Store(session, pop3Url());

        assertThat(store.isConnected()).isFalse();
    }

    private static URLName pop3Url() {
        return new URLName("pop3", "localhost", -1, null, "user", "password");
    }
}
