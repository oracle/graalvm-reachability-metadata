/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import static org.assertj.core.api.Assertions.assertThat;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.builder.impl.EvaluatingVisitor;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

public class EvaluatingVisitorTest {
    @Test
    void evaluatesParsedAnnotationExpressions() {
        JavaProjectBuilder builder = new JavaProjectBuilder();
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
        JavaClass javaClassAnnotationAccess = builder.getClassByName("sample.AnnotatedSample");
        JavaAnnotation annotation = javaClassAnnotationAccess.getAnnotations().get(0);
        EvaluatingVisitor visitor = new ConstantOnlyEvaluatingVisitor();

        assertThat(visitor.getValue(annotation, "total")).isEqualTo(7);
        assertThat(visitor.getValue(annotation, "label")).isEqualTo("qdox");
        assertThat(visitor.getListValue(annotation, "numbers")).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void evaluatesParsedAnnotationReferenceTypeCasts() {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        builder.addSource(new StringReader("""
                package sample;

                @interface Values {
                    String label();
                }

                @Values(label = (java.lang.String) "qdox")
                public class AnnotatedReferenceCast {
                }
                """));
        JavaClass javaClassAnnotationAccess = builder.getClassByName("sample.AnnotatedReferenceCast");
        JavaAnnotation annotation = javaClassAnnotationAccess.getAnnotations().get(0);
        EvaluatingVisitor visitor = new ConstantOnlyEvaluatingVisitor();

        assertThat(visitor.getValue(annotation, "label")).isEqualTo("qdox");
    }

    private static final class ConstantOnlyEvaluatingVisitor extends EvaluatingVisitor {
        @Override
        protected Object getFieldReferenceValue(JavaField javaField) {
            throw new UnsupportedOperationException("Field references are not used by this test.");
        }
    }
}
