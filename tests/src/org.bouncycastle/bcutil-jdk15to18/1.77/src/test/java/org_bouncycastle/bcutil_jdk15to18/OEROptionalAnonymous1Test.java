/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.oer.OEROptional;
import org.bouncycastle.oer.its.ieee1609dot2.basetypes.UINT8;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {

    @Test
    public void getObjectUsesTargetGetInstanceForDefinedOptionalValue() {
        ASN1Integer encodedValue = new ASN1Integer(7);
        OEROptional optional = OEROptional.getInstance(encodedValue);

        OEROptional decodedOptional = optional.getObject(OEROptional.class);

        assertThat(decodedOptional).isNotNull();
        assertThat(decodedOptional.isDefined()).isTrue();
        assertThat(decodedOptional.get().toASN1Primitive()).isEqualTo(encodedValue);
    }

    @Test
    public void getObjectUsesAsn1GetInstanceForDefinedAsn1Value() {
        BigInteger expectedValue = BigInteger.valueOf(23);
        ASN1Integer encodedValue = new ASN1Integer(expectedValue);
        OEROptional optional = OEROptional.getInstance(encodedValue);

        ASN1Integer decodedValue = optional.getObject(ASN1Integer.class);

        assertThat(decodedValue).isNotNull();
        assertThat(decodedValue.getValue()).isEqualTo(expectedValue);
    }

    @Test
    public void getValueUsesTargetGetInstanceForDefinedAsn1Value() {
        BigInteger expectedValue = BigInteger.valueOf(42);
        ASN1Integer encodedValue = new ASN1Integer(expectedValue);

        UINT8 decodedValue = OEROptional.getValue(UINT8.class, encodedValue);

        assertThat(decodedValue).isNotNull();
        assertThat(decodedValue.getValue()).isEqualTo(expectedValue);
    }
}
