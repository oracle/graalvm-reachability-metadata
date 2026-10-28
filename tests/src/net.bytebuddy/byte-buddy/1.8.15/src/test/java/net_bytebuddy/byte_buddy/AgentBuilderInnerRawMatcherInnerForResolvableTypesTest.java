/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AgentBuilderInnerRawMatcherInnerForResolvableTypesTest {
    @Test
    void matchesExistingResolvableTypeAndItsInverse() {
        AgentBuilder.RawMatcher.ForResolvableTypes matcher = AgentBuilder.RawMatcher.ForResolvableTypes.INSTANCE;
        ClassLoader loader = getClass().getClassLoader();
        JavaModule module = JavaModule.ofType(TestFixtures.Sample.class);
        assertTrue(matcher.matches(TestFixtures.sample(), loader, module, TestFixtures.Sample.class, null));
        assertFalse(matcher.inverted().matches(TestFixtures.sample(), loader, module, TestFixtures.Sample.class, null));
        assertTrue(matcher.matches(TestFixtures.sample(), loader, module, null, null));
    }
}
