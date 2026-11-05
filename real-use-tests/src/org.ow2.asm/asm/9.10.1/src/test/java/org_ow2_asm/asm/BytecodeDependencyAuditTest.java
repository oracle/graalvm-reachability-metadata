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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Audits application bytecode dependencies for a build verification subsystem. */
public class BytecodeDependencyAuditTest {
    @Test
    void recordsServiceDependenciesFromCompiledBytecode() throws IOException {
        List<String> invocations = new ArrayList<>();
        ClassReader reader = new ClassReader(Type.getInternalName(OrderService.class));

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                            String methodDescriptor, boolean isInterface) {
                        invocations.add(owner + "." + methodName + methodDescriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        String catalogOwner = Type.getInternalName(PricingCatalog.class);
        String auditOwner = Type.getInternalName(AuditSink.class);
        assertEquals(Type.getInternalName(OrderService.class), reader.getClassName());
        assertTrue(invocations.contains(catalogOwner + ".priceFor(Ljava/lang/String;)Ljava/lang/String;"));
        assertTrue(invocations.contains(auditOwner
                + ".record(Ljava/lang/String;Ljava/lang/String;)V"));
    }

    private static final class OrderService {
        private final PricingCatalog catalog = new PricingCatalog();
        private final AuditSink audit = new AuditSink();

        private String submit(String orderId) {
            String price = catalog.priceFor(orderId);
            audit.record(orderId, price);
            return price;
        }
    }

    private static final class PricingCatalog {
        private String priceFor(String orderId) {
            return orderId.equals("order-7") ? "19.95" : "0.00";
        }
    }

    private static final class AuditSink {
        private void record(String orderId, String price) {
            if (orderId.isEmpty() || price.isEmpty()) {
                throw new IllegalArgumentException("audit entry must be populated");
            }
        }
    }
}
