/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

import com.sun.el.ExpressionFactoryImpl;

import jakarta.el.ELProcessor;
import jakarta.el.ExpressionFactory;
import jakarta.el.StandardELContext;
import jakarta.el.ValueExpression;

import static org.assertj.core.api.Assertions.assertThat;

public class FunctionMapperImplInnerFunctionTest {
    private final ExpressionFactory factory = new ExpressionFactoryImpl();

    @Test
    void restoresFunctionWithPrimitiveParametersInPersistedExpression() throws Exception {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction("numbers", "larger", "java.lang.Math", "int max(int,int)");
        ValueExpression original = factory.createValueExpression(
                processor.getELManager().getELContext(), "${numbers:larger(17, 29)}", Integer.class);

        ValueExpression restored = persistAndRestore(original);
        StandardELContext context = new StandardELContext(factory);

        assertThat(restored.getExpressionString()).isEqualTo("${numbers:larger(17, 29)}");
        assertThat(restored.getValue(context)).isEqualTo(29);
        assertThat(restored.getValue(context)).isEqualTo(29);
    }

    @Test
    void restoresFunctionWithReferenceParameterInPersistedExpression() throws Exception {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction("numbers", "parse", "java.lang.Integer", "int parseInt(java.lang.String)");
        ValueExpression original = factory.createValueExpression(
                processor.getELManager().getELContext(), "${numbers:parse('37') + 5}", Integer.class);

        ValueExpression restored = persistAndRestore(original);

        assertThat(restored.getValue(new StandardELContext(factory))).isEqualTo(42);
    }

    private ValueExpression persistAndRestore(ValueExpression expression) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(expression);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ValueExpression) input.readObject();
        }
    }
}
