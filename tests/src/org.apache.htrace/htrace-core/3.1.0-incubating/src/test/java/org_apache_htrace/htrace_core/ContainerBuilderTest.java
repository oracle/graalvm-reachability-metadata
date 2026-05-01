/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.databind.util.ContainerBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ContainerBuilderTest {
    @Test
    void finishArrayCreatesTypedArrayFromBufferedEntries() {
        ContainerBuilder builder = new ContainerBuilder(4);
        int previousStart = builder.start();
        builder.add("first");
        builder.add("second");

        Object[] result = builder.finishArray(previousStart, String.class);

        assertThat(result).containsExactly("first", "second");
        assertThat(result.getClass()).isSameAs(String[].class);
        assertThat(builder.canReuse()).isTrue();
    }
}
