/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerAsynchronousInnerThreadSwitchingClassLoadingDelegateInnerSimpleClassLoadingActionTest {
    @Test
    @Timeout(60)
    void resolvesExistingSuperclassOnWorkerThread() {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            TypeDescription type = AgentBuilder.DescriptionStrategy.Default.POOL_ONLY.withSuperTypeLoading(executor)
                    .apply(JavaModule.class.getName(), null, TestFixtures.libraryPool(), AgentBuilder.CircularityLock.Inactive.INSTANCE,
                            getClass().getClassLoader(), JavaModule.ofType(JavaModule.class));
            assertTrue(type.getSuperClass().asErasure().represents(Object.class));
        }
    }
}
