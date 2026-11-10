/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package mysql;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MysqlTestSupport.class)
public class NativeQueryBindingsTest {
    @Test
    void arbitrarySerializableParameterIsSentAsBinaryData() throws Exception {
        ArrayList<String> payload = new ArrayList<>(List.of("first-payload", "second-payload"));
        try (Connection connection = MysqlTestSupport.connect("", Map.of("useServerPrepStmts", "false"));
                PreparedStatement statement = connection.prepareStatement("SELECT ? AS payload")) {
            statement.setObject(1, payload);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                byte[] encoded = result.getBytes("payload");
                assertThat(encoded).startsWith((byte) 0xac, (byte) 0xed, (byte) 0x00, (byte) 0x05);
                assertThat(new String(encoded, StandardCharsets.ISO_8859_1))
                        .contains("java.util.ArrayList", "first-payload", "second-payload");
                assertThat(result.next()).isFalse();
            }
        }
    }
}
