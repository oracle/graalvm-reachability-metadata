/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.ArrayList;
import java.util.List;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassWriterTest {
    @Test
    void recomputesStackFramesForOfflineClassFileProcessing() throws Exception {
        byte[] original = ClassFileLocator.ForClassLoader.read(MethodList.ForLoadedMethods.class).resolve();
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        reader.accept(writer, ClassReader.SKIP_FRAMES);

        // Inspect the rewritten class file without defining or executing it.
        ClassReader rewritten = new ClassReader(writer.toByteArray());
        assertEquals(reader.getClassName(), rewritten.getClassName());
        assertEquals(reader.getSuperName(), rewritten.getSuperName());
        List<String> mergedTypes = new ArrayList<>();
        rewritten.accept(new ClassVisitor(Opcodes.ASM6) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!name.equals("get") || !descriptor.equals("(I)Lnet/bytebuddy/description/method/MethodDescription$InDefinedShape;")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM6) {
                    @Override
                    public void visitFrame(int type, int localCount, Object[] locals, int stackCount, Object[] stack) {
                        for (int index = 0; index < stackCount; index++) {
                            if (stack[index] instanceof String mergedType) {
                                mergedTypes.add(mergedType);
                            }
                        }
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        assertTrue(mergedTypes.contains(MethodDescription.InDefinedShape.AbstractBase.class.getName().replace('.', '/')));
    }
}
