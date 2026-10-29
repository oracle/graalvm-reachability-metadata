/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.Serializable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.bytebuddy.agent.builder.LambdaFactory;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LambdaFactoryTest {
    @Test
    void registrationTracksTheFirstAndLastTransformer() {
        ClassFileTransformer first = new UnusedTransformer();
        ClassFileTransformer second = new UnusedTransformer();
        PrecompiledFactory factory = new PrecompiledFactory();
        try {
            assertTrue(LambdaFactory.register(first, factory));
            assertFalse(LambdaFactory.register(second, factory));
            assertFalse(LambdaFactory.release(first));
            assertFalse(LambdaFactory.release(first));
            assertTrue(LambdaFactory.release(second));
            assertFalse(LambdaFactory.release(second));
            assertTrue(LambdaFactory.register(first, factory));
            assertTrue(LambdaFactory.release(first));
        } finally {
            LambdaFactory.release(first);
            LambdaFactory.release(second);
        }
    }

    @Test
    void dispatchesClassFileRequestsToTheRegisteredFactory() throws Exception {
        ClassFileTransformer transformer = new UnusedTransformer();
        PrecompiledFactory factory = new PrecompiledFactory();
        MethodHandles.Lookup caller = MethodHandles.lookup();
        MethodType invokedType = MethodType.methodType(Supplier.class);
        MethodType samType = MethodType.methodType(Object.class);
        MethodType specializedType = MethodType.methodType(String.class);
        MethodHandle implementation = caller.findStatic(LambdaFactoryTest.class, "supply", specializedType);
        List<Class<?>> markers = List.of(Cloneable.class);
        List<MethodType> bridges = List.of(MethodType.methodType(CharSequence.class));
        try {
            assertTrue(LambdaFactory.register(transformer, factory));

            byte[] bytes = LambdaFactory.make(caller, "get", invokedType, samType,
                    implementation, specializedType, true, markers, bridges);

            assertSame(factory.bytes, bytes);
            assertEquals(List.of(caller, "get", invokedType, samType, implementation,
                    specializedType, true, markers, bridges), factory.arguments);
            assertEquals(Set.of(transformer), factory.transformers);
            assertEquals("registered", new PrecompiledSupplier().get());
            assertTrue(LambdaFactory.release(transformer));
        } finally {
            LambdaFactory.release(transformer);
        }
    }

    public static String supply() {
        return "registered";
    }

    public static class PrecompiledSupplier implements Supplier<String>, Serializable, Cloneable {
        private static final long serialVersionUID = 1L;

        @Override
        public String get() {
            return supply();
        }
    }

    public static class PrecompiledFactory {
        private final byte[] bytes = ClassFileLocator.ForClassLoader.read(PrecompiledSupplier.class).resolve();
        private List<Object> arguments;
        private Set<ClassFileTransformer> transformers;

        // The dispatcher consumes existing class bytes; it never defines a lambda class.
        public byte[] make(Object caller, String name, Object invokedType, Object samType,
                           Object implementation, Object specializedType, boolean serializable,
                           List<Class<?>> markers, List<?> bridges,
                           Collection<ClassFileTransformer> classFileTransformers) {
            arguments = List.of(caller, name, invokedType, samType, implementation,
                    specializedType, serializable, markers, bridges);
            transformers = Set.copyOf(classFileTransformers);
            return bytes;
        }
    }

    private static class UnusedTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                ProtectionDomain protectionDomain, byte[] bytes) {
            throw new AssertionError("Registry operations must not transform classes");
        }
    }

    @Test
    void releasingAnUnregisteredTransformerDoesNotRequestMetafactoryReset() {
        ClassFileTransformer transformer = new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                                    ProtectionDomain protectionDomain, byte[] bytes) {
                throw new AssertionError("Releasing a transformer must not execute it");
            }
        };
        assertFalse(LambdaFactory.release(transformer));
        assertFalse(LambdaFactory.release(transformer));
    }
}
