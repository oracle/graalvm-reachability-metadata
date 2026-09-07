/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1Object;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1Primitive;
import org.testcontainers.shaded.org.bouncycastle.asn1.ASN1UTF8String;
import org.testcontainers.shaded.org.bouncycastle.asn1.DERUTF8String;
import org.testcontainers.shaded.org.bouncycastle.oer.OEROptional;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {
    @Test
    void convertsPresentOptionalValuesThroughTheirAsn1Factory() {
        OEROptional optional = OEROptional.getInstance(new WrappedUtf8("present"));

        ASN1UTF8String value = optional.getObject(ASN1UTF8String.class);

        assertThat(value.getString()).isEqualTo("present");
        assertThat(optional.isDefined()).isTrue();
    }

    public static class WrappedUtf8 extends ASN1Object {
        private final String value;

        public WrappedUtf8(String value) {
            this.value = value;
        }

        @Override
        public ASN1Primitive toASN1Primitive() {
            return new DERUTF8String(value);
        }
    }
}
