/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jmock.jmock;

import org.jmock.core.constraint.HasPropertyWithValue;
import org.jmock.core.constraint.IsEqual;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HasPropertyWithValueTest {
    @Test
    void evalInvokesJavaBeanGetterAndMatchesReturnedPropertyValue() {
        HasPropertyWithValue constraint = new HasPropertyWithValue("name", new IsEqual("Ada"));

        boolean result = constraint.eval(new Person("Ada"));

        assertThat(result).isTrue();
    }

    public static final class Person {
        private final String name;

        public Person(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
