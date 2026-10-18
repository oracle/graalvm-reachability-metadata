/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.pulsar.shade.javax.ws.rs.core.GenericType;
import org.junit.jupiter.api.Test;

public class GenericTypeTest {

    @Test
    void resolvesTheRawClassOfAGenericArray() {
        final GenericType<List<String>[]> type = new GenericType<List<String>[]>() {
        };

        assertThat(type.getRawType()).isEqualTo(List[].class);
        assertThat(type.getType().getTypeName()).contains("java.util.List<java.lang.String>[]");
    }
}
