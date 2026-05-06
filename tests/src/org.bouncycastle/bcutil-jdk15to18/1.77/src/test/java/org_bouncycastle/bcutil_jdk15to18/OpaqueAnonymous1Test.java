/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import org.bouncycastle.oer.Element;
import org.bouncycastle.oer.OEREncoder;
import org.bouncycastle.oer.its.ieee1609dot2.Opaque;
import org.bouncycastle.oer.its.ieee1609dot2.PduFunctionalType;
import org.bouncycastle.oer.its.template.ieee1609dot2.IEEE1609dot2;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpaqueAnonymous1Test {
    @Test
    public void getValueInvokesTargetTypeGetInstanceForParsedOpaqueContent() {
        Element pduFunctionalTypeDefinition = IEEE1609dot2.PduFunctionalType.build();
        PduFunctionalType expectedValue = new PduFunctionalType(2L);
        Opaque opaque = new Opaque(OEREncoder.toByteArray(expectedValue, pduFunctionalTypeDefinition));

        PduFunctionalType decodedValue = Opaque.getValue(
                PduFunctionalType.class,
                pduFunctionalTypeDefinition,
                opaque);

        assertThat(decodedValue.getFunctionalType()).isEqualTo(expectedValue.getFunctionalType());
    }
}
