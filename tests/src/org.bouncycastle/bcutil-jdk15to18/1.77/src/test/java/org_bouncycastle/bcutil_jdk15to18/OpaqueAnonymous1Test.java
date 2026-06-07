/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.oer.Element;
import org.bouncycastle.oer.OERDefinition;
import org.bouncycastle.oer.OEREncoder;
import org.bouncycastle.oer.its.ieee1609dot2.Opaque;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpaqueAnonymous1Test {

    @Test
    public void getValueParsesOpaqueContentAndInvokesTargetGetInstance() {
        byte[] expectedContent = new byte[] {1, 2, 3, 4};
        Element octetsDefinition = OERDefinition.octets(expectedContent.length).build();
        byte[] encodedContent = OEREncoder.toByteArray(new DEROctetString(expectedContent), octetsDefinition);
        Opaque opaque = new Opaque(encodedContent);

        Opaque decodedValue = Opaque.getValue(Opaque.class, octetsDefinition, opaque);

        assertThat(decodedValue).isNotNull();
        assertThat(decodedValue.getContent()).containsExactly(expectedContent);
    }
}
