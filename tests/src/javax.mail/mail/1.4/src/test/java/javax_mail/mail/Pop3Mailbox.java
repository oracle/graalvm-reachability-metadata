/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Properties;
import javax.mail.Folder;
import javax.mail.Session;
import javax.mail.Store;

final class Pop3Mailbox implements AutoCloseable {
    private final LocalMailServer server;
    private final Store store;
    private final Folder inbox;

    Pop3Mailbox() throws Exception {
        server = new LocalMailServer((input, output) -> {
            LocalMailServer.reply(output, "+OK mailbox ready");
            assertEquals("USER reader", input.readLine());
            LocalMailServer.reply(output, "+OK user accepted");
            assertEquals("PASS secret", input.readLine());
            LocalMailServer.reply(output, "+OK authenticated");
            String command;
            while ((command = input.readLine()) != null) {
                switch (command) {
                    case "STAT" -> LocalMailServer.reply(output, "+OK 1 120");
                    case "NOOP" -> LocalMailServer.reply(output, "+OK");
                    case "RETR 1" -> LocalMailServer.reply(output,
                            "+OK message follows\r\nSubject: Local mailbox\r\nContent-Type: text/plain; charset=us-ascii\r\n\r\nHello reader\r\n.");
                    case "QUIT" -> {
                        LocalMailServer.reply(output, "+OK goodbye");
                        return;
                    }
                    default -> throw new AssertionError("Unexpected POP3 command: " + command);
                }
            }
            throw new AssertionError("POP3 client disconnected without QUIT");
        });
        Properties properties = LocalMailServer.properties("pop3");
        properties.setProperty("mail.pop3.message.class", CustomPop3Message.class.getName());
        properties.setProperty("mail.pop3.disabletop", "true");
        store = Session.getInstance(properties).getStore("pop3");
        try {
            store.connect(server.host(), server.port(), "reader", "secret");
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
        } catch (Exception | Error failure) {
            server.close();
            throw failure;
        }
    }

    Folder inbox() {
        return inbox;
    }

    @Override
    public void close() throws Exception {
        try {
            try {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
            } finally {
                store.close();
            }
            server.verify();
        } finally {
            server.close();
        }
    }
}
