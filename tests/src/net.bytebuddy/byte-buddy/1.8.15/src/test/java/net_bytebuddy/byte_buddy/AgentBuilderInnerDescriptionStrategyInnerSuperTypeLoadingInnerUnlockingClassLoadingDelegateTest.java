/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentBuilderInnerDescriptionStrategyInnerSuperTypeLoadingInnerUnlockingClassLoadingDelegateTest {
    @Test
    void resolvesExistingSuperclassAndRestoresCircularityLock() {
        AgentBuilder.CircularityLock lock = new AgentBuilder.CircularityLock.Default();
        assertTrue(lock.acquire());
        try {
            TypeDescription type = AgentBuilder.DescriptionStrategy.Default.POOL_ONLY.withSuperTypeLoading()
                    .apply(getClass().getName(), null, TestFixtures.superTypePool(getClass().getName()), lock,
                            getClass().getClassLoader(), JavaModule.ofType(getClass()));
            assertTrue(type.getSuperClass().asErasure().represents(Object.class));
            assertFalse(lock.acquire());
        } finally {
            lock.release();
        }
        assertTrue(lock.acquire());
        lock.release();
    }
}
