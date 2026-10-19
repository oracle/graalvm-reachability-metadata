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
import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.jupiter.api.Test;

public class TypeUtilsTest {
    @Test
    void resolvesRawClassForGenericArray() throws Exception {
        Type genericArray = GenericArrays.class.getField("values").getGenericType();

        Class<?> rawType = TypeUtils.getRawType(genericArray, GenericArrays.class);

        assertThat(rawType).isEqualTo(List[].class);
    }

    public static class GenericArrays {
        public List<String>[] values;
    }
}
