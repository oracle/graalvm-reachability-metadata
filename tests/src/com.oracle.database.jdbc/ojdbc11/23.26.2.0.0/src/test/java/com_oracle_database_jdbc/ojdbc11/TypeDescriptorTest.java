/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import oracle.sql.TypeDescriptor;
import org.junit.jupiter.api.Test;

public class TypeDescriptorTest {
    @Test
    void mapsInternalTypeCodesToTheirNames() throws Exception {
        TypeDescriptor descriptor = new NumberTypeDescriptor();

        assertThat(descriptor.getTypeCode()).isEqualTo(TypeDescriptor.TYPECODE_NUMBER);
        assertThat(descriptor.getTypeCodeName()).isEqualTo("TYPECODE_NUMBER");
    }

    private static final class NumberTypeDescriptor extends TypeDescriptor {
        private NumberTypeDescriptor() {
            super(TYPECODE_NUMBER);
        }
    }
}
