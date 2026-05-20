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

public class UnsafeAllocatorTest {
    @Test
    void deserializesTypeWithoutNoArgsConstructorUsingUnsafeAllocator() {
        Gson gson = new Gson();

        ConstructorOnlyMessage actual = gson.fromJson("""
                {
                  "message": "allocated without constructor",
                  "priority": 7
                }
                """, ConstructorOnlyMessage.class);

        assertThat(actual.message).isEqualTo("allocated without constructor");
        assertThat(actual.priority).isEqualTo(7);
        assertThat(actual.constructorMarker).isNull();
    }

    private static final class ConstructorOnlyMessage {
        private String message;
        private int priority;
        private String constructorMarker;

        private ConstructorOnlyMessage(String message) {
            this.message = message;
            priority = -1;
            constructorMarker = "constructor invoked";
        }
    }
}
