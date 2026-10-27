/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu_inject.cglib;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.core.CodeEmitter;
import net.sf.cglib.core.Signature;
import net.sf.cglib.transform.impl.AddDelegateTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class AddDelegateTransformerTest {

    @Test
    void generatesInterfaceMethodsDelegatingToImplementation() {
        AddDelegateTransformer transformer = new AddDelegateTransformer(
                new Class<?>[]{GreetingDelegate.class}, GreetingDelegateImpl.class);
        ClassWriter writer = new ClassWriter(0);
        transformer.setTarget(writer);

        transformer.begin_class(
                Opcodes.V1_2,
                Opcodes.ACC_PUBLIC,
                "org_sonatype_sisu_inject/cglib/GeneratedDelegateTarget",
                Type.getType(Object.class),
                new Type[0],
                null);
        CodeEmitter constructor = transformer.begin_method(
                Opcodes.ACC_PUBLIC,
                new Signature("<init>", "()V"),
                new Type[0]);
        constructor.load_this();
        constructor.super_invoke_constructor();
        constructor.return_value();
        constructor.end_method();
        transformer.end_class();

        ClassReader generatedClass = new ClassReader(writer.toByteArray());

        assertThat(generatedClass.getInterfaces()).contains(Type.getInternalName(GreetingDelegate.class));
    }

    public interface GreetingDelegate {
        String greet(String name);
    }

    public static class GreetingDelegateImpl implements GreetingDelegate {
        public GreetingDelegateImpl(Object target) {
        }

        @Override
        public String greet(String name) {
            return "delegated: " + name;
        }
    }
}
