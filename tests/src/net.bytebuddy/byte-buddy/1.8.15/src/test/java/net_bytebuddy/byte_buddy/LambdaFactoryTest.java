/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.agent.builder.LambdaFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class LambdaFactoryTest {
    @Test
    void registersInvokesAndReleasesLambdaClassFileFactory() {
        ClassFileTransformer transformer = new NoOpClassFileTransformer();
        TestLambdaClassFileFactory factory = new TestLambdaClassFileFactory();
        boolean registered = false;
        try {
            boolean firstRegistration = LambdaFactory.register(transformer, factory);
            registered = true;
            assertThat(firstRegistration).isTrue();

            Object caller = new Object();
            Object invokedType = new Object();
            Object samMethodType = new Object();
            Object implMethod = new Object();
            Object instantiatedMethodType = new Object();
            List<Class<?>> markerInterfaces = Collections.<Class<?>>singletonList(Runnable.class);
            List<?> additionalBridges = Collections.singletonList("bridge");

            byte[] result = LambdaFactory.make(
                    caller,
                    "run",
                    invokedType,
                    samMethodType,
                    implMethod,
                    instantiatedMethodType,
                    true,
                    markerInterfaces,
                    additionalBridges);

            assertThat(result).containsExactly(TestLambdaClassFileFactory.classFile);
            assertThat(factory.caller).isSameAs(caller);
            assertThat(factory.invokedName).isEqualTo("run");
            assertThat(factory.invokedType).isSameAs(invokedType);
            assertThat(factory.samMethodType).isSameAs(samMethodType);
            assertThat(factory.implMethod).isSameAs(implMethod);
            assertThat(factory.instantiatedMethodType).isSameAs(instantiatedMethodType);
            assertThat(factory.serializable).isTrue();
            assertThat(factory.markerInterfaces).isSameAs(markerInterfaces);
            assertThat(factory.additionalBridges).isSameAs(additionalBridges);
            assertThat(factory.classFileTransformers).containsExactly(transformer);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            if (registered) {
                assertThat(LambdaFactory.release(transformer)).isTrue();
            }
        }
    }

    public static class TestLambdaClassFileFactory {
        static final byte classFile = 42;

        Object caller;
        String invokedName;
        Object invokedType;
        Object samMethodType;
        Object implMethod;
        Object instantiatedMethodType;
        boolean serializable;
        List<Class<?>> markerInterfaces;
        List<?> additionalBridges;
        Collection<ClassFileTransformer> classFileTransformers;

        public byte[] make(Object caller,
                           String invokedName,
                           Object invokedType,
                           Object samMethodType,
                           Object implMethod,
                           Object instantiatedMethodType,
                           boolean serializable,
                           List<Class<?>> markerInterfaces,
                           List<?> additionalBridges,
                           Collection<ClassFileTransformer> classFileTransformers) {
            this.caller = caller;
            this.invokedName = invokedName;
            this.invokedType = invokedType;
            this.samMethodType = samMethodType;
            this.implMethod = implMethod;
            this.instantiatedMethodType = instantiatedMethodType;
            this.serializable = serializable;
            this.markerInterfaces = markerInterfaces;
            this.additionalBridges = additionalBridges;
            this.classFileTransformers = classFileTransformers;
            return new byte[] {classFile};
        }
    }

    private static class NoOpClassFileTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader,
                                String className,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            return null;
        }
    }
}
