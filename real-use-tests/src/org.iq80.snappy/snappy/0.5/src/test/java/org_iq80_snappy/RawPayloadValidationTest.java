/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_iq80_snappy;

import org.iq80.snappy.CorruptionException;
import org.iq80.snappy.Snappy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises Snappy's raw-payload validation subsystem for an application cache.
public class RawPayloadValidationTest {
    @Test
    @Timeout(60)
    public void rejectsMalformedCachedPayloadBeforeRestoringIt() {
        byte[] malformedPayload = {(byte) 0x80};

        assertThrows(CorruptionException.class,
                () -> Snappy.getUncompressedLength(malformedPayload, 0));
        assertThrows(CorruptionException.class,
                () -> Snappy.uncompress(malformedPayload, 0, malformedPayload.length));
    }
}
