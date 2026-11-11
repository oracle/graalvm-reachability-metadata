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
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryAnonymous2Test {
    @Test
    void readsAndWritesPrivateObjectFields() {
        Gson gson = new Gson();

        Label label = gson.fromJson("""
                {"text":"fragile","priority":7}
                """, Label.class);

        assertThat(label.text).isEqualTo("fragile");
        assertThat(label.priority).isEqualTo(7);
        JsonObject json = JsonParser.parseString(gson.toJson(label)).getAsJsonObject();
        assertThat(json.get("text").getAsString()).isEqualTo("fragile");
        assertThat(json.get("priority").getAsInt()).isEqualTo(7);
    }

    @Test
    void serializesRecordUsingItsAccessorRatherThanBackingField() {
        Gson gson = new Gson();
        DisplayLabel label = gson.fromJson("""
                {"text":"handle gently"}
                """, DisplayLabel.class);

        assertThat(label).isEqualTo(new DisplayLabel("handle gently"));
        JsonObject json = JsonParser.parseString(gson.toJson(label)).getAsJsonObject();
        assertThat(json.get("text").getAsString()).isEqualTo("HANDLE GENTLY");
    }

    private static final class Label {
        private String text;
        private int priority;
    }

    public record DisplayLabel(String text) {
        @Override
        public String text() {
            return text.toUpperCase(Locale.ROOT);
        }
    }
}
