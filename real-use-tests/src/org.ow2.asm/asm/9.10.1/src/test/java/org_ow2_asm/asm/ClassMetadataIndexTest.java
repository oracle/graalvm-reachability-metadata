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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Indexes class hierarchy metadata for an application's plugin discovery subsystem. */
public class ClassMetadataIndexTest {
    @Test
    void indexesPluginIdentityAndImplementedContracts() {
        ClassReader reader = new ClassReader(createPluginClass());
        Metadata metadata = new Metadata();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                metadata.access = access;
                metadata.name = name;
                metadata.superName = superName;
                metadata.interfaces = interfaces;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("com/example/billing/InvoicePlugin", metadata.name);
        assertEquals("java/lang/Object", metadata.superName);
        assertArrayEquals(new String[] {Type.getInternalName(PluginContract.class)},
                metadata.interfaces);
        assertTrue((metadata.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue((metadata.access & Opcodes.ACC_FINAL) != 0);
        assertEquals(metadata.name, reader.getClassName());
        assertEquals(metadata.superName, reader.getSuperName());
        assertEquals(Arrays.toString(metadata.interfaces), Arrays.toString(reader.getInterfaces()));
    }

    private static byte[] createPluginClass() {
        String className = "com/example/billing/InvoicePlugin";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, className, null,
                Type.getInternalName(Object.class), new String[] {Type.getInternalName(PluginContract.class)});
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V",
                null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(Object.class),
                "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class Metadata {
        private int access;
        private String name;
        private String superName;
        private String[] interfaces;
    }

    private interface PluginContract {
    }
}
