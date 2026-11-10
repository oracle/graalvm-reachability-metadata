/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.util.Instantiator;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiatorTest {
    @Test
    void injectsAvailableArgumentsIntoMostSpecificConstructor() {
        Instantiator<Greeting> instantiator = new Instantiator<>(Greeting.class,
                parameters -> parameters.add(String.class, "Ada"));
        Greeting greeting = instantiator.instantiateType(PersonalGreeting.class);

        assertThat(greeting).isNotNull();
        assertThat(greeting.message()).isEqualTo("Hello Ada");
    }

    public interface Greeting {
        String message();
    }

    public static class PersonalGreeting implements Greeting {
        private final String name;

        public PersonalGreeting() {
            this("world");
        }

        public PersonalGreeting(String name) {
            this.name = name;
        }

        @Override
        public String message() {
            return "Hello " + this.name;
        }
    }
}
