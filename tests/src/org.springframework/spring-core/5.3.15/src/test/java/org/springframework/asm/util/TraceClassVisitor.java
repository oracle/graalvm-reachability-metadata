/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.springframework.asm.util;

import java.io.PrintWriter;

import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Opcodes;

public class TraceClassVisitor extends ClassVisitor {

    private final PrintWriter printWriter;

    public TraceClassVisitor(ClassVisitor classVisitor, PrintWriter printWriter) {
        super(Opcodes.ASM9, classVisitor);
        this.printWriter = printWriter;
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces
    ) {
        printWriter.println(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public void visitEnd() {
        printWriter.println("visitEnd");
        super.visitEnd();
    }
}
