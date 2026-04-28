/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_asn1;

import org.apache.kerby.asn1.Asn1;
import org.apache.kerby.asn1.UniversalTag;
import org.apache.kerby.asn1.type.Asn1Any;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1Type;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Asn1AnyTest {
    @Test
    void decodeWithValueTypeInstantiatesAndBindsRequestedAsn1Type() throws Exception {
        BigInteger expectedValue = BigInteger.valueOf(67_890L);
        byte[] encodedValue = Asn1.encode(new Asn1Integer(expectedValue));
        Asn1Any anyValue = new Asn1Any();
        anyValue.setValueType(Asn1Integer.class);

        anyValue.decode(encodedValue);

        Asn1Type decodedValue = anyValue.getValue();
        assertThat(decodedValue).isInstanceOf(Asn1Integer.class);
        assertThat(((Asn1Integer) decodedValue).getValue()).isEqualTo(expectedValue);
        assertThat(anyValue.getParseResult()).isNotNull();
        assertThat(anyValue.tag().universalTag()).isEqualTo(UniversalTag.INTEGER);
    }
}
