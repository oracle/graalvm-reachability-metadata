/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class POP3FolderTest {
    @Test
    @Timeout(60)
    public void configuredMessageClassReadsMailboxHeaders() throws Exception {
        POP3StoreTest.withConfiguredMailbox(inbox -> {
            Message message = inbox.getMessage(1);
            assertThat(message).isInstanceOf(POP3StoreTest.CustomPOP3Message.class);
            assertThat(message.getSubject()).isEqualTo("Configured POP3 message");
            assertThat(inbox.getMessage(1)).isSameAs(message);
        });
    }
}
