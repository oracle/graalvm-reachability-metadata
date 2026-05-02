/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_asm9_shaded;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import org.apache.xbean.asm9.ClassVisitor;
import org.apache.xbean.asm9.Opcodes;
import org.apache.xbean.asm9.tree.ClassNode;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class ConstantsTest {
    private static final String EXPERIMENTAL_API_REJECTION_MESSAGE =
            "ASM9_EXPERIMENTAL can only be used by classes compiled with --enable-preview";
    private static final String GENERATED_VISITOR_NAME =
            "org_apache_xbean.xbean_asm9_shaded.ConstantsPreviewVisitor";
    private static final String GENERATED_VISITOR_INTERNAL_NAME = GENERATED_VISITOR_NAME.replace('.', '/');
    private static final String GENERATED_VISITOR_RESOURCE = GENERATED_VISITOR_INTERNAL_NAME + ".class";
    private static final byte[] PREVIEW_CLASS_HEADER = {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, (byte) 0xFF, (byte) 0xFF
    };

    @Test
    void experimentalClassNodeChecksTheShadedTreeClassResource() {
        assertThatIllegalStateException()
                .isThrownBy(() -> createExperimentalClassNode())
                .withMessage(EXPERIMENTAL_API_REJECTION_MESSAGE);
    }

    @Test
    void experimentalVisitorAcceptsPreviewBytecodeResourceFromCallerClassLoader() throws Exception {
        try {
            PreviewResourceClassLoader classLoader = new PreviewResourceClassLoader();
            Class<? extends ClassVisitor> visitorClass = classLoader.definePreviewVisitor();
            ClassVisitor visitor = visitorClass.getConstructor().newInstance();

            assertThat(visitor.getClass().getName()).isEqualTo(GENERATED_VISITOR_NAME);
        } catch (InvocationTargetException exception) {
            rethrowUnlessUnsupportedFeatureError(exception.getCause());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        }
    }

    @SuppressWarnings("deprecation")
    private static ClassNode createExperimentalClassNode() {
        return new ClassNode(Opcodes.ASM10_EXPERIMENTAL);
    }

    @SuppressWarnings("deprecation")
    private static byte[] createPreviewVisitorClassBytes() throws IOException {
        ByteArrayOutputStream classBytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(classBytes)) {
            output.writeInt(0xCAFEBABE);
            output.writeShort(0);
            output.writeShort(52);
            output.writeShort(14);
            writeUtf8(output, GENERATED_VISITOR_INTERNAL_NAME);
            output.writeByte(7);
            output.writeShort(1);
            writeUtf8(output, "org/apache/xbean/asm9/ClassVisitor");
            output.writeByte(7);
            output.writeShort(3);
            writeUtf8(output, "<init>");
            writeUtf8(output, "(I)V");
            output.writeByte(12);
            output.writeShort(5);
            output.writeShort(6);
            output.writeByte(10);
            output.writeShort(4);
            output.writeShort(7);
            output.writeByte(3);
            output.writeInt(Opcodes.ASM10_EXPERIMENTAL);
            writeUtf8(output, "Code");
            writeUtf8(output, "()V");
            writeUtf8(output, "SourceFile");
            writeUtf8(output, "ConstantsPreviewVisitor.java");
            output.writeShort(0x0021);
            output.writeShort(2);
            output.writeShort(4);
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(1);
            writeDefaultConstructor(output);
            output.writeShort(1);
            output.writeShort(12);
            output.writeInt(2);
            output.writeShort(13);
        }
        return classBytes.toByteArray();
    }

    private static void writeDefaultConstructor(DataOutputStream output) throws IOException {
        output.writeShort(0x0001);
        output.writeShort(5);
        output.writeShort(11);
        output.writeShort(1);
        output.writeShort(10);
        output.writeInt(19);
        output.writeShort(2);
        output.writeShort(1);
        output.writeInt(7);
        output.writeByte(0x2A);
        output.writeByte(0x12);
        output.writeByte(9);
        output.writeByte(0xB7);
        output.writeShort(8);
        output.writeByte(0xB1);
        output.writeShort(0);
        output.writeShort(0);
    }

    private static void writeUtf8(DataOutputStream output, String value) throws IOException {
        output.writeByte(1);
        output.writeUTF(value);
    }

    private static void rethrowUnlessUnsupportedFeatureError(Throwable throwable) {
        if (throwable instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
            return;
        }
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException(throwable);
    }

    private static final class PreviewResourceClassLoader extends ClassLoader {
        private PreviewResourceClassLoader() {
            super(ConstantsTest.class.getClassLoader());
        }

        private Class<? extends ClassVisitor> definePreviewVisitor() throws IOException {
            byte[] classBytes = createPreviewVisitorClassBytes();
            Class<?> visitorClass = defineClass(GENERATED_VISITOR_NAME, classBytes, 0, classBytes.length);
            return visitorClass.asSubclass(ClassVisitor.class);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (GENERATED_VISITOR_RESOURCE.equals(name)) {
                return new ByteArrayInputStream(PREVIEW_CLASS_HEADER);
            }
            return super.getResourceAsStream(name);
        }
    }
}
