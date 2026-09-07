/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1Integer;
import org.testcontainers.shaded.org.bouncycastle.oer.Element;
import org.testcontainers.shaded.org.bouncycastle.oer.OERDefinition;
import org.testcontainers.shaded.org.bouncycastle.oer.OEROutputStream;
import org.testcontainers.shaded.org.bouncycastle.oer.its.ieee1609dot2.Opaque;

import static org.assertj.core.api.Assertions.assertThat;

public class OpaqueAnonymous1Test {
    @Test
    void decodesOpaqueOerValuesThroughTheirAsn1Factory() throws Exception {
        Element definition = OERDefinition.integer(0, 255).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        OEROutputStream output = new OEROutputStream(bytes);
        output.write(new ASN1Integer(7), definition);
        Opaque opaque = new Opaque(bytes.toByteArray());

        ASN1Integer decoded = Opaque.getValue(ASN1Integer.class, definition, opaque);

        assertThat(decoded.intValueExact()).isEqualTo(7);
    }
}
