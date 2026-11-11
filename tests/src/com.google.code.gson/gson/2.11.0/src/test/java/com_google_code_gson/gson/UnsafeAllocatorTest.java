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
    void allocatesObjectWithoutRunningConstructorOrFieldInitializers() {
        Gson gson = new Gson();

        ImportedMessage message = gson.fromJson("""
                {"body":"sync","sequence":27}
                """, ImportedMessage.class);

        assertThat(message.body).isEqualTo("sync");
        assertThat(message.sequence).isEqualTo(27);
        assertThat(message.initialized).isFalse();
    }

    private static final class ImportedMessage {
        private String body;
        private int sequence;
        private transient boolean initialized = true;

        private ImportedMessage(String body) {
            this.body = body;
            sequence = -1;
        }
    }
}
