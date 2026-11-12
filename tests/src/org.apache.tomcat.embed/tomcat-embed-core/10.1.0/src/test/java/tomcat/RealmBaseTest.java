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

import static org.assertj.core.api.Assertions.assertThat;

public class RealmBaseTest {
    @Test
    void hashesCredentialsUsingAutomaticAndExplicitHandlerSelection() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            RealmBase.main(new String[] {"-a", "SHA-256", "-s", "0", "-i", "1", "secret"});
            RealmBase.main(new String[] {"-a", "SHA-256", "-s", "0", "-i", "1", "-h",
                    MessageDigestCredentialHandler.class.getName(), "secret"});
        } finally {
            System.setOut(previous);
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(output.toString(StandardCharsets.UTF_8).lines()).containsExactly("secret:" + digest,
                "secret:" + digest);
    }
}
