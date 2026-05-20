/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_angus.pop3;

import com.sun.mail.pop3.POP3Message;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;

public class POP3StoreFallbackMessage extends POP3Message {
    public POP3StoreFallbackMessage(Folder folder, int messageNumber) throws MessagingException {
        super(folder, messageNumber);
    }
}
