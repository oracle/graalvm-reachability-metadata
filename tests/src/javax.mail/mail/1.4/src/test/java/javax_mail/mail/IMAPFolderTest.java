/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.mail.imap.IMAPFolder;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class IMAPFolderTest {
    @Test
    void appliesUnsolicitedFlagUpdatesToCachedMessage() throws Exception {
        try (LocalMailServer server = new LocalMailServer((input, output) -> {
            LocalMailServer.reply(output, "* PREAUTH mail server ready");
            String line;
            while ((line = input.readLine()) != null) {
                String[] parts = line.split(" ", 2);
                String tag = parts[0];
                String command = parts[1];
                if (command.equals("CAPABILITY")) {
                    LocalMailServer.reply(output, "* CAPABILITY IMAP4rev1");
                    LocalMailServer.reply(output, tag + " OK capabilities complete");
                } else if (command.startsWith("LIST ")) {
                    LocalMailServer.reply(output, "* LIST () \"/\" \"INBOX\"");
                    LocalMailServer.reply(output, tag + " OK folders listed");
                } else if (command.startsWith("SELECT ")) {
                    LocalMailServer.reply(output, "* FLAGS (\\Seen \\Answered)");
                    LocalMailServer.reply(output, "* 1 EXISTS");
                    LocalMailServer.reply(output, "* 0 RECENT");
                    LocalMailServer.reply(output, "* OK [UIDVALIDITY 7] mailbox identity");
                    LocalMailServer.reply(output, tag + " OK [READ-WRITE] selected");
                } else if (command.equals("NOOP")) {
                    LocalMailServer.reply(output, "* 1 FETCH (FLAGS (\\Seen \\Answered))");
                    LocalMailServer.reply(output, tag + " OK updated");
                } else if (command.equals("CLOSE")) {
                    LocalMailServer.reply(output, tag + " OK closed");
                } else if (command.equals("LOGOUT")) {
                    LocalMailServer.reply(output, "* BYE goodbye");
                    LocalMailServer.reply(output, tag + " OK logged out");
                    return;
                } else {
                    throw new AssertionError("Unexpected IMAP command: " + command);
                }
            }
            throw new AssertionError("IMAP client disconnected without LOGOUT");
        })) {
            Store store = Session.getInstance(LocalMailServer.properties("imap")).getStore("imap");
            try {
                store.connect(server.host(), server.port(), "reader", "secret");
                IMAPFolder inbox = assertInstanceOf(IMAPFolder.class, store.getFolder("INBOX"));
                try {
                    inbox.open(Folder.READ_WRITE);
                    assertEquals(1, inbox.getMessageCount());
                    Message message = inbox.getMessage(1);
                    inbox.doCommand(protocol -> {
                        protocol.simpleCommand("NOOP", null);
                        return null;
                    });
                    assertTrue(message.isSet(Flags.Flag.SEEN));
                    assertTrue(message.isSet(Flags.Flag.ANSWERED));
                } finally {
                    if (inbox.isOpen()) {
                        inbox.close(true);
                    }
                }
            } finally {
                store.close();
            }
            server.verify();
        }
    }
}
