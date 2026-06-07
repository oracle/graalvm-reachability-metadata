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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerAsynchronousInnerThreadSwitchingClassLoadingDelegateInnerNotifyingClassLoadingActionTest {
    private static final String GENERATED_PACKAGE = "net_bytebuddy.byte_buddy.generated.agentbuilder";

    private static final String SUPER_TYPE_NAME = GENERATED_PACKAGE + ".LockHeldSuperType";

    private static final String SUB_TYPE_NAME = GENERATED_PACKAGE + ".LockHeldSubType";

    @Test
    void loadsSuperTypeAsynchronouslyWhenCurrentThreadOwnsClassLoaderMonitor() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
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
            ClassLoader classLoader = new ByteArrayClassLoader(
                    getClass().getClassLoader(),
                    typeDefinitions,
                    ByteArrayClassLoader.PersistenceHandler.MANIFEST);
            TypePool typePool = TypePool.Default.of(classLoader);
            AgentBuilder.DescriptionStrategy descriptionStrategy = AgentBuilder.DescriptionStrategy.Default.POOL_ONLY
                    .withSuperTypeLoading(executorService);
            TypeDescription typeDescription = descriptionStrategy.apply(
                    SUB_TYPE_NAME,
                    null,
                    typePool,
                    AgentBuilder.CircularityLock.Inactive.INSTANCE,
                    classLoader,
                    null);

            synchronized (classLoader) {
                TypeDescription loadedSuperType = typeDescription.getSuperClass().asErasure();

                assertThat(loadedSuperType.getName()).isEqualTo(SUPER_TYPE_NAME);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
