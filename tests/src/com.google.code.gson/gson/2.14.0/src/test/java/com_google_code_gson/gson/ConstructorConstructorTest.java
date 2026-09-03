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
    void deserializesObjectByInvokingNoArgsConstructor() {
        Gson gson = new Gson();

        ConstructedPerson person = gson.fromJson(
                """
                {"name":"Ada","age":37}
                """,
                ConstructedPerson.class);

        assertThat(person.name).isEqualTo("Ada");
        assertThat(person.age).isEqualTo(37);
        assertThat(person.constructorCalled).isTrue();
    }

    @Test
    void rejectsObjectWithoutNoArgsConstructorWhenJdkUnsafeIsDisabled() {
        Gson gson = new GsonBuilder()
                .disableJdkUnsafe()
                .create();

        assertThatThrownBy(() -> gson.fromJson(
                """
                {"name":"Grace"}
                """,
                ConstructorOnlyPerson.class))
                .isInstanceOf(JsonIOException.class)
                .hasMessageContaining("Unable to create instance of")
                .hasMessageContaining("usage of JDK Unsafe is disabled");
    }

    private static final class ConstructedPerson {
        private String name;
        private int age;
        private boolean constructorCalled;

        private ConstructedPerson() {
            constructorCalled = true;
        }
    }

    private static final class ConstructorOnlyPerson {
        private String name;

        private ConstructorOnlyPerson(String name) {
            this.name = name;
        }
    }
}
