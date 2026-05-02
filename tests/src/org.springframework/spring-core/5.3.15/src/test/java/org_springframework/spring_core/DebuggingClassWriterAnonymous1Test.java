/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.cglib.core.DebuggingClassWriter;

public class DebuggingClassWriterAnonymous1Test {

    @Test
    void writesClassBytesAndAsmTraceWhenDebuggingIsEnabled() throws IOException {
        String debugLocation = System.getProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY);
        if (debugLocation == null) {
            Path fallbackDebugLocation = Files.createTempDirectory("spring-cglib-debug");
            debugLocation = fallbackDebugLocation.toString();
            System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, debugLocation);
        }
        String simpleClassName = "GeneratedDebuggingClassWriter" + Long.toUnsignedString(System.nanoTime());
        String internalClassName = "org_springframework/spring_core/" + simpleClassName;

        DebuggingClassWriter writer = new DebuggingClassWriter(0);
        writer.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                internalClassName,
                null,
                "java/lang/Object",
                null
        );
        writeDefaultConstructor(writer);
        writer.visitEnd();

        byte[] classBytes = writer.toByteArray();

        assertThat(classBytes).startsWith(new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});
        assertThat(Files.readAllBytes(Paths.get(debugLocation, internalClassName + ".class"))).isEqualTo(classBytes);
        assertThat(Files.readString(Paths.get(debugLocation, internalClassName + ".asm"))).contains(simpleClassName);
    }

    private static void writeDefaultConstructor(DebuggingClassWriter writer) {
        MethodVisitor methodVisitor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        methodVisitor.visitCode();
        methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        methodVisitor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        methodVisitor.visitInsn(Opcodes.RETURN);
        methodVisitor.visitMaxs(1, 1);
        methodVisitor.visitEnd();
    }
}
