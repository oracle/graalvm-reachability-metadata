/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;

import org.apache.pulsar.shade.org.yaml.snakeyaml.Yaml;
import org.junit.jupiter.api.Test;

public class ConstructorInnerConstructScalarTest {
    @Test
    void constructsStandardAndApplicationScalarTypesFromStrings() {
        final Yaml yaml = new Yaml();
        final BigInteger number = yaml.loadAs("123456789", BigInteger.class);
        final Port port = yaml.loadAs("6650", Port.class);

        assertThat(number).isEqualTo(new BigInteger("123456789"));
        assertThat(port.value).isEqualTo(6650);
    }

    public static final class Port {
        private final int value;

        public Port(String text) {
            value = Integer.parseInt(text);
        }
    }
}
