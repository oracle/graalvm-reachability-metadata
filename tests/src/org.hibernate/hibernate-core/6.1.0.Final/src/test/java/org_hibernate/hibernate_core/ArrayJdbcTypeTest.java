/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.jdbc.ArrayJdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayJdbcTypeTest {

    @Test
    public void recommendsAnArrayDescriptorForTheElementJdbcType() {
        ArrayJdbcType jdbcType = new ArrayJdbcType(VarcharJdbcType.INSTANCE);

        BasicJavaType<?> javaType = jdbcType.getJdbcRecommendedJavaTypeMapping(
                null,
                null,
                new TypeConfiguration()
        );

        assertThat(javaType.getJavaTypeClass()).isEqualTo(String[].class);
    }
}
