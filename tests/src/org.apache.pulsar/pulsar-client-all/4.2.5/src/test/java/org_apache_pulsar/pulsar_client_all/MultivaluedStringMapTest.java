/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class MultivaluedStringMapTest {
    @Test
    void convertsStringValuesThroughPublicConstructors() {
        MultivaluedStringMap values = new MultivaluedStringMap();
        values.putSingle("port", "6650");
        values.putSingle("retries", "4");

        Port port = values.getFirst("port", Port.class);
        RetryCount retries = values.getFirst("retries", new RetryCount("1"));

        assertThat(port.value()).isEqualTo(6650);
        assertThat(retries.value()).isEqualTo(4);
        assertThat(values.getFirst("missing", new RetryCount("2")).value()).isEqualTo(2);
    }

    public static final class Port {
        private final int value;

        public Port(String value) {
            this.value = Integer.parseInt(value);
        }

        public int value() {
            return value;
        }
    }

    public static final class RetryCount {
        private final int value;

        public RetryCount(String value) {
            this.value = Integer.parseInt(value);
        }

        public int value() {
            return value;
        }
    }
}
