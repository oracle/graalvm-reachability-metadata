/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class POP3StoreTest {
    @Test
    void configuredMessageClassIsUsedForMailboxMessages() throws Exception {
        try (Pop3Mailbox mailbox = new Pop3Mailbox()) {
            CustomPop3Message message = assertInstanceOf(CustomPop3Message.class, mailbox.inbox().getMessage(1));
            assertEquals("message-1", message.mailboxLabel());
            assertEquals("Local mailbox", message.getSubject());
        }
    }
}
