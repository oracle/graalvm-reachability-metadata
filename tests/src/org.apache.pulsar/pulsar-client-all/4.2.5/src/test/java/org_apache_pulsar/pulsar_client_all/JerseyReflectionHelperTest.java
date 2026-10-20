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

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class JerseyReflectionHelperTest {
    @Test
    void createsArrayClassForComponentType() {
        assertThat(ReflectionHelper.getArrayForComponentType(String.class)).isEqualTo(String[].class);
    }

    @Test
    void resolvesRawClassForGenericArrayType() throws Exception {
        Type genericArray = GenericArrays.class.getField("values").getGenericType();

        assertThat(ReflectionHelper.getRawClass(genericArray)).isEqualTo(List[].class);
    }

    public static class GenericArrays {
        public List<String>[] values;
    }
}
