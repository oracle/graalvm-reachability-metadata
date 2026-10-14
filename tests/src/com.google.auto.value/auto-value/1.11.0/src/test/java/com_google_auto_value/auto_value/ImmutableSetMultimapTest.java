/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.ImmutableSetMultimap;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void restoresImmutableEntriesAndComparatorState() throws Exception {
        ImmutableSetMultimap<String, Integer> values = ImmutableSetMultimap.<String, Integer>builder()
                .put("odd", 3)
                .put("odd", 1)
                .put("even", 2)
                .orderValuesBy(Comparator.naturalOrder())
                .build();

        ImmutableSetMultimap<String, Integer> restored = SerializationSupport.roundTrip(values);

        assertThat(restored).isEqualTo(values);
        assertThat(restored.get("odd")).containsExactly(1, 3);
    }
}
