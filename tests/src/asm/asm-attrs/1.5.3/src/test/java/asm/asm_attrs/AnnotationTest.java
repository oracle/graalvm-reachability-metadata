/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.attrs.Annotation;
import org.objectweb.asm.attrs.Annotation.EnumConstValue;
import org.objectweb.asm.attrs.RuntimeVisibleAnnotations;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationTest {
    @Test
    void preservesScalarAnnotationElements() {
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("byteValue", (byte) 7);
        expected.put("charValue", 'Q');
        expected.put("shortValue", (short) 300);
        expected.put("intValue", 42);
        expected.put("longValue", 12345678901L);
        expected.put("floatValue", 1.25f);
        expected.put("doubleValue", 2.5d);
        expected.put("enabled", true);
        expected.put("disabled", false);
        expected.put("text", "document");
        Annotation annotation = new Annotation("Lexample/Options;");
        expected.forEach(annotation::add);

        Annotation result = roundTrip(annotation);
        assertEquals("Lexample/Options;", result.type);
        assertEquals(expected, values(result));
    }

    @Test
    void preservesPrimitiveAndStringArrayElements() {
        Annotation annotation = new Annotation("Lexample/Arrays;");
        annotation.add("bytes", new byte[]{1, -2});
        annotation.add("chars", new char[]{'a', 'z'});
        annotation.add("shorts", new short[]{5, 300});
        annotation.add("ints", new int[]{8, 90000});
        annotation.add("longs", new long[]{10L, 9876543210L});
        annotation.add("floats", new float[]{1.5f, -2.25f});
        annotation.add("doubles", new double[]{3.5d, -4.25d});
        annotation.add("booleans", new boolean[]{true, false});
        annotation.add("strings", new Object[]{"alpha", "beta"});

        Map<String, Object> result = values(roundTrip(annotation));
        assertEquals(9, result.size());
        assertArrayEquals(new byte[]{1, -2}, (byte[]) result.get("bytes"));
        assertArrayEquals(new char[]{'a', 'z'}, (char[]) result.get("chars"));
        assertArrayEquals(new short[]{5, 300}, (short[]) result.get("shorts"));
        assertArrayEquals(new int[]{8, 90000}, (int[]) result.get("ints"));
        assertArrayEquals(new long[]{10L, 9876543210L}, (long[]) result.get("longs"));
        assertArrayEquals(new float[]{1.5f, -2.25f}, (float[]) result.get("floats"));
        assertArrayEquals(new double[]{3.5d, -4.25d}, (double[]) result.get("doubles"));
        assertArrayEquals(new boolean[]{true, false}, (boolean[]) result.get("booleans"));
        assertArrayEquals(new Object[]{"alpha", "beta"}, (Object[]) result.get("strings"));
    }

    @Test
    void preservesClassEnumAndNestedAnnotationElements() {
        Annotation nested = new Annotation("Lexample/Label;");
        nested.add("value", "important");
        Annotation annotation = new Annotation("Lexample/Options;");
        annotation.add("target", Type.getType("Ljava/lang/String;"));
        annotation.add("policy", new EnumConstValue("Lexample/Policy;", "STRICT"));
        annotation.add("label", nested);
        annotation.add("labels", new Object[]{nested, new Annotation("Lexample/Marker;")});

        Map<String, Object> result = values(roundTrip(annotation));
        assertEquals("Ljava/lang/String;", ((Type) result.get("target")).getDescriptor());
        EnumConstValue policy = (EnumConstValue) result.get("policy");
        assertEquals("Lexample/Policy;", policy.typeName);
        assertEquals("STRICT", policy.constName);
        assertEquals("Lexample/Policy;:STRICT", policy.toString());
        Annotation label = (Annotation) result.get("label");
        assertEquals("Lexample/Label;", label.type);
        assertEquals(Map.of("value", "important"), values(label));
        assertEquals("@Lexample/Label; ( important )", label.toString());
        Object[] labels = (Object[]) result.get("labels");
        assertEquals(2, labels.length);
        assertEquals(Map.of("value", "important"), values((Annotation) labels[0]));
        Annotation marker = (Annotation) labels[1];
        assertEquals("Lexample/Marker;", marker.type);
        assertEquals(0, marker.elementValues.size());
        assertEquals("@Lexample/Marker;", marker.toString());
    }

    private static Annotation roundTrip(Annotation annotation) {
        ClassWriter writer = AttributeTestSupport.newClass();
        RuntimeVisibleAnnotations attribute = new RuntimeVisibleAnnotations();
        attribute.annotations.add(annotation);
        writer.visitAttribute(attribute);

        RuntimeVisibleAnnotations result = (RuntimeVisibleAnnotations) AttributeTestSupport.read(writer)
                .classAttribute("RuntimeVisibleAnnotations");
        assertEquals(1, result.annotations.size());
        return (Annotation) result.annotations.get(0);
    }

    private static Map<String, Object> values(Annotation annotation) {
        return AttributeTestSupport.annotationValues(annotation);
    }
}
