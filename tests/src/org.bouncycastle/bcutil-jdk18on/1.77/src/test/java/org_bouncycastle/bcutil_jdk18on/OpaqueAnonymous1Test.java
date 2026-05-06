/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk18on;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.oer.Element;
import org.bouncycastle.oer.OERDefinition;
import org.bouncycastle.oer.OEROutputStream;
import org.bouncycastle.oer.its.ieee1609dot2.Opaque;
import org.bouncycastle.oer.its.ieee1609dot2.basetypes.UINT8;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpaqueAnonymous1Test {
    @Test
    void getValueParsesOpaqueContentAndInvokesGetInstanceOnLibraryType() throws Exception {
        Element definition = OERDefinition.integer(0, 255).build();
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(42));
        Opaque opaque = new Opaque(encode(encodedValue, definition));

        UINT8 value = Opaque.getValue(UINT8.class, definition, opaque);

        assertThat(value.getValue()).isEqualTo(BigInteger.valueOf(42));
    }

    @Test
    void getValueWrapsFailuresFromInvokedGetInstanceMethod() throws Exception {
        Element definition = OERDefinition.integer(0, 255).build();
        ASN1Integer encodedValue = new ASN1Integer(BigInteger.valueOf(7));
        Opaque opaque = new Opaque(encode(encodedValue, definition));

        assertThatThrownBy(() -> Opaque.getValue(RejectingValue.class, definition, opaque))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not invoke getInstance on type");
    }

    private static byte[] encode(ASN1Integer value, Element definition) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OEROutputStream oerOutput = new OEROutputStream(output);
        oerOutput.write(value, definition);
        return output.toByteArray();
    }

    public static final class RejectingValue {
        private RejectingValue() {
        }

        public static RejectingValue getInstance(Object value) {
            throw new IllegalArgumentException("rejected parsed value " + value.getClass().getSimpleName());
        }
    }
}
