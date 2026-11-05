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
import org.objectweb.asm.Type;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Builds source locations for an application's diagnostic reporting subsystem. */
public class SourceLocationIndexTest {
    @Test
    void indexesSourceLocationsForServiceOperations() throws IOException {
        SourceIndex index = new SourceIndex();
        ClassReader reader = new ClassReader(Type.getInternalName(PaymentService.class));

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visitSource(String source, String debug) {
                index.sourceFile = source;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (!Set.of("authorize", "cancel").contains(name)) {
                    return null;
                }
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLineNumber(int line, Label start) {
                        index.locations.add(new SourceLocation(name, line));
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES);

        assertEquals("SourceLocationIndexTest.java", index.sourceFile);
        Set<String> operationNames = index.locations.stream()
                .map(SourceLocation::methodName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("authorize", "cancel"), operationNames);
        assertTrue(index.locations.stream().allMatch(location -> location.line() > 0));
        int firstAuthorizeLine = firstLineFor(index, "authorize");
        int firstCancelLine = firstLineFor(index, "cancel");
        assertTrue(firstAuthorizeLine < firstCancelLine);
    }

    private static int firstLineFor(SourceIndex index, String methodName) {
        return index.locations.stream()
                .filter(location -> location.methodName().equals(methodName))
                .mapToInt(SourceLocation::line)
                .min()
                .orElseThrow();
    }

    private static final class SourceIndex {
        private String sourceFile;
        private final List<SourceLocation> locations = new ArrayList<>();
    }

    private record SourceLocation(String methodName, int line) {
    }

    private static final class PaymentService {
        private String authorize(String accountId) {
            if (accountId.isEmpty()) {
                return "denied";
            }
            return "authorized:" + accountId;
        }

        private String cancel(String accountId) {
            return "cancelled:" + accountId;
        }
    }
}
