/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.builder.LambdaFactory;

public class LambdaFactoryTest {
    @Test
    void registersInvokesAndReleasesLambdaClassFileFactory() {
        RecordingClassFileTransformer transformer = new RecordingClassFileTransformer();
        RecordingLambdaClassFileFactory factory = new RecordingLambdaClassFileFactory();

        LambdaFactory.CLASS_FILE_TRANSFORMERS.clear();
        boolean releaseRequired = false;
        try {
            assertThat(LambdaFactory.register(transformer, factory)).isTrue();
            releaseRequired = true;

            Object caller = new Object();
            Object invokedType = new Object();
            Object samMethodType = new Object();
            Object implementationMethod = new Object();
            Object instantiatedMethodType = new Object();
            List<Class<?>> markerInterfaces = List.of(Runnable.class);
            List<String> additionalBridges = List.of("bridge");

            byte[] bytecode = LambdaFactory.make(
                caller,
                "run",
                invokedType,
                samMethodType,
                implementationMethod,
                instantiatedMethodType,
                true,
                markerInterfaces,
                additionalBridges);

            assertThat(bytecode).containsExactly(1, 2, 3);
            assertThat(factory.caller).isSameAs(caller);
            assertThat(factory.invokedName).isEqualTo("run");
            assertThat(factory.invokedType).isSameAs(invokedType);
            assertThat(factory.samMethodType).isSameAs(samMethodType);
            assertThat(factory.implementationMethod).isSameAs(implementationMethod);
            assertThat(factory.instantiatedMethodType).isSameAs(instantiatedMethodType);
            assertThat(factory.serializable).isTrue();
            assertThat(factory.markerInterfaces).isSameAs(markerInterfaces);
            assertThat(factory.additionalBridges).isSameAs(additionalBridges);
            assertThat(factory.classFileTransformers).containsExactly(transformer);

            assertThat(LambdaFactory.release(transformer)).isTrue();
            releaseRequired = false;
        } finally {
            if (releaseRequired) {
                LambdaFactory.release(transformer);
            }
            LambdaFactory.CLASS_FILE_TRANSFORMERS.clear();
        }
    }

    private static final class RecordingClassFileTransformer implements ClassFileTransformer {
    }

    public static final class RecordingLambdaClassFileFactory {
        private Object caller;
        private String invokedName;
        private Object invokedType;
        private Object samMethodType;
        private Object implementationMethod;
        private Object instantiatedMethodType;
        private boolean serializable;
        private List<Class<?>> markerInterfaces;
        private List<?> additionalBridges;
        private Collection<ClassFileTransformer> classFileTransformers;

        public byte[] make(
            Object caller,
            String invokedName,
            Object invokedType,
            Object samMethodType,
            Object implementationMethod,
            Object instantiatedMethodType,
            boolean serializable,
            List<Class<?>> markerInterfaces,
            List<?> additionalBridges,
            Collection<ClassFileTransformer> classFileTransformers) {
            this.caller = caller;
            this.invokedName = invokedName;
            this.invokedType = invokedType;
            this.samMethodType = samMethodType;
            this.implementationMethod = implementationMethod;
            this.instantiatedMethodType = instantiatedMethodType;
            this.serializable = serializable;
            this.markerInterfaces = markerInterfaces;
            this.additionalBridges = additionalBridges;
            this.classFileTransformers = classFileTransformers;
            return new byte[] {1, 2, 3};
        }
    }
}
