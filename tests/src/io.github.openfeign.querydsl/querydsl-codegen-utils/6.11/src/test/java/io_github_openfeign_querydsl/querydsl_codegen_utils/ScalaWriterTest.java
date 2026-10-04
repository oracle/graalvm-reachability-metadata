/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;

import com.querydsl.codegen.utils.ScalaWriter;
import com.querydsl.codegen.utils.model.SimpleType;
import org.junit.jupiter.api.Test;

public class ScalaWriterTest {

    @Test
    void writesAnnotatedScalaClass() throws Exception {
        StringBuilder source = new StringBuilder();
        ScalaWriter writer = new ScalaWriter(source);

        writer.packageDecl("example.generated");
        writer.annotation(suppressWarningsAnnotation());
        writer.annotation(deprecatedAnnotation());
        writer.beginClass(new SimpleType("example.generated.Sample", "example.generated", "Sample"));
        writer.end();

        assertThat(source.toString())
                .contains("package example.generated", "@Deprecated", "Sample");
    }

    private static SuppressWarnings suppressWarningsAnnotation() {
        return new SuppressWarnings() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return SuppressWarnings.class;
            }

            @Override
            public String[] value() {
                return new String[] {"unchecked"};
            }
        };
    }

    private static Deprecated deprecatedAnnotation() {
        return new Deprecated() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Deprecated.class;
            }

            @Override
            public String since() {
                return "";
            }

            @Override
            public boolean forRemoval() {
                return false;
            }
        };
    }
}
