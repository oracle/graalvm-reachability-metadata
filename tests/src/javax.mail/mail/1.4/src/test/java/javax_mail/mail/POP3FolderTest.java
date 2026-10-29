/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import javax.mail.Folder;
import javax.mail.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class POP3FolderTest {
    @Test
    void readsAndCachesConfiguredMessages() throws Exception {
        try (Pop3Mailbox mailbox = new Pop3Mailbox()) {
            Folder inbox = mailbox.inbox();
            assertEquals(1, inbox.getMessageCount());
            Message message = inbox.getMessage(1);
            assertInstanceOf(CustomPop3Message.class, message);
            assertSame(inbox, message.getFolder());
            assertSame(message, inbox.getMessage(1));
            assertEquals("Hello reader\r\n", message.getContent());
        }
    }
}
