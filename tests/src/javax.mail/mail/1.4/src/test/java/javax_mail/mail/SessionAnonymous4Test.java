/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_mail.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import javax.mail.Session;
import org.junit.jupiter.api.Test;

public class SessionAnonymous4Test {
    @Test
    void loadsBundledProviderAndAddressResources() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.debug", "true");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            Session session = Session.getInstance(properties);
            assertEquals("smtp", session.getTransport("smtp").getURLName().getProtocol());
        } finally {
            System.setOut(original);
        }
        String log = captured.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("successfully loaded resource: /META-INF/javamail.default.providers"), log);
        assertTrue(log.contains("successfully loaded resource: /META-INF/javamail.default.address.map"), log);
    }
}
