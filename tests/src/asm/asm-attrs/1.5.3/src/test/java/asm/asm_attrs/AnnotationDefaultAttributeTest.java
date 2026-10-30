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
import org.objectweb.asm.Type;
import org.objectweb.asm.attrs.Annotation;
import org.objectweb.asm.attrs.Annotation.EnumConstValue;
import org.objectweb.asm.attrs.AnnotationDefaultAttribute;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationDefaultAttributeTest {
    @Test
    void preservesAnnotationElementDefaults() {
        ClassWriter writer = new ClassWriter(false);
        writer.visit(Constants.V1_5, Constants.ACC_PUBLIC | Constants.ACC_INTERFACE | Constants.ACC_ABSTRACT | Constants.ACC_ANNOTATION,
                "example/Options", "java/lang/Object", new String[]{"java/lang/annotation/Annotation"}, null);
        addElement(writer, "text", "Ljava/lang/String;", "untitled");
        addElement(writer, "count", "I", 12);
        addElement(writer, "enabled", "Z", true);
        addElement(writer, "target", "Ljava/lang/Class;", Type.getType("Ljava/lang/String;"));
        addElement(writer, "policy", "Lexample/Policy;", new EnumConstValue("Lexample/Policy;", "STRICT"));
        addElement(writer, "ranks", "[I", new int[]{2, 4, 6});
        Annotation label = new Annotation("Lexample/Label;");
        label.add("value", "default-label");
        addElement(writer, "label", "Lexample/Label;", label);

        AttributeTestSupport result = AttributeTestSupport.read(writer);
        AnnotationDefaultAttribute text = (AnnotationDefaultAttribute) result.methodAttribute("text", "AnnotationDefault");
        assertEquals("untitled", text.defaultValue);
        assertEquals("default untitled", text.toString());
        assertEquals(12, defaultValue(result, "count"));
        assertEquals(true, defaultValue(result, "enabled"));
        assertEquals("Ljava/lang/String;", ((Type) defaultValue(result, "target")).getDescriptor());
        EnumConstValue policy = (EnumConstValue) defaultValue(result, "policy");
        assertEquals("Lexample/Policy;", policy.typeName);
        assertEquals("STRICT", policy.constName);
        assertArrayEquals(new int[]{2, 4, 6}, (int[]) defaultValue(result, "ranks"));
        Annotation decodedLabel = (Annotation) defaultValue(result, "label");
        assertEquals("Lexample/Label;", decodedLabel.type);
        assertEquals(Map.of("value", "default-label"), AttributeTestSupport.annotationValues(decodedLabel));
    }

    private static void addElement(ClassWriter writer, String name, String descriptor, Object value) {
        writer.visitMethod(Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT, name, "()" + descriptor, null,
                new AnnotationDefaultAttribute(value));
    }

    private static Object defaultValue(AttributeTestSupport result, String name) {
        return ((AnnotationDefaultAttribute) result.methodAttribute(name, "AnnotationDefault")).defaultValue;
    }
}
