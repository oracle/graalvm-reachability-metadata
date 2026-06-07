/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import java.math.BigInteger;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.oer.its.ItsUtils;
import org.bouncycastle.oer.its.ieee1609dot2.basetypes.UINT8;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ItsUtilsAnonymous1Test {

    @Test
    @SuppressWarnings("deprecation")
    public void fillListInvokesElementGetInstanceForEachSequenceValue() {
        ASN1Sequence sequence = new DERSequence(new ASN1Encodable[] {
            new ASN1Integer(1),
            new ASN1Integer(2),
            new ASN1Integer(255)
        });

        List<UINT8> decodedValues = ItsUtils.fillList(UINT8.class, sequence);

        assertThat(decodedValues).hasSize(3);
        assertThat(decodedValues)
            .extracting(UINT8::getValue)
            .containsExactly(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(255));
    }
}
