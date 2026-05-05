/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationValue;
import com.sun.codemodel.JAnnotationWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFormatter;
import java.io.StringWriter;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TypedAnnotationWriterTest {
    @Test
    void typedAnnotationWriterProxyWritesMembersAndExposesBaseWriterMethods() throws Exception {
        JCodeModel codeModel = new JCodeModel();
        JDefinedClass generatedClass = codeModel._class("example.TypedAnnotationWriterGenerated");

        SampleAnnotationWriter writer = generatedClass.annotate2(SampleAnnotationWriter.class);

        assertThat(writer.getAnnotationType()).isSameAs(SampleAnnotation.class);
        JAnnotationUse annotationUse = writer.getAnnotationUse();
        assertThat(annotationUse.getAnnotationClass().fullName())
                .isEqualTo(SampleAnnotation.class.getName().replace('$', '.'));

        assertThat(writer.value("alpha")).isSameAs(writer);
        assertThat(writer.count(7)).isSameAs(writer);
        assertThat(writer.type(String.class)).isSameAs(writer);

        Map<String, JAnnotationValue> members = annotationUse.getAnnotationMembers();
        assertThat(members).containsOnlyKeys("value", "count", "type");
        assertThat(render(annotationUse))
                .contains("value = \"alpha\"")
                .contains("count = 7")
                .contains("java.lang.String.class");
    }

    private static String render(JAnnotationUse annotationUse) {
        StringWriter output = new StringWriter();
        annotationUse.generate(new JFormatter(output));
        return output.toString();
    }

    public @interface SampleAnnotation {
        String value();

        int count();

        Class<?> type();
    }

    public interface SampleAnnotationWriter extends JAnnotationWriter<SampleAnnotation> {
        @Override
        JAnnotationUse getAnnotationUse();

        @Override
        Class<SampleAnnotation> getAnnotationType();

        SampleAnnotationWriter value(String value);

        SampleAnnotationWriter count(int count);

        SampleAnnotationWriter type(Class<?> type);
    }
}
