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
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class ReflectiveTypeAdapterFactoryTest {
    @Test
    void discoversInheritedGenericFieldsAndSerializedAliases() {
        Gson gson = new Gson();

        TextEvent event = gson.fromJson("""
                {"legacy_payload":"updated","attempts":2,"unknown":{"ignored":true}}
                """, TextEvent.class);

        assertThat(event.payload()).isEqualTo("updated");
        assertThat(event.attempts).isEqualTo(2);
        JsonObject json = JsonParser.parseString(gson.toJson(event)).getAsJsonObject();
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("payload").getAsString()).isEqualTo("updated");
        assertThat(json.get("attempts").getAsInt()).isEqualTo(2);
        assertThat(json.has("legacy_payload")).isFalse();
        assertThat(json.has("localState")).isFalse();
    }

    private static class Event<T> {
        @SerializedName(value = "payload", alternate = "legacy_payload")
        private T payload;
        private transient String localState = "local";

        T payload() {
            return payload;
        }
    }

    private static final class TextEvent extends Event<String> {
        private int attempts;
    }
}
