/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_core;

import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.kerberos.kerb.KrbCodec;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class KrbCodecTest {
    @Test
    void decodesAsn1TypeFromClassToken() throws Exception {
        Asn1Integer original = new Asn1Integer(42);

        Asn1Integer decoded = KrbCodec.decode(KrbCodec.encode(original), Asn1Integer.class);

        assertThat(decoded.getValue()).isEqualTo(BigInteger.valueOf(42));
    }
}
