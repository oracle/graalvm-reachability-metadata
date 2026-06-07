/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.NexusAccessor;
import net.bytebuddy.dynamic.TypeResolutionStrategy;
import net.bytebuddy.implementation.LoadedTypeInitializer;

import java.lang.reflect.Constructor;

final class NexusInitializationSupport {
    static final String GENERATED_TYPE_NAME = "net_bytebuddy.byte_buddy.generated.NexusInitializationSample";

    private static final int FIXED_IDENTIFICATION = 42;
    private static final Constructor<?> ACTIVE_RESOLVED_CONSTRUCTOR = activeResolvedConstructor();

    private NexusInitializationSupport() {
    }

    static DynamicType.Unloaded<?> makeGeneratedType(LoadedTypeInitializer initializer) {
        return new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(GENERATED_TYPE_NAME)
                .initializer(initializer)
                .make(new FixedActiveTypeResolutionStrategy());
    }

    static byte[] generatePredefinedClassBytes() {
        return makeGeneratedType(NoOpLoadedTypeInitializer.INSTANCE).getBytes();
    }

    private static Constructor<?> activeResolvedConstructor() {
        try {
            Constructor<?> constructor = Class
                    .forName("net.bytebuddy.dynamic.TypeResolutionStrategy$Active$Resolved")
                    .getDeclaredConstructor(NexusAccessor.class, int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class FixedActiveTypeResolutionStrategy implements TypeResolutionStrategy {
        @Override
        public Resolved resolve() {
            try {
                return (Resolved) ACTIVE_RESOLVED_CONSTRUCTOR.newInstance(new NexusAccessor(), FIXED_IDENTIFICATION);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create fixed Nexus type resolution strategy", exception);
            }
        }
    }

    private enum NoOpLoadedTypeInitializer implements LoadedTypeInitializer {
        INSTANCE;

        @Override
        public void onLoad(Class<?> type) {
            /* no action required */
        }

        @Override
        public boolean isAlive() {
            return true;
        }
    }
}
