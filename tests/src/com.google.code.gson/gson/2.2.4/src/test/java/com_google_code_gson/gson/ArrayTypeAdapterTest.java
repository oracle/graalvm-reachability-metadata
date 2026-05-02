/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayTypeAdapterTest {
    private final Gson gson = new Gson();

    @Test
    public void readsPrimitiveArrayFromJson() {
        final int[] numbers = gson.fromJson("[1,2,3]", int[].class);

        assertThat(numbers).containsExactly(1, 2, 3);
    }

    @Test
    public void readsReferenceArrayFromJson() {
        final String[] names = gson.fromJson("[\"alpha\",\"beta\"]", String[].class);

        assertThat(names).containsExactly("alpha", "beta");
    }
}
