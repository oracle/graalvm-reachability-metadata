/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.ClassWriter;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.cglib.transform.impl.AddDelegateTransformer;

public class AddDelegateTransformerTest {

    @Test
    void addsDelegateInterfaceAndMethodsToGeneratedClass() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[] {GreetingOperations.class},
                GreetingDelegate.class
        );

        transformer.setTarget(writer);
        transformer.begin_class(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "org_springframework.spring_core.GeneratedDelegateTarget",
                Type.getType(Object.class),
                new Type[0],
                "GeneratedDelegateTarget.java"
        );
        transformer.end_class();

        GeneratedClassInfo classInfo = readGeneratedClassInfo(writer.toByteArray());

        assertThat(classInfo.interfaces).contains(Type.getInternalName(GreetingOperations.class));
        assertThat(classInfo.methods).contains("greet(Ljava/lang/String;)Ljava/lang/String;");
    }

    private static GeneratedClassInfo readGeneratedClassInfo(byte[] bytecode) {
        GeneratedClassInfo classInfo = new GeneratedClassInfo();
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName,
                    String[] interfaces) {

                classInfo.interfaces.addAll(Arrays.asList(interfaces));
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {

                classInfo.methods.add(name + descriptor);
                return null;
            }
        }, 0);
        return classInfo;
    }

    public interface GreetingOperations {

        String greet(String name);
    }

    public static class GreetingDelegate {

        private final Object target;

        public GreetingDelegate(Object target) {
            this.target = target;
        }

        public String greet(String name) {
            return "hello " + name + " from " + target.getClass().getSimpleName();
        }
    }

    private static final class GeneratedClassInfo {

        private final List<String> interfaces = new ArrayList<>();

        private final List<String> methods = new ArrayList<>();
    }
}
