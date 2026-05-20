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
    void deserializesClassWithPrivateNoArgsConstructor() {
        Gson gson = new Gson();

        DefaultConstructible actual = gson.fromJson("""
                {
                  "name": "Ada",
                  "count": 37,
                  "active": true
                }
                """, DefaultConstructible.class);

        assertThat(actual.name).isEqualTo("Ada");
        assertThat(actual.count).isEqualTo(37);
        assertThat(actual.active).isTrue();
    }

    @Test
    void reportsMissingNoArgsConstructorWhenUnsafeIsDisabled() {
        Gson gson = new GsonBuilder().disableJdkUnsafe().create();

        assertThatThrownBy(() -> gson.fromJson("""
                {
                  "value": "Ada"
                }
                """, NoDefaultConstructor.class))
                .isInstanceOf(JsonIOException.class)
                .hasMessageContaining("usage of JDK Unsafe is disabled")
                .hasMessageContaining(NoDefaultConstructor.class.getName());
    }

    private static final class DefaultConstructible {
        private String name;
        private int count;
        private boolean active;

        private DefaultConstructible() {
            name = "created by constructor";
        }
    }

    private static final class NoDefaultConstructor {
        @SuppressWarnings("unused")
        private final String value;

        private NoDefaultConstructor(String value) {
            this.value = value;
        }
    }
}
