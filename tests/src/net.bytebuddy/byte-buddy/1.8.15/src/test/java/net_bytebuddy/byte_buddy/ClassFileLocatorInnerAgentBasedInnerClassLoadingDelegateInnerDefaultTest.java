/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.dynamic.ClassFileLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClassFileLocatorInnerAgentBasedInnerClassLoadingDelegateInnerDefaultTest {
    @Test
    void resolvesAlreadyAvailableApplicationClass() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        ClassFileLocator.AgentBased.ClassLoadingDelegate delegate =
                ClassFileLocator.AgentBased.ClassLoadingDelegate.Default.of(loader);
        assertEquals(TestFixtures.Sample.class, delegate.locate(TestFixtures.Sample.class.getName()));
        assertEquals(loader, delegate.getClassLoader());
    }
}
