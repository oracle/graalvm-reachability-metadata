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
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Inspects Java record schemas for an application's command registration subsystem. */
public class RecordSchemaInspectionTest {
    @Test
    void readsRecordComponentsAndTheirApplicationAnnotation() throws IOException {
        List<String> components = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        ClassReader reader = new ClassReader(Type.getInternalName(PaymentCommand.class));
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public RecordComponentVisitor visitRecordComponent(
                    String name, String descriptor, String signature) {
                components.add(name + ":" + descriptor);
                return new RecordComponentVisitor(Opcodes.ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                        if (descriptor.equals(Type.getDescriptor(CommandLabel.class))) {
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String name, Object value) {
                                    if (name.equals("value")) {
                                        labels.add((String) value);
                                    }
                                }
                            };
                        }
                        return null;
                    }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("org_ow2_asm/asm/RecordSchemaInspectionTest$PaymentCommand", reader.getClassName());
        assertEquals(List.of("id:Ljava/lang/String;", "amount:I"), components);
        assertEquals(List.of("payment identifier"), labels);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    private @interface CommandLabel {
        String value();
    }

    private record PaymentCommand(@CommandLabel("payment identifier") String id, int amount) {
    }
}
