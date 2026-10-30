/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Constants;
import org.objectweb.asm.attrs.Annotation;
import org.objectweb.asm.attrs.RuntimeInvisibleAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuntimeInvisibleAnnotationsTest {
    @Test
    void preservesFieldAndMethodAnnotationsWithClassRetention() {
        Annotation fieldAnnotation = new Annotation("Lexample/FieldLabel;");
        fieldAnnotation.add("value", "title");
        RuntimeInvisibleAnnotations fieldAttribute = new RuntimeInvisibleAnnotations();
        fieldAttribute.annotations.add(fieldAnnotation);
        RuntimeInvisibleAnnotations methodAttribute = new RuntimeInvisibleAnnotations();
        methodAttribute.annotations.add(new Annotation("Lexample/ReadOnly;"));
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitField(Constants.ACC_PUBLIC, "title", "Ljava/lang/String;", null, fieldAttribute);
        writer.visitMethod(Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT, "title", "()Ljava/lang/String;", null, methodAttribute);

        AttributeTestSupport result = AttributeTestSupport.read(writer);
        RuntimeInvisibleAnnotations field = (RuntimeInvisibleAnnotations) result.fieldAttribute("title", "RuntimeInvisibleAnnotations");
        RuntimeInvisibleAnnotations method = (RuntimeInvisibleAnnotations) result.methodAttribute("title", "RuntimeInvisibleAnnotations");
        assertEquals(1, field.annotations.size());
        Annotation decodedField = (Annotation) field.annotations.get(0);
        assertEquals("Lexample/FieldLabel;", decodedField.type);
        assertEquals(Map.of("value", "title"), AttributeTestSupport.annotationValues(decodedField));
        assertEquals(1, method.annotations.size());
        Annotation decodedMethod = (Annotation) method.annotations.get(0);
        assertEquals("Lexample/ReadOnly;", decodedMethod.type);
        assertEquals(0, decodedMethod.elementValues.size());
        assertEquals("\n@Lexample/ReadOnly;", method.toString());
    }
}
