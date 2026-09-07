/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.util.Date;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorInnerConstructScalarTest {
    @Test
    void constructsACustomScalarThroughItsStringConstructor() {
        Yaml yaml = new Yaml();
        Scalar scalar = yaml.loadAs("metadata", Scalar.class);
        CustomDate date = yaml.loadAs("2024-01-02T03:04:05Z", CustomDate.class);

        assertThat(scalar.value).isEqualTo("metadata");
        assertThat(date).isAfter(new Date(0));
    }

    public static class Scalar {
        private final String value;

        public Scalar(String value) {
            this.value = value;
        }

        public Scalar(Object value) {
            this.value = value.toString();
        }
    }

    public static class CustomDate extends Date {
        private static final long serialVersionUID = 1L;

        public CustomDate(long timestamp) {
            super(timestamp);
        }
    }
}
