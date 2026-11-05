/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ow2_asm.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Generates constant-dynamic configuration values for an application's build tooling subsystem. */
public class ConstantDynamicConfigurationTest {
    @Test
    void preservesConstantDynamicConfigurationField() {
        Handle bootstrap = new Handle(
                Opcodes.H_INVOKESTATIC,
                "example/ConfigurationBootstrap",
                "resolve",
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;"
                        + "Ljava/lang/String;)Ljava/lang/Object;",
                false);
        ConstantDynamic region = new ConstantDynamic(
                "REGION", "Ljava/lang/String;", bootstrap, "eu-west-1");

        ClassReader reader = new ClassReader(createConfigurationClass(region));
        GeneratedField field = new GeneratedField();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                    String signature, Object value) {
                if (name.equals("REGION")) {
                    field.access = access;
                    field.descriptor = descriptor;
                    field.value = value;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        ConstantDynamic parsed = assertInstanceOf(ConstantDynamic.class, field.value);
        assertEquals("example/GeneratedConfiguration", reader.getClassName());
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, field.access);
        assertEquals("Ljava/lang/String;", field.descriptor);
        assertEquals("REGION", parsed.getName());
        assertEquals("Ljava/lang/String;", parsed.getDescriptor());
        assertEquals("eu-west-1", parsed.getBootstrapMethodArgument(0));
        assertEquals("example/ConfigurationBootstrap", parsed.getBootstrapMethod().getOwner());
        assertEquals("resolve", parsed.getBootstrapMethod().getName());
    }

    private static byte[] createConfigurationClass(ConstantDynamic region) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "example/GeneratedConfiguration", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "REGION", "Ljava/lang/String;", null, region).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class GeneratedField {
        private int access;
        private String descriptor;
        private Object value;
    }
}
