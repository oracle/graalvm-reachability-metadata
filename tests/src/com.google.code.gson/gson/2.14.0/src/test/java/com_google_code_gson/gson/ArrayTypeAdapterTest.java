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
    void deserializesPrimitiveArray() {
        Gson gson = new Gson();

        int[] actual = gson.fromJson("[1, 2, 3]", int[].class);

        assertThat(actual).containsExactly(1, 2, 3);
    }

    @Test
    void deserializesObjectArray() {
        Gson gson = new Gson();

        String[] actual = gson.fromJson("[\"alpha\", \"beta\", \"gamma\"]", String[].class);

        assertThat(actual).containsExactly("alpha", "beta", "gamma");
    }
}
