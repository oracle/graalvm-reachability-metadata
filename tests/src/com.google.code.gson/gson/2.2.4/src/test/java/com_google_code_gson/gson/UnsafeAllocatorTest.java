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

public class UnsafeAllocatorTest {
    private final Gson gson = new Gson();

    @Test
    public void deserializesTypeWithoutNoArgsConstructor() {
        final ConstructorBlockedModel model = gson.fromJson("""
                {
                  "name": "unsafe-allocated",
                  "quantity": 42
                }
                """, ConstructorBlockedModel.class);

        assertThat(model.name).isEqualTo("unsafe-allocated");
        assertThat(model.quantity).isEqualTo(42);
    }

    public static final class ConstructorBlockedModel {
        String name;
        int quantity;

        public ConstructorBlockedModel(final String unused) {
            throw new AssertionError("Gson should allocate this type without invoking its constructor");
        }
    }
}
