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
import org.objectweb.asm.attrs.RuntimeInvisibleParameterAnnotations;
import org.objectweb.asm.attrs.RuntimeVisibleParameterAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParameterAnnotationsTest {
    @Test
    void preservesVisibleAndInvisibleAnnotationsAndUnannotatedParameters() {
        Annotation name = new Annotation("Lexample/Name;");
        name.add("value", "title");
        RuntimeVisibleParameterAnnotations visible = new RuntimeVisibleParameterAnnotations();
        visible.parameters.add(List.of(name, new Annotation("Lexample/Required;")));
        visible.parameters.add(List.of());
        RuntimeInvisibleParameterAnnotations invisible = new RuntimeInvisibleParameterAnnotations();
        invisible.parameters.add(List.of());
        invisible.parameters.add(List.of(new Annotation("Lexample/Internal;")));
        visible.next = invisible;
        ClassWriter writer = AttributeTestSupport.newClass();
        writer.visitMethod(Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT, "update", "(Ljava/lang/String;I)V", null, visible);

        AttributeTestSupport result = AttributeTestSupport.read(writer);
        RuntimeVisibleParameterAnnotations decodedVisible = (RuntimeVisibleParameterAnnotations) result
                .methodAttribute("update", "RuntimeVisibleParameterAnnotations");
        RuntimeInvisibleParameterAnnotations decodedInvisible = (RuntimeInvisibleParameterAnnotations) result
                .methodAttribute("update", "RuntimeInvisibleParameterAnnotations");
        assertEquals(2, decodedVisible.parameters.size());
        List<?> firstVisible = (List<?>) decodedVisible.parameters.get(0);
        assertEquals(2, firstVisible.size());
        Annotation decodedName = (Annotation) firstVisible.get(0);
        assertEquals("Lexample/Name;", decodedName.type);
        assertEquals(Map.of("value", "title"), AttributeTestSupport.annotationValues(decodedName));
        assertEquals("Lexample/Required;", ((Annotation) firstVisible.get(1)).type);
        assertEquals(List.of(), decodedVisible.parameters.get(1));
        assertEquals(2, decodedInvisible.parameters.size());
        assertEquals(List.of(), decodedInvisible.parameters.get(0));
        List<?> secondInvisible = (List<?>) decodedInvisible.parameters.get(1);
        assertEquals(1, secondInvisible.size());
        assertEquals("Lexample/Internal;", ((Annotation) secondInvisible.get(0)).type);
        assertEquals("<none>, \n@Lexample/Internal;", decodedInvisible.toString());
    }
}
