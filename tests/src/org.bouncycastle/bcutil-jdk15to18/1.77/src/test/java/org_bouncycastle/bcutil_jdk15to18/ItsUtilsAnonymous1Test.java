/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.oer.its.ItsUtils;
import org.bouncycastle.oer.its.etsi102941.AuthorizationResponseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ItsUtilsAnonymous1Test {
    @Test
    @SuppressWarnings("deprecation")
    public void fillListInvokesPublicGetInstanceMethodOnSuppliedTargetType() {
        PublicGetInstanceTarget.reset();
        ASN1Enumerated firstValue = new ASN1Enumerated(BigInteger.ONE);
        ASN1Enumerated secondValue = new ASN1Enumerated(BigInteger.TEN);
        ASN1Sequence sequence = ItsUtils.toSequence(firstValue, secondValue);

        List<PublicGetInstanceTarget> decodedValues = ItsUtils.fillList(PublicGetInstanceTarget.class, sequence);

        assertThat(PublicGetInstanceTarget.invocationCount()).isEqualTo(2);
        assertThat(decodedValues)
                .extracting(PublicGetInstanceTarget::sourceValue)
                .containsExactly(firstValue, secondValue);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void fillListUsesItsTargetTypeGetInstanceForSequenceItems() {
        ASN1Sequence sequence = ItsUtils.toSequence(
                new ASN1Enumerated(BigInteger.ZERO),
                new ASN1Enumerated(BigInteger.valueOf(26L)));

        List<AuthorizationResponseCode> decodedValues = ItsUtils.fillList(AuthorizationResponseCode.class, sequence);

        assertThat(decodedValues).hasSize(2);
        assertThat(decodedValues.get(0).getValue()).isEqualTo(BigInteger.ZERO);
        assertThat(decodedValues.get(1).getValue()).isEqualTo(BigInteger.valueOf(26L));
    }

    public static final class PublicGetInstanceTarget {
        private static final AtomicInteger INVOCATION_COUNT = new AtomicInteger();

        private final ASN1Encodable sourceValue;

        private PublicGetInstanceTarget(ASN1Encodable sourceValue) {
            this.sourceValue = sourceValue;
        }

        public static PublicGetInstanceTarget getInstance(Object sourceValue) {
            INVOCATION_COUNT.incrementAndGet();
            return new PublicGetInstanceTarget((ASN1Encodable) sourceValue);
        }

        static void reset() {
            INVOCATION_COUNT.set(0);
        }

        static int invocationCount() {
            return INVOCATION_COUNT.get();
        }

        ASN1Encodable sourceValue() {
            return sourceValue;
        }
    }
}
