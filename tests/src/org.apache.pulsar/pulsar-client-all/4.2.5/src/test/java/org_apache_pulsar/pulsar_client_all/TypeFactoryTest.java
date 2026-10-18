/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

public class TypeFactoryTest {

    @Test
    void resolvesNamedAndGenericArrayClasses() throws Exception {
        final TypeFactory factory = TypeFactory.defaultInstance();
        final GenericArrayType genericArray = new GenericArrayType() {
            @Override
            public Type getGenericComponentType() {
                return String.class;
            }
        };

        assertThat(factory.findClass("java.util.ArrayList")).isEqualTo(java.util.ArrayList.class);
        assertThat(TypeFactory.rawClass(genericArray)).isEqualTo(String[].class);
    }
}
