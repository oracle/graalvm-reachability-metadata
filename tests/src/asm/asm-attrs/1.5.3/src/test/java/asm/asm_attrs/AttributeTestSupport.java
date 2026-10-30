/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package asm.asm_attrs;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.CodeAdapter;
import org.objectweb.asm.CodeVisitor;
import org.objectweb.asm.Constants;
import org.objectweb.asm.attrs.Annotation;
import org.objectweb.asm.attrs.Attributes;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

final class AttributeTestSupport extends ClassWriter {
    private final Map<String, Attribute> classAttributes = new HashMap<>();
    private final Map<String, Attribute> fieldAttributes = new HashMap<>();
    private final Map<String, Attribute> methodAttributes = new HashMap<>();
    private final Map<String, Attribute> codeAttributes = new HashMap<>();

    private AttributeTestSupport() {
        super(false);
    }

    static ClassWriter newClass() {
        ClassWriter writer = new ClassWriter(false);
        writer.visit(Constants.V1_5, Constants.ACC_PUBLIC | Constants.ACC_ABSTRACT | Constants.ACC_SUPER,
                "example/Document", "java/lang/Object", null, "Document.java");
        return writer;
    }

    static AttributeTestSupport read(ClassWriter writer) {
        writer.visitEnd();
        AttributeTestSupport result = new AttributeTestSupport();
        new ClassReader(writer.toByteArray()).accept(result, Attributes.getDefaultAttributes(), false);
        return result;
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        classAttributes.put(attribute.type, attribute);
    }

    @Override
    public void visitField(int access, String name, String descriptor, Object value, Attribute attributes) {
        fieldAttributes.put(name, attributes);
        super.visitField(access, name, descriptor, value, attributes);
    }

    @Override
    public CodeVisitor visitMethod(int access, String name, String descriptor, String[] exceptions, Attribute attributes) {
        methodAttributes.put(name, attributes);
        return new CodeAdapter(super.visitMethod(access, name, descriptor, exceptions, attributes)) {
            @Override
            public void visitAttribute(Attribute attribute) {
                codeAttributes.put(name + ":" + attribute.type, attribute);
                super.visitAttribute(attribute);
            }
        };
    }

    Attribute classAttribute(String type) {
        Attribute attribute = classAttributes.get(type);
        assertNotNull(attribute, "Missing class attribute: " + type);
        return attribute;
    }

    Attribute fieldAttribute(String name, String type) {
        return find(fieldAttributes.get(name), type);
    }

    Attribute methodAttribute(String name, String type) {
        return find(methodAttributes.get(name), type);
    }

    Attribute codeAttribute(String name, String type) {
        Attribute attribute = codeAttributes.get(name + ":" + type);
        assertNotNull(attribute, "Missing code attribute: " + type);
        return attribute;
    }

    static Map<String, Object> annotationValues(Annotation annotation) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object entry : annotation.elementValues) {
            Object[] pair = (Object[]) entry;
            result.put((String) pair[0], pair[1]);
        }
        return result;
    }

    private static Attribute find(Attribute attributes, String type) {
        for (Attribute attribute = attributes; attribute != null; attribute = attribute.next) {
            if (type.equals(attribute.type)) {
                return attribute;
            }
        }
        return fail("Missing member attribute: " + type);
    }
}
