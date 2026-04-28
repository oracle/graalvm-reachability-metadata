/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_qdox.qdox;

import com.thoughtworks.qdox.JavaDocBuilder;
import com.thoughtworks.qdox.model.Annotation;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;
import com.thoughtworks.qdox.model.annotation.EvaluatingVisitor;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

public class EvaluatingVisitorTest {
    @SuppressWarnings("checkstyle:annotationAccess")
    @Test
    void evaluatesStringConcatenationInParsedAnnotationValues() throws Exception {
        JavaDocBuilder builder = new JavaDocBuilder();
        builder.addSource(new StringReader("""
                package samples;

                public @interface Marker {
                    String label();
                }

                @Marker(label = "release-" + 7)
                public class AnnotatedSample {
                }
                """));
        JavaClass annotatedClass = builder.getClassByName("samples.AnnotatedSample");
        Annotation annotation = annotatedClass.getAnnotations()[0];
        EvaluatingVisitor visitor = new ParsedAnnotationVisitor();

        Object label = visitor.getValue(annotation, "label");

        assertThat(label).isEqualTo("release-7");
    }

    private static class ParsedAnnotationVisitor extends EvaluatingVisitor {
        @Override
        protected Object getFieldReferenceValue(JavaField javaField) {
            throw new UnsupportedOperationException("Field references are not used by this test.");
        }
    }
}
