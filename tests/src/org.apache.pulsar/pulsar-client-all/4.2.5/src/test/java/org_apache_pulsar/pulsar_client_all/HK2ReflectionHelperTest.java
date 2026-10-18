/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Type;
import java.util.List;
import org.apache.pulsar.shade.javax.ws.rs.core.GenericType;
import org.apache.pulsar.shade.org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class HK2ReflectionHelperTest {

    @Test
    void resolvesTheRawClassOfAGenericArrayType() {
        final Type genericArray = new GenericType<List<String>[]>() {
        }.getType();

        assertThat(ReflectionHelper.getRawClass(genericArray)).isEqualTo(List[].class);
    }
}
