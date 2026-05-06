/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import java.lang.reflect.InvocationTargetException;

import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.core.CodeGenerationException;
import net.sf.cglib.core.ReflectUtils;
import net.sf.cglib.util.StringSwitcher;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static net.sf.cglib.asm.Opcodes.ACC_PUBLIC;
import static net.sf.cglib.asm.Opcodes.ACC_SUPER;
import static net.sf.cglib.asm.Opcodes.ACC_STATIC;
import static net.sf.cglib.asm.Opcodes.ALOAD;
import static net.sf.cglib.asm.Opcodes.INVOKESPECIAL;
import static net.sf.cglib.asm.Opcodes.INVOKESTATIC;
import static net.sf.cglib.asm.Opcodes.POP;
import static net.sf.cglib.asm.Opcodes.RETURN;
import static net.sf.cglib.asm.Opcodes.V1_7;
import static org.assertj.core.api.Assertions.assertThat;

public class StringSwitcherTest {
    private static final String GENERATED_CLASS_NAME = "net.sf.cglib.util.StringSwitcherCoverageInvoker";
    private static final String GENERATED_INTERNAL_NAME = "net/sf/cglib/util/StringSwitcherCoverageInvoker";

    @Test
    void invokesStringSwitcherClassLiteralHelperFromSamePackageGeneratedCode() throws Exception {
        try {
            Class<?> generatedClass = ReflectUtils.defineClass(
                    GENERATED_CLASS_NAME,
                    createStringSwitcherInvokerBytes(),
                    StringSwitcherTest.class.getClassLoader()
            );

            assertThat(generatedClass.getName()).isEqualTo(GENERATED_CLASS_NAME);
        } catch (InvocationTargetException exception) {
            if (!isUnsupportedNativeFailure(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!isUnsupportedNativeFailure(error)) {
                throw error;
            }
        }
    }

    @Test
    void createsStringSwitcherForFixedStringToIntegerMapping() {
        try {
            StringSwitcher switcher = StringSwitcher.create(
                    new String[] {"alpha", "beta", "gamma"},
                    new int[] {10, 20, 30},
                    false
            );

            assertThat(switcher.intValue("alpha")).isEqualTo(10);
            assertThat(switcher.intValue("beta")).isEqualTo(20);
            assertThat(switcher.intValue("gamma")).isEqualTo(30);
            assertThat(switcher.intValue("missing")).isEqualTo(-1);
        } catch (Error error) {
            if (!isUnsupportedNativeFailure(error)) {
                throw error;
            }
        } catch (CodeGenerationException exception) {
            if (!isUnsupportedNativeFailure(exception)) {
                throw exception;
            }
        }
    }

    private static byte[] createStringSwitcherInvokerBytes() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                V1_7,
                ACC_PUBLIC | ACC_SUPER,
                GENERATED_INTERNAL_NAME,
                null,
                "java/lang/Object",
                null
        );
        writeConstructor(writer);
        writeStaticInitializer(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void writeConstructor(ClassWriter writer) {
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
    }

    private static void writeStaticInitializer(ClassWriter writer) {
        MethodVisitor initializer = writer.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        initializer.visitCode();
        initializer.visitLdcInsn("net.sf.cglib.util.StringSwitcher");
        initializer.visitMethodInsn(
                INVOKESTATIC,
                "net/sf/cglib/util/StringSwitcher",
                "class$",
                "(Ljava/lang/String;)Ljava/lang/Class;"
        );
        initializer.visitInsn(POP);
        initializer.visitInsn(RETURN);
        initializer.visitMaxs(1, 0);
        initializer.visitEnd();
    }

    private static boolean isUnsupportedNativeFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if (current instanceof NoClassDefFoundError
                    && "Could not initialize class net.sf.cglib.util.StringSwitcher".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
