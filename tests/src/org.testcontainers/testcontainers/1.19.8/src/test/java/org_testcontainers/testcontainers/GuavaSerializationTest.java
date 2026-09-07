/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.LinkedHashMultimap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class GuavaSerializationTest {
    @Test
    void populatesMapMultimapAndMultisetStateDuringRoundTrip() {
        LinkedHashMultimap<String, String> values = LinkedHashMultimap.create();
        values.put("key", "value");

        assertThat(SerializationUtils.roundtrip(values)).isEqualTo(values);
    }
}
