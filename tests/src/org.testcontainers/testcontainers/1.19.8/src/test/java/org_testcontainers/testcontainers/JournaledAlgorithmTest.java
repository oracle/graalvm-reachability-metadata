/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.testcontainers.shaded.org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.testcontainers.shaded.org.bouncycastle.crypto.util.JournaledAlgorithm;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;
import org.testcontainers.shaded.org.bouncycastle.crypto.util.JournalingSecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

public class JournaledAlgorithmTest {
    @Test
    void storesAndRestoresTheJournaledRandomState() throws Exception {
        AlgorithmIdentifier identifier = new AlgorithmIdentifier(new ASN1ObjectIdentifier("1.2.840.113549.1.1.1"));
        JournalingSecureRandom random = new JournalingSecureRandom();
        random.nextBytes(new byte[8]);
        JournaledAlgorithm original = new JournaledAlgorithm(identifier, random);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        JournaledAlgorithm serialized = SerializationUtils.roundtrip(original);
        original.storeState(output);
        JournaledAlgorithm restored = JournaledAlgorithm.getState(
            new ByteArrayInputStream(output.toByteArray()),
            new JournalingSecureRandom()
        );

        assertThat(serialized.getAlgorithmIdentifier()).isEqualTo(identifier);
        assertThat(restored.getAlgorithmIdentifier()).isEqualTo(identifier);
        assertThat(restored.getJournalingSecureRandom().getTranscript()).isEqualTo(random.getTranscript());
    }
}
