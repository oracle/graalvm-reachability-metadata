/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_api;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.TraceState;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ReadOnlyArrayMapSetViewTest {

    @Test
    void typedToArrayCreatesTypedArraysForAllReadOnlyViews() {
        Map<String, String> entries = TraceState.builder()
                .put("alpha", "one")
                .put("beta", "two")
                .build()
                .asMap();

        String[] keys = entries.keySet().toArray(new String[0]);
        String[] values = entries.values().toArray(new String[0]);
        @SuppressWarnings("unchecked")
        Map.Entry<String, String>[] pairs = entries.entrySet().toArray(new Map.Entry[0]);

        assertThat(keys).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values).containsExactlyInAnyOrder("one", "two");
        String[] pairStrings = Arrays.stream(pairs)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);

        assertThat(pairStrings).containsExactlyInAnyOrder("alpha=one", "beta=two");
    }
}
