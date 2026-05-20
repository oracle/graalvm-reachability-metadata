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

public class ReflectiveTypeAdapterFactoryAnonymous2Test {
    @Test
    void serializesPojoFieldsWithReflectiveAdapter() {
        Gson gson = new Gson();
        Profile profile = new Profile("Ada", 37, true);

        JsonObject jsonObject = JsonParser.parseString(gson.toJson(profile)).getAsJsonObject();

        assertThat(jsonObject.get("name").getAsString()).isEqualTo("Ada");
        assertThat(jsonObject.get("score").getAsInt()).isEqualTo(37);
        assertThat(jsonObject.get("active").getAsBoolean()).isTrue();
    }

    private static final class Profile {
        private final String name;
        private final int score;
        private final boolean active;

        private Profile(String name, int score, boolean active) {
            this.name = name;
            this.score = score;
            this.active = active;
        }
    }
}
