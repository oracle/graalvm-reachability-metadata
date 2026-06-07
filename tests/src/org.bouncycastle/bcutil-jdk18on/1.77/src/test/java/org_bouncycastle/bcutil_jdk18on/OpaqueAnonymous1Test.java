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
import org.bouncycastle.oer.Element;
import org.bouncycastle.oer.OERDefinition;
import org.bouncycastle.oer.OEREncoder;
import org.bouncycastle.oer.its.ieee1609dot2.Opaque;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OpaqueAnonymous1Test {
    @Test
    void decodesOpaqueContentThroughTargetTypeFactory() {
        Element definition = OERDefinition.integer(0, 255).build();
        ASN1Integer expectedValue = new ASN1Integer(73);
        Opaque opaque = new Opaque(OEREncoder.toByteArray(expectedValue, definition));

        DecodedInteger decodedValue = Opaque.getValue(DecodedInteger.class, definition, opaque);

        assertThat(decodedValue.value).isEqualTo(BigInteger.valueOf(73));
    }

    public static class DecodedInteger {
        private final BigInteger value;

        private DecodedInteger(BigInteger value) {
            this.value = value;
        }

        public static DecodedInteger getInstance(Object source) {
            ASN1Encodable encodable = (ASN1Encodable) source;
            ASN1Integer integer = ASN1Integer.getInstance(encodable);
            return new DecodedInteger(integer.getValue());
        }
    }
}
