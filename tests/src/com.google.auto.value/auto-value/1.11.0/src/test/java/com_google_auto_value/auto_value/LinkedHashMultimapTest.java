/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.LinkedHashMultimap;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void preservesKeyAndValueOrderDuringSerialization() throws Exception {
        LinkedHashMultimap<String, Integer> values = LinkedHashMultimap.create();
        values.put("first", 1);
        values.put("first", 2);
        values.put("second", 3);

        LinkedHashMultimap<String, Integer> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.entries()).containsExactlyElementsOf(values.entries());
    }
}
