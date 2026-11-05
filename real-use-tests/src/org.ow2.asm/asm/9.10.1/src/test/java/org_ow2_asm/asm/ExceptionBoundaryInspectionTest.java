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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Inspects exception boundaries for an application's bytecode policy subsystem. */
public class ExceptionBoundaryInspectionTest {
    @Test
    void findsTheRecoveryBoundaryForCheckout() throws IOException {
        List<ExceptionBoundary> boundaries = new ArrayList<>();
        ClassReader reader = new ClassReader(CheckoutService.class.getName());

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!name.equals("checkout")) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitTryCatchBlock(Label start, Label end, Label handler,
                            String type) {
                        boundaries.add(new ExceptionBoundary(name, type));
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertEquals("org_ow2_asm/asm/ExceptionBoundaryInspectionTest$CheckoutService",
                reader.getClassName());
        assertEquals(List.of(new ExceptionBoundary(
                "checkout", "java/lang/IllegalStateException")), boundaries);
    }

    private record ExceptionBoundary(String methodName, String caughtType) {
    }

    private static final class CheckoutService {
        private String checkout(String cartId) {
            try {
                return charge(cartId);
            } catch (IllegalStateException exception) {
                return "retry:" + cartId;
            }
        }

        private String charge(String cartId) {
            return "charged:" + cartId;
        }
    }
}
