/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk18on;

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.oer.OEROptional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {
    @Test
    void getObjectInvokesPublicGetInstanceOnRequestedType() {
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(42));
        OEROptional optional = OEROptional.getInstance(encodedValue);
        CapturingAsn1Value.reset();

        CapturingAsn1Value decodedValue = decodeOptional(optional, CapturingAsn1Value.class);

        assertThat(decodedValue.getWrapped()).isSameAs(encodedValue);
        assertThat(CapturingAsn1Value.getInvocationCount()).isEqualTo(1);
        assertThat(CapturingAsn1Value.getLastInput()).isSameAs(encodedValue);
    }

    @Test
    void getValueInvokesPublicGetInstanceOnRequestedType() {
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(7));
        CapturingAsn1Value.reset();

        CapturingAsn1Value decodedValue = OEROptional.getValue(CapturingAsn1Value.class, encodedValue);

        assertThat(decodedValue.getWrapped()).isSameAs(encodedValue);
        assertThat(CapturingAsn1Value.getInvocationCount()).isEqualTo(1);
        assertThat(CapturingAsn1Value.getLastInput()).isSameAs(encodedValue);
    }

    @Test
    void getObjectInvokesGetInstanceMethodOnCustomAsn1Type() {
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(99));
        OEROptional optional = OEROptional.getInstance(encodedValue);
        CapturingAsn1Value.reset();

        CapturingAsn1Value decodedValue = decodeOptional(optional, CapturingAsn1Value.class);

        assertThat(decodedValue.getWrapped()).isSameAs(encodedValue);
        assertThat(CapturingAsn1Value.getInvocationCount()).isEqualTo(1);
        assertThat(CapturingAsn1Value.getLastInput()).isSameAs(encodedValue);
    }

    private static <T> T decodeOptional(OEROptional optional, Class<T> type) {
        return optional.getObject(type);
    }

    public static final class CapturingAsn1Value extends ASN1Object {
        private static int invocationCount;
        private static ASN1Encodable lastInput;
        private final ASN1Encodable wrapped;

        private CapturingAsn1Value(ASN1Encodable wrapped) {
            this.wrapped = wrapped;
        }

        public static CapturingAsn1Value getInstance(Object value) {
            invocationCount++;
            lastInput = (ASN1Encodable) value;
            return new CapturingAsn1Value(lastInput);
        }

        static void reset() {
            invocationCount = 0;
            lastInput = null;
        }

        static int getInvocationCount() {
            return invocationCount;
        }

        static ASN1Encodable getLastInput() {
            return lastInput;
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
