/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.gson.Gson;
import org.apache.pulsar.shade.com.google.gson.JsonObject;
import org.apache.pulsar.shade.com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void serializesAndDeserializesRecordComponents() {
        Gson gson = new Gson();
        Account input = new Account("alice", 37, true);

        JsonObject json = JsonParser.parseString(gson.toJson(input)).getAsJsonObject();
        Account restored = gson.fromJson(
                """
                {"name":"bob","age":41,"active":false}
                """,
                Account.class);

        assertThat(json.get("name").getAsString()).isEqualTo("alice");
        assertThat(json.get("age").getAsInt()).isEqualTo(37);
        assertThat(json.get("active").getAsBoolean()).isTrue();
        assertThat(restored).isEqualTo(new Account("bob", 41, false));
    }

    public record Account(String name, int age, boolean active) {
    }
}
