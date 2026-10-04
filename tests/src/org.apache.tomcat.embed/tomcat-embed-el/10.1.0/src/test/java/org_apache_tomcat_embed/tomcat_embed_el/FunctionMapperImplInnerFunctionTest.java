/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import jakarta.el.ELContext;
import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.ValueExpression;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {

    @Test
    void evaluatesFunctionAfterExpressionSerialization()
            throws ClassNotFoundException, IOException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction("lib", "join", FunctionLibrary.class.getName(), "join");
        ELContext context = processor.getELManager().getELContext();
        ValueExpression expression = ExpressionFactory.newInstance()
                .createValueExpression(context, "${lib:join()}", Object.class);

        ValueExpression restoredExpression = roundTrip(expression);

        Object value = restoredExpression.getValue(context);
        assertThat(value).isEqualTo("joined");
    }

    private static ValueExpression roundTrip(ValueExpression expression)
            throws ClassNotFoundException, IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(expression);
        }

        try (ObjectInputStream objectInput =
                new ObjectInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            return (ValueExpression) objectInput.readObject();
        }
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String join() {
            return "joined";
        }
    }
}
