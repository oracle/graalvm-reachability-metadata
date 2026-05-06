/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15on;

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.oer.OEROptional;
import org.bouncycastle.oer.its.Uint16;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {
    @Test
    void getObjectInvokesBouncyCastleGetInstanceFactory() {
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(42));
        OEROptional optional = OEROptional.getInstance(encodedValue);

        Uint16 decodedValue = optional.getObject(Uint16.class);
        ASN1Integer decodedPrimitive = ASN1Integer.getInstance(decodedValue.toASN1Primitive());

        assertThat(optional.isDefined()).isTrue();
        assertThat(decodedPrimitive.getValue()).isEqualTo(BigInteger.valueOf(42));
    }
}
