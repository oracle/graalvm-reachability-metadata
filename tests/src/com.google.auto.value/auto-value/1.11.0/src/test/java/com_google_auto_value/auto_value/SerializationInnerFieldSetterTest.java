/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import static org.assertj.core.api.Assertions.assertThat;

import autovalue.shaded.com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

public class SerializationInnerFieldSetterTest {
    @Test
    void restoresPrivateImmutableMultimapState() throws Exception {
        ImmutableSetMultimap<String, String> values =
                ImmutableSetMultimap.of("language", "java", "runtime", "native");

        ImmutableSetMultimap<String, String> restored = SerializationSupport.roundTrip(values);

        assertThat(restored.asMap()).containsOnlyKeys("language", "runtime");
        assertThat(restored.size()).isEqualTo(2);
    }
}
