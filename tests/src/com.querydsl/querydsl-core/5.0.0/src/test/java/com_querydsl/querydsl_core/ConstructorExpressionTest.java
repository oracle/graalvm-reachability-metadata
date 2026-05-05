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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
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
    void readObjectRestoresTransientConstructorFields() throws Exception {
        ConstructorExpression<NameValue> projection = Projections.constructor(
                NameValue.class,
                Expressions.constant("name"));
        Method readObject = ConstructorExpression.class.getDeclaredMethod("readObject", ObjectInputStream.class);
        readObject.setAccessible(true);

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedTrigger()))) {
            readObject.invoke(projection, input);
        }

        assertThat(projection.newInstance("bob").name()).isEqualTo("bob");
    }

    private static byte[] serializedTrigger() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject("unused serialization payload");
        }
        return bytes.toByteArray();
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
