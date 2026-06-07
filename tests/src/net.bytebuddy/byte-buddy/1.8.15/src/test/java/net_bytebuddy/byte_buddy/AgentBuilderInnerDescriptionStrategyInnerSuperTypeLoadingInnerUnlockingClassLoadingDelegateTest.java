/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.pool.TypePool;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerUnlockingClassLoadingDelegateTest {
    private static final String SUPER_TYPE_NAME = "net_bytebuddy.byte_buddy.generated.agentbuilder.UnlockingDelegateSuperType";
    private static final String SUB_TYPE_NAME = "net_bytebuddy.byte_buddy.generated.agentbuilder.UnlockingDelegateSubType";

    @Test
    void loadsSuperTypeWhileTemporarilyReleasingCircularityLock() {
        try {
            Map<String, byte[]> typeDefinitions = typeDefinitions();
            ClassLoader classLoader = new ByteArrayClassLoader(
                    getClass().getClassLoader(),
                    typeDefinitions,
                    ByteArrayClassLoader.PersistenceHandler.MANIFEST);
            TypePool typePool = TypePool.Default.of(classLoader);
            RecordingCircularityLock circularityLock = new RecordingCircularityLock();
            AgentBuilder.DescriptionStrategy descriptionStrategy = AgentBuilder.DescriptionStrategy.Default.POOL_ONLY
                    .withSuperTypeLoading();
            TypeDescription typeDescription = descriptionStrategy.apply(
                    SUB_TYPE_NAME,
                    null,
                    typePool,
                    circularityLock,
                    classLoader,
                    null);

            TypeDescription loadedSuperType = typeDescription.getSuperClass().asErasure();

            assertThat(loadedSuperType.getName()).isEqualTo(SUPER_TYPE_NAME);
            assertThat(circularityLock.releaseCount).isEqualTo(1);
            assertThat(circularityLock.acquireCount).isEqualTo(1);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static class RecordingCircularityLock implements AgentBuilder.CircularityLock {
        private int acquireCount;
        private int releaseCount;

        @Override
        public boolean acquire() {
            acquireCount++;
            return true;
        }

        @Override
        public void release() {
            releaseCount++;
        }
    }

    private static Map<String, byte[]> typeDefinitions() {
        DynamicType.Unloaded<?> superType = new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(Object.class)
                .name(SUPER_TYPE_NAME)
                .make();
        DynamicType.Unloaded<?> subType = new ByteBuddy(ClassFileVersion.JAVA_V8)
                .subclass(superType.getTypeDescription())
                .name(SUB_TYPE_NAME)
                .make();
        Map<String, byte[]> typeDefinitions = new LinkedHashMap<String, byte[]>();
        typeDefinitions.put(superType.getTypeDescription().getName(), superType.getBytes());
        typeDefinitions.put(subType.getTypeDescription().getName(), subType.getBytes());
        return typeDefinitions;
    }
}
