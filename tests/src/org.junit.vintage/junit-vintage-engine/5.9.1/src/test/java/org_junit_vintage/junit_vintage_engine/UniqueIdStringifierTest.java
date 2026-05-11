/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_vintage.junit_vintage_engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.vintage.engine.support.UniqueIdStringifier;

public class UniqueIdStringifierTest {
    @Test
    void serializesNonStringAndNonNumberIdentifiers() {
        UniqueIdStringifier stringifier = new UniqueIdStringifier();
        UUID uniqueId = new UUID(0x1234567890ABCDEFL, 0x0FEDCBA987654321L);

        String encoded = stringifier.apply(uniqueId);

        byte[] serialized = Base64.getDecoder().decode(encoded);
        assertThat(serialized).startsWith((byte) 0xAC, (byte) 0xED, (byte) 0x00, (byte) 0x05);
        assertThat(encoded).isNotEqualTo(uniqueId.toString());
    }
}
