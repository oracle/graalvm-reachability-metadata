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

public class TypeUtilTest {
    @Test
    void convertsValuesAndLocatesClassesThroughTheirClassLoader() {
        TextValue converted = (TextValue) TypeUtil.valueOf(TextValue.class, "converted by Jetty");
        assertThat(converted.getValue()).isEqualTo("converted by Jetty");

        URI location = TypeUtil.getClassLoaderLocation(TypeUtil.class);
        assertThat(location).isNotNull();
        assertThat(location.getScheme()).isNotBlank();
    }

    public static final class TextValue {
        private final String value;

        public TextValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
