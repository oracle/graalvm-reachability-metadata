/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.refEq;

public class EqualsBuilderTest {
    @Test
    void refEqMatcherComparesObjectsByTheirFields() {
        PersonSink sink = Mockito.mock(PersonSink.class);

        sink.accept(new Person("Ada", 37));

        Mockito.verify(sink).accept(refEq(new Person("Ada", 37)));
    }

    interface PersonSink {
        void accept(Person person);
    }

    public static class Person {
        private final String name;
        private final int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }
}
