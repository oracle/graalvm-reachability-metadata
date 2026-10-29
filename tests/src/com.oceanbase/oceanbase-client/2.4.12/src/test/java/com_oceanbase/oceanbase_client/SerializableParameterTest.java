/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oceanbase.oceanbase_client;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializableParameterTest extends DatabaseTestSupport {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void roundTripsSerializableObjectsThroughBlobParameters(boolean serverPrepared) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("useServerPrepStmts", Boolean.toString(serverPrepared));
        Map<String, Integer> payload = new LinkedHashMap<>();
        payload.put("first", 17);
        payload.put("second", 25);
        try (Connection connection = database.connect("", properties);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEMPORARY TABLE serialized_values (payload BLOB)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO serialized_values VALUES (?)")) {
                insert.setObject(1, payload);
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            try (ResultSet result = statement.executeQuery("SELECT payload FROM serialized_values")) {
                assertThat(result.next()).isTrue();
                try (ObjectInputStream input = new ObjectInputStream(result.getBinaryStream(1))) {
                    assertThat(input.readObject()).isEqualTo(payload);
                }
                assertThat(result.next()).isFalse();
            }
        }
    }
}
