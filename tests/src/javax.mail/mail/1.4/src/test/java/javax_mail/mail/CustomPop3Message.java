/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import com.sun.mail.pop3.POP3Message;
import javax.mail.Folder;
import javax.mail.MessagingException;

public class CustomPop3Message extends POP3Message {
    public CustomPop3Message(Folder folder, int number) throws MessagingException {
        super(folder, number);
    }

    public String mailboxLabel() {
        return "message-" + getMessageNumber();
    }
}
