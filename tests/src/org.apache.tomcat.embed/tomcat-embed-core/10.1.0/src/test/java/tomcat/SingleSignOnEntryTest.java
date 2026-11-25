/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.apache.catalina.authenticator.SingleSignOnEntry;
import org.apache.catalina.realm.GenericPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SingleSignOnEntryTest {
    @Test
    void serializesCredentialsAndSerializablePrincipal() throws Exception {
        SingleSignOnEntry entry = new SingleSignOnEntry(new GenericPrincipal("reader", List.of("user")),
                "BASIC", "reader", "secret");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(entry);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            SingleSignOnEntry restored = (SingleSignOnEntry) input.readObject();
            assertThat(restored.getPrincipal().getName()).isEqualTo("reader");
            assertThat(restored.getUsername()).isEqualTo("reader");
            assertThat(restored.getPassword()).isEqualTo("secret");
            assertThat(restored.getCanReauthenticate()).isTrue();
        }
    }
}
