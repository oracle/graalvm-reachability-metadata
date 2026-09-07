/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.io.serialization.ValidatingObjectInputStream;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidatingObjectInputStreamTest {
    @Test
    void resolvesAcceptedSerializedClasses() throws Exception {
        byte[] encoded = SerializationUtils.serialize(new ArrayList<>(List.of("accepted")));

        try (
            ValidatingObjectInputStream input = new ValidatingObjectInputStream(new ByteArrayInputStream(encoded))
                .accept(ArrayList.class, String.class)
        ) {
            assertThat(input.readObject()).isEqualTo(List.of("accepted"));
        }
    }
}
