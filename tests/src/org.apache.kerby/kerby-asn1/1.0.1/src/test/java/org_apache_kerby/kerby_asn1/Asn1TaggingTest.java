/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_asn1;

import org.apache.kerby.asn1.TagClass;
import org.apache.kerby.asn1.TaggingOption;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1Tagging;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class Asn1TaggingTest {
    private static final int TAG_NUMBER = 7;

    @Test
    void nullTaggedValueIsInstantiatedFromGenericAsn1Type() {
        IntegerTagging tagging = new IntegerTagging(false, true);

        assertThat(tagging.getValue()).isInstanceOf(Asn1Integer.class);
        assertThat(tagging.getValue().getValue()).isNull();
        assertThat(tagging.tag().tagClass()).isEqualTo(TagClass.CONTEXT_SPECIFIC);
        assertThat(tagging.tag().tagNo()).isEqualTo(TAG_NUMBER);
        assertThat(tagging.isImplicit()).isTrue();
        assertThat(tagging.isPrimitive()).isTrue();
    }

    @Test
    void explicitApplicationTaggingInitializesValueAsConstructedType() {
        IntegerTagging tagging = new IntegerTagging(true, false);

        assertThat(tagging.getValue()).isInstanceOf(Asn1Integer.class);
        assertThat(tagging.tag().tagClass()).isEqualTo(TagClass.APPLICATION);
        assertThat(tagging.tag().tagNo()).isEqualTo(TAG_NUMBER);
        assertThat(tagging.isImplicit()).isFalse();
        assertThat(tagging.isPrimitive()).isFalse();
    }

    @Test
    void initializedImplicitValueDecodesTaggedIntegerBody() throws Exception {
        BigInteger expectedValue = BigInteger.valueOf(8_192L);
        byte[] encoded = new Asn1Integer(expectedValue)
                .taggedEncode(TaggingOption.newImplicitContextSpecific(TAG_NUMBER));
        IntegerTagging tagging = new IntegerTagging(false, true);

        tagging.decode(encoded);

        assertThat(tagging.getValue().getValue()).isEqualTo(expectedValue);
    }

    @Test
    void initializedExplicitValueDecodesNestedTaggedInteger() throws Exception {
        BigInteger expectedValue = BigInteger.valueOf(16_384L);
        byte[] encoded = new Asn1Integer(expectedValue)
                .taggedEncode(TaggingOption.newExplicitContextSpecific(TAG_NUMBER));
        IntegerTagging tagging = new IntegerTagging(false, false);

        tagging.decode(encoded);

        assertThat(tagging.getValue().getValue()).isEqualTo(expectedValue);
    }

    private static final class IntegerTagging extends Asn1Tagging<Asn1Integer> {
        private IntegerTagging(boolean isAppSpecific, boolean isImplicit) {
            super(TAG_NUMBER, null, isAppSpecific, isImplicit);
        }
    }
}
