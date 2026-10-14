/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.HashMultiset;
import org.junit.jupiter.api.Test;

public class SerializationTest {
    @Test
    void restoresMultisetElementsAndCounts() throws Exception {
        HashMultiset<String> values = HashMultiset.create();
        values.add("one", 1);
        values.add("many", 4);

        HashMultiset<String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.count("one")).isOne();
        assertThat(restored.count("many")).isEqualTo(4);
    }
}
