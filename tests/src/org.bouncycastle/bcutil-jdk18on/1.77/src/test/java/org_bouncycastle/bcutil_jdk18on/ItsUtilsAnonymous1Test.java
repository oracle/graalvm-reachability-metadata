/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk18on;

import java.math.BigInteger;
import java.util.List;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.oer.its.ItsUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ItsUtilsAnonymous1Test {
    @Test
    @SuppressWarnings("deprecation")
    void fillsListByCallingSuppliedTypeFactoryForEachSequenceItem() {
        ASN1Sequence sequence = ItsUtils.toSequence(new ASN1Integer(7), new ASN1Integer(255));

        List<DecodedInteger> values = ItsUtils.fillList(DecodedInteger.class, sequence);

        assertThat(values).hasSize(2);
        assertThat(values.get(0).value).isEqualTo(BigInteger.valueOf(7));
        assertThat(values.get(1).value).isEqualTo(BigInteger.valueOf(255));
    }

    public static class DecodedInteger {
        private final BigInteger value;

        private DecodedInteger(BigInteger value) {
            this.value = value;
        }

        public static DecodedInteger getInstance(Object source) {
            ASN1Integer integer = ASN1Integer.getInstance(source);
            return new DecodedInteger(integer.getValue());
        }
    }
}
