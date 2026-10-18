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

public class MultivaluedStringMapTest {

    @Test
    void convertsHeaderValuesWithStringConstructors() {
        final MultivaluedStringMap headers = new MultivaluedStringMap();
        headers.add("attempts", "7");
        headers.add("timeout", "11");

        final Integer attempts = headers.getFirst("attempts", Integer.class);
        final Long timeout = headers.getFirst("timeout", Long.valueOf(30));

        assertThat(attempts).isEqualTo(7);
        assertThat(timeout).isEqualTo(11L);
        assertThat(headers.getFirst("missing", Long.valueOf(30))).isEqualTo(30L);
    }
}
