/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import org.junit.jupiter.api.Test;

public class ConstructorConstructorTest {
    @Test
    void findsPrivateNoArgsConstructorAndPreservesDefaults() {
        Gson gson = new Gson();

        Settings settings = gson.fromJson("""
                {"endpoint":"registry"}
                """, Settings.class);

        assertThat(settings.endpoint).isEqualTo("registry");
        assertThat(settings.retries).isEqualTo(5);
        assertThat(settings.initialized).isTrue();
    }

    @Test
    void rejectsConstructorlessDeserializationWhenUnsafeIsDisabled() {
        Gson gson = new GsonBuilder().disableJdkUnsafe().create();

        assertThatThrownBy(() -> gson.fromJson("""
                {"value":"payload"}
                """, RequiredArgument.class))
                .isInstanceOf(JsonIOException.class)
                .hasMessageContaining("usage of JDK Unsafe is disabled");
    }

    private static final class Settings {
        private String endpoint;
        private int retries;
        private transient boolean initialized;

        private Settings() {
            retries = 5;
            initialized = true;
        }
    }

    private static final class RequiredArgument {
        private final String value;

        private RequiredArgument(String value) {
            this.value = value;
        }
    }
}
