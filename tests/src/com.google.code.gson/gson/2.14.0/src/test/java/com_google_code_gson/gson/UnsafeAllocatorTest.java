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
    void deserializesClassWithoutNoArgsConstructorByUsingJdkUnsafe() {
        Gson gson = new Gson();

        ConstructorOnlyPerson person = gson.fromJson(
                """
                {"name":"Ada","age":37}
                """,
                ConstructorOnlyPerson.class);

        assertThat(person.name).isEqualTo("Ada");
        assertThat(person.age).isEqualTo(37);
        assertThat(person.constructorOrInitializerRan).isFalse();
    }

    private static final class ConstructorOnlyPerson {
        private String name;
        private int age;
        private boolean constructorOrInitializerRan = true;

        private ConstructorOnlyPerson(String name) {
            this.name = name;
            constructorOrInitializerRan = true;
        }
    }
}
