/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_asn1;

import org.apache.kerby.asn1.Asn1;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1SequenceOf;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Asn1CollectionOfTest {
    @Test
    void decodeCollectionCreatesTypedElementsFromGenericElementType() throws Exception {
        BigInteger firstValue = BigInteger.valueOf(101L);
        BigInteger secondValue = BigInteger.valueOf(202L);
        IntegerSequence encodedSequence = new IntegerSequence();
        encodedSequence.add(new Asn1Integer(firstValue));
        encodedSequence.add(new Asn1Integer(secondValue));

        IntegerSequence decodedSequence = new IntegerSequence();
        decodedSequence.decode(Asn1.encode(encodedSequence));

        List<Asn1Integer> decodedElements = decodedSequence.getElements();
        assertThat(decodedElements).hasSize(2);
        assertThat(decodedElements.get(0).getValue()).isEqualTo(firstValue);
        assertThat(decodedElements.get(1).getValue()).isEqualTo(secondValue);
    }

    private static final class IntegerSequence extends Asn1SequenceOf<Asn1Integer> {
    }
}
