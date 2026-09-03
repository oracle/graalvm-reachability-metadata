/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import java.util.ArrayList;
import java.util.List;

import net.sf.cglib.asm.ClassAdapter;
import net.sf.cglib.asm.ClassReader;
import net.sf.cglib.asm.ClassVisitor;
import net.sf.cglib.asm.ClassWriter;
import net.sf.cglib.asm.MethodVisitor;
import net.sf.cglib.core.ClassGenerator;
import net.sf.cglib.transform.TransformingClassGenerator;
import net.sf.cglib.transform.impl.AddDelegateTransformer;
import org.junit.jupiter.api.Test;

import static net.sf.cglib.asm.Opcodes.ACC_PUBLIC;
import static net.sf.cglib.asm.Opcodes.ALOAD;
import static net.sf.cglib.asm.Opcodes.INVOKESPECIAL;
import static net.sf.cglib.asm.Opcodes.RETURN;
import static net.sf.cglib.asm.Opcodes.V1_5;
import static org.assertj.core.api.Assertions.assertThat;

public class AddDelegateTransformerTest {
    private static final String TARGET_CLASS = "cglib/cglib_nodep/GeneratedDelegateTarget";

    @Test
    void addsDelegateInterfaceAndMethodsToGeneratedClass() throws Exception {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassGenerator generator = new TransformingClassGenerator(
                new DelegateTargetGenerator(),
                new AddDelegateTransformer(new Class<?>[]{GreetingDelegate.class}, GreetingDelegateImplementation.class)
        );

        generator.generateClass(writer);

        ClassReader reader = new ClassReader(writer.toByteArray());
        List<String> methods = new ArrayList<>();
        reader.accept(new MethodCollectingClassAdapter(methods), 0);

        assertThat(reader.getInterfaces())
                .contains(GreetingDelegate.class.getName().replace('.', '/'));
        assertThat(methods).contains("greet(Ljava/lang/String;)Ljava/lang/String;");
    }

    public interface GreetingDelegate {
        String greet(String name);
    }

    public static final class GreetingDelegateImplementation implements GreetingDelegate {
        public GreetingDelegateImplementation(Object target) {
        }

        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    private static final class DelegateTargetGenerator implements ClassGenerator {
        @Override
        public void generateClass(ClassVisitor visitor) {
            visitor.visit(V1_5, ACC_PUBLIC, TARGET_CLASS, null, "java/lang/Object", new String[0]);
            MethodVisitor constructor = visitor.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();
            visitor.visitEnd();
        }
    }

    private static final class MethodCollectingClassAdapter extends ClassAdapter {
        private final List<String> methods;

        MethodCollectingClassAdapter(List<String> methods) {
            super(new ClassWriter(0));
            this.methods = methods;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            methods.add(name + descriptor);
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }
}
