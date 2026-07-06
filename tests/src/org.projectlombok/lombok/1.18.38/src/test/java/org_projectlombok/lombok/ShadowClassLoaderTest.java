/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import lombok.Builder;
import lombok.Value;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ShadowClassLoaderTest {
    @Test
    void buildsImmutableValueUsingGeneratedBuilder() {
        Person person = Person.builder()
                .name("Ada")
                .age(36)
                .build();

        assertThat(person.getName()).isEqualTo("Ada");
        assertThat(person.getAge()).isEqualTo(36);
    }
    @Value
    @Builder
    static class Person {
        String name;
        int age;
    }
}
