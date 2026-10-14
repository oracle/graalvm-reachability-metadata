/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.LinkedListMultimap;
import org.junit.jupiter.api.Test;

public class LinkedListMultimapTest {
    @Test
    void preservesDuplicateValuesDuringSerialization() throws Exception {
        LinkedListMultimap<String, String> values = LinkedListMultimap.create();
        values.put("letters", "a");
        values.put("letters", "a");
        values.put("letters", "b");

        LinkedListMultimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.get("letters")).containsExactly("a", "a", "b");
    }
}
