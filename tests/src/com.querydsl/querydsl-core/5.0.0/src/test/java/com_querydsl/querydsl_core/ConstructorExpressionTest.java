/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collections;

import org.junit.jupiter.api.Test;

public class ConstructorExpressionTest {

    @Test
    void newInstanceInvokesMatchingConstructorAndAppliesPrimitiveDefaults() {
        ConstructorExpression<ProjectedCustomer> projection = customerProjection();

        ProjectedCustomer customer = projection.newInstance("Ada", null);

        assertThat(customer.getName()).isEqualTo("Ada");
        assertThat(customer.getAge()).isZero();
    }

    @Test
    void deserializedExpressionRestoresTransientConstructorState() throws Exception {
        ConstructorExpression<DefaultCustomer> projection = Projections.constructor(
                DefaultCustomer.class,
                new Class<?>[0],
                Collections.emptyList());

        ConstructorExpression<?> restored = (ConstructorExpression<?>) roundTrip(projection);
        DefaultCustomer customer = (DefaultCustomer) restored.newInstance();

        assertThat(restored.getType()).isEqualTo(DefaultCustomer.class);
        assertThat(customer.getName()).isEqualTo("default-name");
    }

    private static ConstructorExpression<ProjectedCustomer> customerProjection() {
        return Projections.constructor(
                ProjectedCustomer.class,
                new Class<?>[] {String.class, int.class},
                Expressions.stringPath("name"),
                Expressions.numberPath(Integer.class, "age"));
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return input.readObject();
        }
    }

    public static class DefaultCustomer {

        private final String name;

        public DefaultCustomer() {
            name = "default-name";
        }

        public String getName() {
            return name;
        }
    }

    public static class ProjectedCustomer {

        private final String name;

        private final int age;

        public ProjectedCustomer(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }
}
