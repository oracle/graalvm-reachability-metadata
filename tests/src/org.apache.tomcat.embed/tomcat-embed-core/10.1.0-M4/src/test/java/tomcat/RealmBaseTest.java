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
    void hashesCredentialsWithDefaultAndNamedHandler() throws Exception {
        String expected = "catalog-secret:" + HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest("catalog-secret".getBytes(StandardCharsets.UTF_8)));
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            RealmBase.main(new String[] {"-a", "SHA-256", "-s", "0", "-i", "1", "catalog-secret"});
            RealmBase.main(new String[] {"-h", MessageDigestCredentialHandler.class.getName(),
                    "-a", "SHA-256", "-s", "0", "-i", "1", "catalog-secret"});
        } finally {
            System.setOut(previous);
        }
        assertThat(bytes.toString(StandardCharsets.UTF_8).lines().toList()).containsExactly(expected, expected);
    }
}
