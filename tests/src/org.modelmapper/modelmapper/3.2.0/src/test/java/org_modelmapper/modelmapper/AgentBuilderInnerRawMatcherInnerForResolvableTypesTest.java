/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.bytebuddy.agent.builder.AgentBuilder.RawMatcher.ForResolvableTypes;

public class AgentBuilderInnerRawMatcherInnerForResolvableTypesTest {
    @Test
    void matchesLoadedTypeResolvableByTheSuppliedClassLoader() {
        boolean matches = ForResolvableTypes.INSTANCE.matches(
            null,
            String.class.getClassLoader(),
            null,
            String.class,
            null);

        assertThat(matches).isTrue();
    }
}
