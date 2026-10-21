/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.EnumSet;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.util.ClassUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassUtilInnerEnumTypeLocatorTest {
    @Test
    void locatesTheElementTypeOfAnEmptyEnumSet() {
        EnumSet<Access> values = EnumSet.noneOf(Access.class);

        assertThat(ClassUtil.findEnumType(values)).isEqualTo(Access.class);
    }

    public enum Access {
        READ,
        WRITE
    }
}
