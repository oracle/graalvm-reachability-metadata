/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.asm.ClassReader;
import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.asm.Opcodes;
import net.sf.cglib.core.ReflectUtils;
import net.sf.cglib.transform.ClassReaderGenerator;
import net.sf.cglib.transform.TransformingClassGenerator;
import net.sf.cglib.transform.impl.AddDelegateTransformer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class AddDelegateTransformerTest {
    private static final String GENERATED_CLASS_NAME = "cglib.cglib_nodep.AddDelegateTransformerGeneratedTarget";

    @Test
    void addsInterfaceMethodsBackedByObjectConstructorDelegate() throws Exception {
        try {
            byte[] transformedBytes = transformGeneratedTarget();
            Class<?> transformedClass = ReflectUtils.defineClass(
                    GENERATED_CLASS_NAME,
                    transformedBytes,
                    AddDelegateTransformerTest.class.getClassLoader());

            DelegatedOperations operations = (DelegatedOperations) ReflectUtils.newInstance(transformedClass);

            assertThat(operations.describe()).isEqualTo("delegate:" + GENERATED_CLASS_NAME);
            assertThat(operations.targetIdentity()).isEqualTo(System.identityHashCode(operations));
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (Exception exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static byte[] transformGeneratedTarget() throws Exception {
        ClassReader reader = new ClassReader(createTargetClassBytes());
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        TransformingClassGenerator generator = new TransformingClassGenerator(
                new ClassReaderGenerator(reader, 0),
                new AddDelegateTransformer(
                        new Class[] {DelegatedOperations.class },
                        DelegatedOperationsImpl.class));

        generator.generateClass(writer);
        return writer.toByteArray();
    }

    private static byte[] createTargetClassBytes() {
        String internalName = GENERATED_CLASS_NAME.replace('.', '/');
        ClassWriter writer = new ClassWriter(0);
        writer.visit(
                Opcodes.V1_5,
                Opcodes.ACC_PUBLIC,
                internalName,
                null,
                "java/lang/Object",
                null);

        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();

        writer.visitEnd();
        return writer.toByteArray();
    }

    public interface DelegatedOperations {
        String describe();

        int targetIdentity();
    }

    public static class DelegatedOperationsImpl implements DelegatedOperations {
        private final Object target;

        public DelegatedOperationsImpl(Object target) {
            this.target = target;
        }

        public String describe() {
            return "delegate:" + target.getClass().getName();
        }

        public int targetIdentity() {
            return System.identityHashCode(target);
        }
    }
}
