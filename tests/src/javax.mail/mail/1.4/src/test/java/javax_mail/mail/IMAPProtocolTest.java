/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.mail.imap.protocol.IMAPProtocol;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class IMAPProtocolTest {
    @Test
    void authenticatesUsingSaslPlain() throws Exception {
        try (LocalMailServer server = new LocalMailServer((input, output) -> {
            LocalMailServer.reply(output, "* OK mail server ready");
            String capability = input.readLine();
            String capabilityTag = capability.split(" ", 2)[0];
            assertEquals(capabilityTag + " CAPABILITY", capability);
            LocalMailServer.reply(output, "* CAPABILITY IMAP4rev1 AUTH=PLAIN");
            LocalMailServer.reply(output, capabilityTag + " OK capabilities complete");
            String authenticate = input.readLine();
            String tag = authenticate.split(" ", 2)[0];
            assertEquals(tag + " AUTHENTICATE PLAIN", authenticate);
            LocalMailServer.reply(output, "+ ");
            byte[] credentials = Base64.getDecoder().decode(input.readLine());
            assertEquals("\0reader\0secret", new String(credentials, StandardCharsets.UTF_8));
            LocalMailServer.reply(output, tag + " OK authenticated");
            String logout = input.readLine();
            String logoutTag = logout.split(" ", 2)[0];
            assertEquals(logoutTag + " LOGOUT", logout);
            LocalMailServer.reply(output, "* BYE goodbye");
            LocalMailServer.reply(output, logoutTag + " OK logout complete");
        })) {
            IMAPProtocol protocol = new IMAPProtocol("imap", server.host(), server.port(), false,
                    System.out, LocalMailServer.properties("imap"), false);
            try {
                assertTrue(protocol.hasCapability("AUTH=PLAIN"));
                assertFalse(protocol.isAuthenticated());
                protocol.sasllogin(new String[] {"PLAIN"}, null, null, "reader", "secret");
                assertTrue(protocol.isAuthenticated());
                protocol.logout();
                server.verify();
            } finally {
                protocol.disconnect();
            }
        }
    }
}
