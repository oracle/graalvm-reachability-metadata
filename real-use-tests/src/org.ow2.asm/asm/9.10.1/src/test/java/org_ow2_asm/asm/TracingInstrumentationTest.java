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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Instruments service methods with trace markers for an application's tracing subsystem. */
public class TracingInstrumentationTest {
    @Test
    void addsTraceMarkerToTransformedServiceMethod() throws IOException {
        ClassReader reader = new ClassReader(Type.getInternalName(BillingService.class));
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, name, descriptor,
                        signature, exceptions);
                if (!name.equals("charge")) {
                    return delegate;
                }
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        visitLdcInsn("trace:charge");
                        visitInsn(Opcodes.POP);
                    }
                };
            }
        }, 0);

        List<String> methods = new ArrayList<>();
        List<String> constants = new ArrayList<>();
        ClassReader transformed = new ClassReader(writer.toByteArray());
        transformed.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                methods.add(name + descriptor);
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        constants.add(String.valueOf(value));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("org_ow2_asm/asm/TracingInstrumentationTest$BillingService",
                transformed.getClassName());
        assertTrue(methods.contains("charge(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(constants.contains("trace:charge"));
    }

    private static final class BillingService {
        private String charge(String account) {
            return "charged:" + account;
        }
    }
}
