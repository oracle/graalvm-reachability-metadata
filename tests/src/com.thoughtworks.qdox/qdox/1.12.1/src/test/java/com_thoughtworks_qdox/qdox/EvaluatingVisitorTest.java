/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.annotation.AnnotationAdd;
import com.thoughtworks.qdox.model.annotation.AnnotationConstant;
import com.thoughtworks.qdox.model.annotation.EvaluatingVisitor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EvaluatingVisitorTest {
    @Test
    void evaluatesStringConcatenationAnnotationExpression() {
        EvaluatingVisitor visitor = new ConstantEvaluatingVisitor();
        AnnotationAdd expression = new AnnotationAdd(
                new AnnotationConstant("qdox", "\"qdox\""),
                new AnnotationConstant(Integer.valueOf(121), "121"));

        Object value = expression.accept(visitor);

        assertThat(value).isEqualTo("qdox121");
    }

    private static final class ConstantEvaluatingVisitor extends EvaluatingVisitor {
        @Override
        protected Object getFieldReferenceValue(JavaField javaField) {
            throw new AssertionError("This test does not evaluate field references");
        }
    }
}
