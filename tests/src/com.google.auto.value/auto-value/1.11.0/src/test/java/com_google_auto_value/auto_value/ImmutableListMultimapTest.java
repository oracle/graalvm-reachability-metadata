/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {
    @Test
    void restoresImmutableListsDuringSerialization() throws Exception {
        ImmutableListMultimap<String, Integer> values =
                ImmutableListMultimap.of("numbers", 1, "numbers", 2, "other", 3);

        ImmutableListMultimap<String, Integer> restored = SerializationSupport.roundTrip(values);

        assertThat(restored).isEqualTo(values);
        assertThat(restored.get("numbers")).containsExactly(1, 2);
    }
}
