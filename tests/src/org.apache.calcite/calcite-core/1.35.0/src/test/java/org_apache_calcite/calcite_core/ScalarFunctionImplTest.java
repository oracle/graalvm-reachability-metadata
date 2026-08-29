/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ScalarFunctionImplTest {
    @SuppressWarnings("deprecation")
    @Test
    void discoversAllPublicScalarFunctions() {
        assertThat(ScalarFunctionImpl.createAll(StringFunctions.class).keySet())
                .contains("upper", "length");
        assertThat(ScalarFunctionImpl.functions(StringFunctions.class).keySet())
                .contains("upper", "length");
    }

    public static class StringFunctions {
        public static String upper(String value) {
            return value.toUpperCase();
        }

        public static int length(String value) {
            return value.length();
        }
    }
}
