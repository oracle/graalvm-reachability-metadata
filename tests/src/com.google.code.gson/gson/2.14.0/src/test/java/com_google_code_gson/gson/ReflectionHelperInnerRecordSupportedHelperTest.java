/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void serializesAndDeserializesRecordThroughReflectiveAdapter() {
        Gson gson = new Gson();
        Account account = new Account("alice", 37, true);

        String json = gson.toJson(account);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        assertThat(jsonObject.get("name").getAsString()).isEqualTo("alice");
        assertThat(jsonObject.get("age").getAsInt()).isEqualTo(37);
        assertThat(jsonObject.get("active").getAsBoolean()).isTrue();

        Account deserialized = gson.fromJson(
                """
                {"name":"bob","age":41,"active":false}
                """,
                Account.class);

        assertThat(deserialized).isEqualTo(new Account("bob", 41, false));
    }

    public record Account(String name, int age, boolean active) {
    }
}
