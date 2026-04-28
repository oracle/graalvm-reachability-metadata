/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_asn1;

import org.apache.kerby.asn1.Asn1;
import org.apache.kerby.asn1.Asn1Converter;
import org.apache.kerby.asn1.parse.Asn1ParseResult;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1Type;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Asn1ConverterTest {
    @Test
    void convertAsInstantiatesRequestedAsn1TypeAndBindsParsedValue() throws Exception {
        BigInteger expectedValue = BigInteger.valueOf(12_345L);
        Asn1Integer encodedValue = new Asn1Integer(expectedValue);
        Asn1ParseResult parseResult = Asn1.parse(Asn1.encode(encodedValue));

        Asn1Type convertedValue = Asn1Converter.convertAs(parseResult, Asn1Integer.class);

        assertThat(convertedValue).isInstanceOf(Asn1Integer.class);
        assertThat(((Asn1Integer) convertedValue).getValue()).isEqualTo(expectedValue);
    }
}
