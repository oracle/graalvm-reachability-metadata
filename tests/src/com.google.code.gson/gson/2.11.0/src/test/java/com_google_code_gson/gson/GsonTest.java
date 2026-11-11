/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

public class GsonTest {
    @Test
    void deserializesUsableGsonWithExposeOnlyFieldPolicy() {
        Gson codec = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        Gson restored = codec.fromJson("{}", Gson.class);

        JsonObject payload = restored.fromJson("""
                {"service":"registry","replicas":3}
                """, JsonObject.class);
        assertThat(payload.get("service").getAsString()).isEqualTo("registry");
        assertThat(payload.get("replicas").getAsInt()).isEqualTo(3);
        assertThat(restored.toJson(new int[]{4, 8})).isEqualTo("[4,8]");
    }
}
