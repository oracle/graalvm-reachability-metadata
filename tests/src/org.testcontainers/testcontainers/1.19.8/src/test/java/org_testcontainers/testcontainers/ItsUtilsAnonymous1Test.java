/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1Integer;
import org.testcontainers.shaded.org.bouncycastle.asn1.DERSequence;
import org.testcontainers.shaded.org.bouncycastle.oer.its.ItsUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ItsUtilsAnonymous1Test {
    @Test
    void convertsSequenceElementsThroughTheirAsn1Factory() {
        DERSequence sequence = new DERSequence(new ASN1Integer(3));

        List<ASN1Integer> values = ItsUtils.fillList(ASN1Integer.class, sequence);

        assertThat(values).extracting(ASN1Integer::intValueExact).containsExactly(3);
    }
}
