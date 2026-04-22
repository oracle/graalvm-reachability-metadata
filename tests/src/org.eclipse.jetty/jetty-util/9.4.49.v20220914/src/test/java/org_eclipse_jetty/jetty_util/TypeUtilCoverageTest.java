/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.net.URI;

import org.eclipse.jetty.util.TypeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeUtilCoverageTest {
    public static class ValueWithStringConstructor {
        private final String value;

        public ValueWithStringConstructor(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    @Test
    void typeUtilConvertsValuesAndFindsClassLoaderLocations() {
        assertThat(TypeUtil.valueOf(Integer.class, "17")).isEqualTo(17);

        ValueWithStringConstructor value = (ValueWithStringConstructor) TypeUtil.valueOf(ValueWithStringConstructor.class, "jetty");
        assertThat(value.getValue()).isEqualTo("jetty");

        URI location = TypeUtil.getClassLoaderLocation(TypeUtilCoverageTest.class);
        assertThat(location == null || location.isAbsolute()).isTrue();
    }
}
