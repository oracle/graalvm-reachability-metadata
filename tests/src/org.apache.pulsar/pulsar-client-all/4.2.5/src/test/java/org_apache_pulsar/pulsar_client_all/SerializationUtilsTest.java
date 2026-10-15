/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class SerializationUtilsTest {
    @Test
    void clonesAndRoundTripsSerializableValues() {
        ArrayList<String> original = new ArrayList<>(List.of("ledger", "cursor"));

        ArrayList<String> clone = SerializationUtils.clone(original);
        byte[] encoded = SerializationUtils.serialize(original);
        ArrayList<String> decoded = SerializationUtils.deserialize(encoded);

        assertThat(clone).containsExactlyElementsOf(original).isNotSameAs(original);
        assertThat(decoded).containsExactlyElementsOf(original).isNotSameAs(original);
    }
}
