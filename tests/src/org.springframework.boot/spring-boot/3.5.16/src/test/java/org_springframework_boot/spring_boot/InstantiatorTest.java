/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.util.Instantiator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import static org.assertj.core.api.Assertions.assertThat;

public class InstantiatorTest {

    @Test
    void instantiatesNamedTypesWithAvailableConstructorArguments() {
        Instantiator<Greeter> instantiator = new Instantiator<>(Greeter.class,
                (parameters) -> parameters.add(String.class, "Ada"));

        List<Greeter> greeters = instantiator.instantiate(
                Arrays.asList(CasualGreeter.class.getName(), FormalGreeter.class.getName()));

        assertThat(greeters).hasSize(2);
        assertThat(greeters.get(0)).isInstanceOf(FormalGreeter.class);
        assertThat(greeters.get(0).greet()).isEqualTo("Good day, Ada");
        assertThat(greeters.get(1).greet()).isEqualTo("Hi");
    }

    @Test
    void instantiatesTypesUsingFactoryProvidedArguments() {
        Instantiator<Greeter> instantiator = new Instantiator<>(Greeter.class,
                (parameters) -> parameters.add(String.class, (type) -> type.getSimpleName()));

        List<Greeter> greeters = instantiator.instantiateTypes(Arrays.asList(FormalGreeter.class));

        assertThat(greeters).singleElement().extracting(Greeter::greet).isEqualTo("Good day, Greeter");
    }

    public interface Greeter {

        String greet();
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    public static class FormalGreeter implements Greeter {

        private final String name;

        public FormalGreeter(String name) {
            this.name = name;
        }

        @Override
        public String greet() {
            return "Good day, " + name;
        }
    }

    public static class CasualGreeter implements Greeter {

        @Override
        public String greet() {
            return "Hi";
        }
    }
}
