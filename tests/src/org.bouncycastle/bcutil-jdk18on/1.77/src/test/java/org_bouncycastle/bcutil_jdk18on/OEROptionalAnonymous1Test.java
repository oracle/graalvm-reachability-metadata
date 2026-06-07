/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk18on;

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.oer.OEROptional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {
    @Test
    void returnsOptionalValueThroughTargetTypeFactory() {
        ASN1Integer encodedValue = new ASN1Integer(42);
        OEROptional optionalValue = OEROptional.getInstance(encodedValue);

        DecodedInteger decodedValue = optionalValue.getObject(DecodedInteger.class);

        assertThat(optionalValue.isDefined()).isTrue();
        assertThat(decodedValue).isNotNull();
        assertThat(decodedValue.value).isEqualTo(BigInteger.valueOf(42));
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
