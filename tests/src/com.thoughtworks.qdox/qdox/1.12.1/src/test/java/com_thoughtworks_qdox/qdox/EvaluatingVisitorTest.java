/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.annotation.EvaluatingVisitor;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

public class EvaluatingVisitorTest {
    @Test
    void evaluatesParsedAnnotationExpressions() {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.addSource(new StringReader("""
                package sample;

                @interface Values {
                    int total();
                    String label();
                    int[] numbers();
                }

                @Values(total = 1 + 2 * 3, label = "q" + "dox", numbers = {1, 2, 3})
                public class AnnotatedSample {
                }
                """));
        JavaClass javaClass = builder.getClassByName("sample.AnnotatedSample");
        Annotation annotation = javaClass.getAnnotations()[0];
        EvaluatingVisitor visitor = new ConstantOnlyEvaluatingVisitor();

        assertThat(visitor.getValue(annotation, "total")).isEqualTo(7);
        assertThat(visitor.getValue(annotation, "label")).isEqualTo("qdox");
        assertThat(visitor.getListValue(annotation, "numbers")).containsExactly(1, 2, 3);
    }

    private static final class ConstantOnlyEvaluatingVisitor extends EvaluatingVisitor {
        @Override
        protected Object getFieldReferenceValue(JavaField javaField) {
            throw new UnsupportedOperationException("Field references are not used by this test.");
        }
    }
}
