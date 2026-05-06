/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk18on;

import java.math.BigInteger;
import java.util.List;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.oer.its.ItsUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ItsUtilsAnonymous1Test {
    @Test
    void fillListInvokesGetInstanceForEachSequenceElement() {
        ASN1Integer firstValue = new ASN1Integer(BigInteger.ONE);
        ASN1Integer secondValue = new ASN1Integer(BigInteger.TEN);
        ASN1Sequence sequence = ItsUtils.toSequence(firstValue, secondValue);
        CapturingAsn1Value.reset();

        List<CapturingAsn1Value> values = ItsUtils.fillList(CapturingAsn1Value.class, sequence);

        assertThat(values).extracting(CapturingAsn1Value::getWrapped).containsExactly(firstValue, secondValue);
        assertThat(CapturingAsn1Value.getInvocationCount()).isEqualTo(2);
    }

    public static final class CapturingAsn1Value extends ASN1Object {
        private static int invocationCount;
        private final ASN1Encodable wrapped;

        private CapturingAsn1Value(ASN1Encodable wrapped) {
            this.wrapped = wrapped;
        }

        public static CapturingAsn1Value getInstance(Object value) {
            invocationCount++;
            return new CapturingAsn1Value((ASN1Encodable) value);
        }

        static void reset() {
            invocationCount = 0;
        }

        static int getInvocationCount() {
            return invocationCount;
        }

        ASN1Encodable getWrapped() {
            return wrapped;
        }

        @Override
        public ASN1Primitive toASN1Primitive() {
            return wrapped.toASN1Primitive();
        }
    }
}
