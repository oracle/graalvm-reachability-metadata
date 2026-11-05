/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Discovers annotated message schemas for an application's serialization subsystem. */
public class SerializationSchemaDiscoveryTest {
    @Test
    void readsMessageNameAndTypedFields() throws IOException {
        Schema schema = new Schema();
        ClassReader reader = new ClassReader(Type.getInternalName(OrderMessage.class));

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!descriptor.equals(Type.getDescriptor(MessageSchema.class))) {
                    return null;
                }
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override
                    public void visit(String name, Object value) {
                        if (name.equals("name")) {
                            schema.name = (String) value;
                        }
                    }

                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        if (!name.equals("fields")) {
                            return null;
                        }
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String ignored,
                                    String fieldDescriptor) {
                                if (!fieldDescriptor.equals(Type.getDescriptor(FieldSchema.class))) {
                                    return null;
                                }
                                Field field = new Field();
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String attributeName, Object value) {
                                        if (attributeName.equals("name")) {
                                            field.name = (String) value;
                                        }
                                    }

                                    @Override
                                    public void visitEnum(String attributeName, String descriptor,
                                            String value) {
                                        if (attributeName.equals("type")) {
                                            field.type = FieldType.valueOf(value);
                                        }
                                    }

                                    @Override
                                    public void visitEnd() {
                                        schema.fields.add(new FieldDefinition(field.name, field.type));
                                    }
                                };
                            }
                        };
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("order", schema.name);
        assertEquals(List.of(
                new FieldDefinition("id", FieldType.STRING),
                new FieldDefinition("totalCents", FieldType.INTEGER)), schema.fields);
    }

    private static final class Schema {
        private String name;
        private final List<FieldDefinition> fields = new ArrayList<>();
    }

    private static final class Field {
        private String name;
        private FieldType type;
    }

    private record FieldDefinition(String name, FieldType type) {
    }

    private enum FieldType {
        STRING,
        INTEGER
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    private @interface MessageSchema {
        String name();

        FieldSchema[] fields();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    private @interface FieldSchema {
        String name();

        FieldType type();
    }

    @MessageSchema(name = "order", fields = {
            @FieldSchema(name = "id", type = FieldType.STRING),
            @FieldSchema(name = "totalCents", type = FieldType.INTEGER)
    })
    private static final class OrderMessage {
    }
}
