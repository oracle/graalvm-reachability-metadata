/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pulsar.shade.org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ValidatingObjectInputStreamTest {
    @Test
    void readsAcceptedObjectWithGenericCast() throws Exception {
        byte[] encoded = SerializationUtils.serialize(new ArrayList<>(List.of("alpha", "beta")));

        try (ValidatingObjectInputStream input = new ValidatingObjectInputStream(
                new ByteArrayInputStream(encoded)).accept(ArrayList.class)) {
            List<String> restored = input.readObjectCast();

            assertThat(restored).containsExactly("alpha", "beta");
        }
    }
}
