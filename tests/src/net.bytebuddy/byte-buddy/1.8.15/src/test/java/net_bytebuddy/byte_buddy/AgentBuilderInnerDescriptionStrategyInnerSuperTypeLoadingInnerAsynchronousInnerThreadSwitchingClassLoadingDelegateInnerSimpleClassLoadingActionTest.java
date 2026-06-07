/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ByteArrayClassLoader;
import net.bytebuddy.pool.TypePool;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerAsynchronousInnerThreadSwitchingClassLoadingDelegateInnerSimpleClassLoadingActionTest {
    private static final String SUPER_TYPE_NAME = GeneratedTypeFixtures.SIMPLE_ACTION_SUPER_TYPE;
    private static final String SUB_TYPE_NAME = GeneratedTypeFixtures.SIMPLE_ACTION_SUB_TYPE;

    @Test
    void loadsSuperTypeAsynchronouslyWhenClassLoaderMonitorIsNotHeld() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Map<String, byte[]> typeDefinitions = GeneratedTypeFixtures.agentBuilderTypeDefinitions(
                    SUPER_TYPE_NAME,
                    SUB_TYPE_NAME);
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

            TypeDescription loadedSuperType = typeDescription.getSuperClass().asErasure();

            assertThat(loadedSuperType.getName()).isEqualTo(SUPER_TYPE_NAME);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            executorService.shutdownNow();
        }
    }
}
