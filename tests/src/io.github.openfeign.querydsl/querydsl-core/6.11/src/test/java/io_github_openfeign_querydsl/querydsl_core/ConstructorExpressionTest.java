/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.Expressions;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

public class ConstructorExpressionTest {

    @Test
    void newInstanceInvokesProjectedConstructor() {
        ConstructorExpression<NameValue> projection = Projections.constructor(
                NameValue.class,
                Expressions.constant("name"));

        NameValue value = projection.newInstance("alice");

        assertThat(value.name()).isEqualTo("alice");
    }

    @Test
    void serializationRestoresTransientConstructorState() throws Exception {
        ConstructorExpression<NameValue> projection = Projections.constructor(
                NameValue.class,
                Expressions.constant("name"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(projection);
        }

        ConstructorExpression<?> restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (ConstructorExpression<?>) input.readObject();
        }

        assertThat(restored.newInstance("bob")).isInstanceOfSatisfying(
                NameValue.class,
                value -> assertThat(value.name()).isEqualTo("bob"));
    }

    public static final class NameValue {
        private final String name;

        public NameValue(String name) {
            this.name = name;
        }

        String name() {
            return name;
        }
    }
}
