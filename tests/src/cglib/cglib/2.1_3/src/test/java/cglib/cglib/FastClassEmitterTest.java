/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import java.util.ArrayList;
import java.util.List;

import cglib.cglib.FastClassTest.Counter;
import cglib.cglib.FastClassTest.CounterAccessor;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.transform.NullClassVisitor;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.CodeAdapter;
import org.objectweb.asm.CodeVisitor;
import org.objectweb.asm.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class FastClassEmitterTest {
    @Test
    void emitsDispatchForEveryConstructorAndPublicMethod() throws Exception {
        FastClass.Generator generator = new FastClass.Generator();
        generator.setType(Counter.class);
        generator.setClassLoader(Counter.class.getClassLoader());
        generator.setAttemptLoad(true);
        generator.setNamingPolicy((prefix, source, key, names) -> CounterAccessor.class.getName());

        // Initialize the generator using an existing accessor; inspect bytes without defining a class.
        FastClass accessor = generator.create();
        assertThat(((Counter) accessor.newInstance(new Class<?>[]{int.class}, new Object[]{9})).value())
                .isEqualTo(9);
        byte[] bytecode = generator.getStrategy().generate(generator);
        List<String> declarations = new ArrayList<>();
        List<String> constructors = new ArrayList<>();
        List<String> methods = new ArrayList<>();

        new ClassReader(bytecode).accept(new NullClassVisitor() {
            @Override
            public void visit(int version, int access, String name, String superName,
                    String[] interfaces, String sourceFile) {
                assertThat(name).isEqualTo(Type.getInternalName(CounterAccessor.class));
                assertThat(superName).isEqualTo(Type.getInternalName(FastClass.class));
            }

            @Override
            public CodeVisitor visitMethod(int access, String name, String descriptor,
                    String[] exceptions, Attribute attributes) {
                declarations.add(name + descriptor);
                return new CodeAdapter(super.visitMethod(access, name, descriptor, exceptions, attributes)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String desc) {
                        if (owner.equals(Type.getInternalName(Counter.class))) {
                            if (name.equals("newInstance") && methodName.equals("<init>")) {
                                constructors.add(desc);
                            } else if (name.equals("invoke")) {
                                methods.add(methodName + desc);
                            }
                        }
                        super.visitMethodInsn(opcode, owner, methodName, desc);
                    }
                };
            }
        }, false);

        assertThat(constructors).containsExactlyInAnyOrder("()V", "(I)V");
        assertThat(methods).contains("add(I)I", "value()I");
        assertThat(declarations).contains(
                "getIndex([Ljava/lang/Class;)I",
                "getIndex(Ljava/lang/String;[Ljava/lang/Class;)I",
                "newInstance(I[Ljava/lang/Object;)Ljava/lang/Object;",
                "invoke(ILjava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    }
}
