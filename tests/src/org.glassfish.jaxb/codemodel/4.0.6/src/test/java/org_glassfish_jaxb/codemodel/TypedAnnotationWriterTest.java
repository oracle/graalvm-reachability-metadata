/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.codemodel;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JAnnotationWriter;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.writer.SingleStreamCodeWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TypedAnnotationWriterTest {
    @Test
    void writesAnnotationThroughTypedProxy() throws JClassAlreadyExistsException, IOException {
        JCodeModel codeModel = new JCodeModel();
        JDefinedClass generatedClass = codeModel._class("example.GeneratedTypedAnnotation");

        TypedAnnotationWriterInterface writer = generatedClass.annotate2(TypedAnnotationWriterInterface.class);
        JAnnotationUse annotationUse = writer.getAnnotationUse();

        assertThat(writer.getAnnotationType()).isEqualTo(TypedAnnotation.class);
        assertThat(annotationUse.getAnnotationClass().fullName()).isEqualTo(TypedAnnotation.class.getCanonicalName());
        assertThat(writer.name("customName")).isSameAs(writer);
        writer.number(7)
                .target(String.class)
                .kind(SampleKind.FIRST)
                .tags("alpha")
                .tags("beta");
        writer.nested().text("nestedText");
        writer.nestedItems().text("itemText");

        Map<String, ?> members = annotationUse.getAnnotationMembers();
        assertThat(members).containsOnlyKeys("name", "number", "target", "kind", "tags", "nested", "nestedItems");

        ByteArrayOutputStream generatedSource = new ByteArrayOutputStream();
        codeModel.build(new SingleStreamCodeWriter(generatedSource));

        assertThat(generatedSource.toString(StandardCharsets.UTF_8))
                .contains("GeneratedTypedAnnotation")
                .contains("customName")
                .contains("nestedText")
                .contains("itemText")
                .contains("alpha")
                .contains("beta");
    }

    public enum SampleKind {
        FIRST,
        SECOND
    }

    public @interface TypedAnnotation {
        String name();

        int number();

        Class<?> target();

        SampleKind kind();

        NestedAnnotation nested();

        String[] tags();

        NestedAnnotation[] nestedItems();
    }

    public @interface NestedAnnotation {
        String text();
    }

    public interface TypedAnnotationWriterInterface extends JAnnotationWriter<TypedAnnotation> {
        @Override
        JAnnotationUse getAnnotationUse();

        @Override
        Class<TypedAnnotation> getAnnotationType();

        TypedAnnotationWriterInterface name(String value);

        TypedAnnotationWriterInterface number(int value);

        TypedAnnotationWriterInterface target(Class<?> value);

        TypedAnnotationWriterInterface kind(SampleKind value);

        NestedAnnotationWriterInterface nested();

        TypedAnnotationWriterInterface tags(String value);

        NestedAnnotationWriterInterface nestedItems();
    }

    public interface NestedAnnotationWriterInterface extends JAnnotationWriter<NestedAnnotation> {
        NestedAnnotationWriterInterface text(String value);
    }
}
