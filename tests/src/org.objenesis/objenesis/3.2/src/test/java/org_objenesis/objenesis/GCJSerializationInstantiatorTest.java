/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.Serializable;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.commons.ClassRemapper;
import net.bytebuddy.jar.asm.commons.Remapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.gcj.GCJSerializationInstantiator;

public class GCJSerializationInstantiatorTest {

    private static final String TARGET_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJSerializationInstantiator";
    private static final String TARGET_BASE_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJInstantiatorBase";
    private static final String TARGET_DUMMY_STREAM_CLASS_NAME =
        "org/objenesis/instantiator/gcj/GCJInstantiatorBase$DummyStream";
    private static final String SUPPORT_CLASS_NAME =
        GCJInstantiatorTest.GCJObjectInputStreamSupport.class.getName().replace('.', '/');
    private static final Set<String> TARGET_CLASS_NAMES = Set.of(
        TARGET_CLASS_NAME,
        TARGET_BASE_CLASS_NAME,
        TARGET_DUMMY_STREAM_CLASS_NAME
    );
    private static final AtomicBoolean transformerInstalled = new AtomicBoolean();

    @Test
    void createsSerializableInstancesUsingGcjSerializationConstructionRules() {
        Assumptions.assumeFalse(isNativeImageRuntime());
        installObjectInputStreamRedirect();
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        GCJSerializationInstantiator<SerializableChild> instantiator =
            new GCJSerializationInstantiator<>(SerializableChild.class);
        SerializableChild instance = instantiator.newInstance();

        Assertions.assertThat(instance).isNotNull();
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
        Assertions.assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        Assertions.assertThat(instance.childState).isNull();
    }

    private static void installObjectInputStreamRedirect() {
        if (!transformerInstalled.compareAndSet(false, true)) {
            return;
        }

        Instrumentation instrumentation = ByteBuddyAgent.install();
        ClassFileTransformer transformer = new GCJSerializationInstantiatorTransformer();
        instrumentation.addTransformer(transformer, true);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        transient String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }

    static final class GCJSerializationInstantiatorTransformer implements ClassFileTransformer {

        @Override
        public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer
        ) {
            if (!TARGET_CLASS_NAMES.contains(className)) {
                return null;
            }

            ClassReader classReader = new ClassReader(classfileBuffer);
            ClassWriter classWriter = new ClassWriter(classReader, 0);
            ClassRemapper classRemapper = new ClassRemapper(classWriter, new Remapper() {
                @Override
                public String map(String internalName) {
                    if ("java/io/ObjectInputStream".equals(internalName)) {
                        return SUPPORT_CLASS_NAME;
                    }
                    return internalName;
                }
            });
            classReader.accept(classRemapper, 0);
            return classWriter.toByteArray();
        }
    }
}
