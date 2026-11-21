/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.apache.catalina.realm.MessageDigestCredentialHandler;
import org.apache.catalina.realm.RealmBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RealmBaseTest {
    @Test
    void hashesCredentialsUsingAutomaticallySelectedAndNamedHandlers() throws Exception {
        String expected = "password:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("password".getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, run("-a", "SHA-256", "-s", "0", "-i", "1", "password"));
        assertEquals(expected, run("-a", "SHA-256", "-s", "0", "-i", "1", "-h",
                MessageDigestCredentialHandler.class.getName(), "password"));
    }

    private String run(String... arguments) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            RealmBase.main(arguments);
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8).strip();
    }
}
