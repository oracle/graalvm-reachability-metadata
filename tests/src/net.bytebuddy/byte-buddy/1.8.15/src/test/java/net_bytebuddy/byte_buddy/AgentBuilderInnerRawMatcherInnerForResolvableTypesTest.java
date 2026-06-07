/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentBuilderInnerRawMatcherInnerForResolvableTypesTest {
    @Test
    void matchesLoadedTypeResolvableByItsClassLoader() {
        Class<?> loadedType = AgentBuilder.class;
        TypeDescription typeDescription = TypeDescription.ForLoadedType.of(loadedType);

        boolean matches = AgentBuilder.RawMatcher.ForResolvableTypes.INSTANCE.matches(
                typeDescription,
                loadedType.getClassLoader(),
                null,
                loadedType,
                loadedType.getProtectionDomain());

        assertThat(matches).isTrue();
    }
}
