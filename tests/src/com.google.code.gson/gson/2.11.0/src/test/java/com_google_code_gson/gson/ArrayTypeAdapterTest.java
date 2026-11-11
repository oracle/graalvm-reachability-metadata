/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

public class ArrayTypeAdapterTest {
    @Test
    void deserializesAndSerializesPrimitiveArray() {
        Gson gson = new Gson();

        int[] readings = gson.fromJson("[12,-4,0,19]", int[].class);

        assertThat(readings).containsExactly(12, -4, 0, 19);
        assertThat(gson.toJson(readings)).isEqualTo("[12,-4,0,19]");
        assertThat(gson.fromJson("[]", int[].class)).isEmpty();
    }

    @Test
    void deserializesAndSerializesReferenceArrayIncludingNull() {
        Gson gson = new Gson();

        String[] routes = gson.fromJson("""
                ["east",null,"west"]
                """, String[].class);

        assertThat(routes).containsExactly("east", null, "west");
        assertThat(gson.toJson(routes)).isEqualTo("[\"east\",null,\"west\"]");
        assertThat(gson.fromJson("[]", String[].class)).isEmpty();
    }
}
