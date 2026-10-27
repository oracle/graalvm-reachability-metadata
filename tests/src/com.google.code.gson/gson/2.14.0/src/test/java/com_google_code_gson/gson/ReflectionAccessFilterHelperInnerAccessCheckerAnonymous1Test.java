/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ReflectionAccessFilter;
import com.google.gson.ReflectionAccessFilter.FilterResult;
import org.junit.jupiter.api.Test;

public class ReflectionAccessFilterHelperInnerAccessCheckerAnonymous1Test {
    @Test
    void usesDefaultJavaAccessChecksWhenOnlyInaccessibleReflectionIsBlocked() {
        ReflectionAccessFilter reflectionAccessFilter =
                new PublicPersonOnlyReflectionAccessFilter();
        Gson gson = new GsonBuilder()
                .addReflectionAccessFilter(reflectionAccessFilter)
                .create();

        PublicPerson person = new PublicPerson();
        person.name = "Ada";
        person.age = 37;

        String json = gson.toJson(person);

        assertThat(json).contains("\"name\":\"Ada\"");
        assertThat(json).contains("\"age\":37");

        PublicPerson deserialized = gson.fromJson(
                """
                {"name":"Grace","age":41}
                """,
                PublicPerson.class);

        assertThat(deserialized.name).isEqualTo("Grace");
        assertThat(deserialized.age).isEqualTo(41);
    }

    public static final class PublicPerson {
        public String name;
        public int age;

        public PublicPerson() {
        }
    }

    private static final class PublicPersonOnlyReflectionAccessFilter
            implements ReflectionAccessFilter {
        @Override
        public FilterResult check(Class<?> rawClass) {
            if (rawClass == PublicPerson.class) {
                return FilterResult.BLOCK_INACCESSIBLE;
            }
            return FilterResult.INDECISIVE;
        }
    }
}
