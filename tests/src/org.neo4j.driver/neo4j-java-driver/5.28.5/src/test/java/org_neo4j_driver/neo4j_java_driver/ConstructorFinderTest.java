/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_neo4j_driver.neo4j_java_driver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.value.ValueException;
import org.neo4j.driver.mapping.Property;

public class ConstructorFinderTest {
    @Test
    void findsAnnotatedConstructorBeforeReportingPropertyConversionError() {
        Value value = Values.value(Map.of("age", "not a number"));

        assertThatThrownBy(() -> value.as(PersonWithPrimitiveAge.class))
                .isInstanceOf(ValueException.class)
                .hasMessageContaining("Failed to map 'age' property to 'int'")
                .hasMessageContaining(PersonWithPrimitiveAge.class.getCanonicalName());
    }

    public static class PersonWithPrimitiveAge {
        private final int age;

        public PersonWithPrimitiveAge(@Property("age") int age) {
            this.age = age;
        }

        public int age() {
            return age;
        }
    }
}
