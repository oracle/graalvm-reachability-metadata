/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorExpressionTest {
    @Test
    void newInstanceCreatesProjectionThroughPublicConstructorExpression() {
        ConstructorExpression<PersonName> projection = personNameProjection();

        PersonName personName = projection.newInstance("Ada", "Lovelace");

        assertThat(personName).isEqualTo(new PersonName("Ada", "Lovelace"));
    }

    @Test
    void readObjectReinitializesTransientConstructorState() throws Throwable {
        ConstructorExpression<PersonName> projection = personNameProjection();

        invokeReadObject(projection);

        assertThat(projection.newInstance("Grace", "Hopper"))
                .isEqualTo(new PersonName("Grace", "Hopper"));
    }

    private static ConstructorExpression<PersonName> personNameProjection() {
        return Projections.constructor(
                PersonName.class,
                Expressions.stringPath("firstName"),
                Expressions.stringPath("lastName"));
    }

    private static void invokeReadObject(ConstructorExpression<?> projection) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                ConstructorExpression.class,
                MethodHandles.lookup());
        MethodHandle readObject = lookup.findSpecial(
                ConstructorExpression.class,
                "readObject",
                MethodType.methodType(void.class, ObjectInputStream.class),
                ConstructorExpression.class);
        readObject.invoke(projection, new StubObjectInputStream());
    }

    public static final class PersonName {
        private final String firstName;
        private final String lastName;

        public PersonName(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PersonName)) {
                return false;
            }
            PersonName personName = (PersonName) o;
            return Objects.equals(firstName, personName.firstName) && Objects.equals(lastName, personName.lastName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstName, lastName);
        }
    }

    private static final class StubObjectInputStream extends ObjectInputStream {
        StubObjectInputStream() throws IOException {
            super();
        }

        @Override
        protected Object readObjectOverride() {
            return null;
        }
    }
}
