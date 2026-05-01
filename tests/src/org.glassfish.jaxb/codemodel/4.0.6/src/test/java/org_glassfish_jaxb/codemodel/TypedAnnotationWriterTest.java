/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class TypedAnnotationWriterTest {
    @Test
    void typedAnnotationWriterProxyWritesAnnotationMembers() throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JDefinedClass generatedClass = codeModel._class("example.TypedAnnotationTarget");

        SampleAnnotationWriter writer = generatedClass.annotate2(SampleAnnotationWriter.class);
        JAnnotationUse annotationUse = writer.getAnnotationUse();

        assertThat(writer.getAnnotationType()).isEqualTo(SampleAnnotation.class);
        assertThat(writer.value("codemodel")).isSameAs(writer);
        writer.enabled(true).count(3).type(String.class);

        assertThat(annotationUse.getAnnotationClass().fullName()).isEqualTo(SampleAnnotation.class.getCanonicalName());
        assertThat(annotationUse.getAnnotationMembers()).containsOnlyKeys("value", "enabled", "count", "type");

        ByteArrayOutputStream sourceOutput = new ByteArrayOutputStream();
        codeModel.build(new SingleStreamCodeWriter(sourceOutput));
        String generatedSource = sourceOutput.toString(StandardCharsets.UTF_8);

        assertThat(generatedSource)
                .contains("SampleAnnotation")
                .contains("value")
                .contains("\"codemodel\"")
                .contains("enabled")
                .contains("true")
                .contains("count")
                .contains("3")
                .contains("String.class");
    }

    public @interface SampleAnnotation {
        String value();

        boolean enabled() default false;

        int count();

        Class<?> type();
    }

    public interface SampleAnnotationWriter extends JAnnotationWriter<SampleAnnotation> {
        @Override
        JAnnotationUse getAnnotationUse();

        @Override
        Class<SampleAnnotation> getAnnotationType();

        SampleAnnotationWriter value(String value);

        SampleAnnotationWriter enabled(boolean enabled);

        SampleAnnotationWriter count(int count);

        SampleAnnotationWriter type(Class<?> type);
    }
}
