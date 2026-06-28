/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_guice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.inject.internal.asm.ClassWriter;
import com.google.inject.internal.cglib.core.ClassGenerator;
import com.google.inject.internal.cglib.core.GeneratorStrategy;
import com.google.inject.internal.cglib.proxy.Callback;
import com.google.inject.internal.cglib.proxy.Enhancer;
import com.google.inject.internal.cglib.proxy.NoOp;
import org.junit.jupiter.api.Test;

public class EnhancerTest {
    @Test
    void emitsSubclassBytecodeWithConfiguredGeneratorStrategy() {
        Enhancer enhancer = new Enhancer();
        enhancer.setUseCache(false);
        enhancer.setSuperclass(EnhancerTarget.class);
        enhancer.setCallbackType(NoOp.class);
        enhancer.setStrategy(new CapturingGeneratorStrategy());

        GeneratedBytecode generated = assertThrows(GeneratedBytecode.class, enhancer::createClass);

        assertThat(generated.bytecode()).isNotEmpty();
    }

    @Test
    void registersThreadAndStaticCallbacksOnEnhancedClassShape() {
        Callback[] callbacks = new Callback[] {NoOp.INSTANCE};

        assertThat(Enhancer.isEnhanced(EnhancedClassShape.class)).isTrue();

        Enhancer.registerCallbacks(EnhancedClassShape.class, callbacks);
        assertThat(EnhancedClassShape.threadCallbacks()).isSameAs(callbacks);

        Enhancer.registerStaticCallbacks(EnhancedClassShape.class, callbacks);
        assertThat(EnhancedClassShape.staticCallbacks()).isSameAs(callbacks);
    }

    public static class EnhancerTarget {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class EnhancedClassShape {
        private static Callback[] threadCallbacks;
        private static Callback[] staticCallbacks;

        public static void CGLIB$SET_THREAD_CALLBACKS(Callback[] callbacks) {
            threadCallbacks = callbacks;
        }

        public static void CGLIB$SET_STATIC_CALLBACKS(Callback[] callbacks) {
            staticCallbacks = callbacks;
        }

        static Callback[] threadCallbacks() {
            return threadCallbacks;
        }

        static Callback[] staticCallbacks() {
            return staticCallbacks;
        }
    }

    private static class CapturingGeneratorStrategy implements GeneratorStrategy {
        @Override
        public byte[] generate(ClassGenerator generator) throws Exception {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            generator.generateClass(classWriter);
            throw new GeneratedBytecode(classWriter.toByteArray());
        }
    }

    private static class GeneratedBytecode extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final byte[] bytecode;

        GeneratedBytecode(byte[] bytecode) {
            this.bytecode = bytecode;
        }

        byte[] bytecode() {
            return bytecode;
        }
    }
}
