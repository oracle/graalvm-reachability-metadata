/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerby_asn1;

import org.apache.kerby.asn1.Asn1FieldInfo;
import org.apache.kerby.asn1.EnumType;
import org.apache.kerby.asn1.Tag;
import org.apache.kerby.asn1.UniversalTag;
import org.apache.kerby.asn1.type.Asn1Integer;
import org.apache.kerby.asn1.type.Asn1Type;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Asn1FieldInfoTest {
    @Test
    void createFieldValueInstantiatesConfiguredAsn1Type() {
        Asn1FieldInfo fieldInfo = new Asn1FieldInfo(SampleField.NUMBER, Asn1Integer.class);

        Asn1Type fieldValue = fieldInfo.createFieldValue();

        assertThat(fieldValue).isInstanceOf(Asn1Integer.class);
        assertThat(fieldValue.tag().universalTag()).isEqualTo(UniversalTag.INTEGER);
    }

    @Test
    void getFieldTagDerivesTagFromInstantiatedFieldValue() {
        Asn1FieldInfo fieldInfo = new Asn1FieldInfo(SampleField.NUMBER, Asn1Integer.class);

        Tag fieldTag = fieldInfo.getFieldTag();

        assertThat(fieldTag.universalTag()).isEqualTo(UniversalTag.INTEGER);
    }

    private enum SampleField implements EnumType {
        NUMBER(0);

        private final int value;

        SampleField(int value) {
            this.value = value;
        }

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public String getName() {
            return name();
        }
    }
}
