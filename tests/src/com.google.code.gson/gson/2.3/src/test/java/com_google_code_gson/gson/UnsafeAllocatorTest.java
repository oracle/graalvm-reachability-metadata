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
    private final Gson gson = new Gson();

    @Test
    void deserializesTypeWithoutNoArgsConstructorUsingUnsafeAllocation() {
        ConstructorOnlyMessage message = gson.fromJson(
                "{\"label\":\"created-from-json\",\"priority\":7}",
                ConstructorOnlyMessage.class);

        assertThat(message.label).isEqualTo("created-from-json");
        assertThat(message.priority).isEqualTo(7);
        assertThat(message.wasConstructorInvoked()).isFalse();
    }

    public static final class ConstructorOnlyMessage {
        private final transient boolean constructorInvoked;
        private String label;
        private int priority;

        public ConstructorOnlyMessage(String label, int priority) {
            this.constructorInvoked = true;
            this.label = label;
            this.priority = priority;
        }

        boolean wasConstructorInvoked() {
            return constructorInvoked;
        }
    }
}
