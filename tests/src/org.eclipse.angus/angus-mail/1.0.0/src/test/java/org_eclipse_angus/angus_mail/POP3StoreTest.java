/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.angus_mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.mail.pop3.POP3Message;
import com.sun.mail.pop3.POP3Store;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.URLName;
import java.util.Properties;
import org.junit.jupiter.api.Test;

public class POP3StoreTest {
    @Test
    public void configuredMessageClassIsResolvedWhenPop3StoreIsCreated() {
        POP3Store store = new POP3Store(newSessionWithMessageClass(RecordingPOP3Message.class.getName()),
                new URLName("pop3://localhost"));

        assertThat(store).isNotNull();
    }

    @Test
    public void unavailableConfiguredMessageClassDoesNotPreventPop3StoreCreation() {
        POP3Store store = new POP3Store(newSessionWithMessageClass("org.example.MissingPOP3Message"),
                new URLName("pop3://localhost"));

        assertThat(store).isNotNull();
    }

    private static Session newSessionWithMessageClass(String messageClassName) {
        Properties properties = new Properties();
        properties.setProperty("mail.pop3.message.class", messageClassName);
        return Session.getInstance(properties);
    }

    public static final class RecordingPOP3Message extends POP3Message {
        public RecordingPOP3Message(Folder folder, int messageNumber) throws MessagingException {
            super(folder, messageNumber);
        }
    }
}
