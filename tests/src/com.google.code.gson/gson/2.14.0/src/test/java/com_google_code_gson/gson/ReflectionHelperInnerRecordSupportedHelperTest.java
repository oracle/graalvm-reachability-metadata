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
import com.google.gson.TypeAdapter;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {
    private record Person(String name, int age, boolean active) {
    }

    @Test
    void serializesAndDeserializesRecord() throws IOException {
        Gson gson = new Gson();
        TypeAdapter<Person> adapter = gson.getAdapter(Person.class);
        Person expected = new Person("Ada", 37, true);

        String json = adapter.toJson(expected);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        assertThat(jsonObject.get("name").getAsString()).isEqualTo("Ada");
        assertThat(jsonObject.get("age").getAsInt()).isEqualTo(37);
        assertThat(jsonObject.get("active").getAsBoolean()).isTrue();

        Person actual = adapter.fromJson("""
                {
                  "name": "Ada",
                  "age": 37,
                  "active": true
                }
                """);
        assertThat(actual).isEqualTo(expected);
    }
}
