/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.util.JournaledAlgorithm;
import org.bouncycastle.crypto.util.JournalingSecureRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JournaledAlgorithmTest {
    @Test
    void resumesJournalAfterSessionSerialization() throws Exception {
        JournalingSecureRandom random = new JournalingSecureRandom();
        byte[] firstRun = new byte[32];
        random.nextBytes(firstRun);
        AlgorithmIdentifier algorithm = new AlgorithmIdentifier(NISTObjectIdentifiers.id_aes256_CBC);
        JournaledAlgorithm original = new JournaledAlgorithm(algorithm, random);

        JournaledAlgorithm restored = SerializationSupport.roundTrip(original);
        byte[] replay = new byte[firstRun.length];
        restored.getJournalingSecureRandom().nextBytes(replay);
        assertEquals(algorithm, restored.getAlgorithmIdentifier());
        assertArrayEquals(firstRun, replay);
    }
}
