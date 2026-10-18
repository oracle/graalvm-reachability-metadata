/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.JavaType;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.ArrayType;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.TypeBindings;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.type.TypeFactory;
import org.junit.jupiter.api.Test;

public class ArrayTypeTest {

    @Test
    void replacesTheArrayComponentType() {
        final TypeFactory factory = TypeFactory.defaultInstance();
        final ArrayType strings =
                ArrayType.construct(factory.constructType(String.class), TypeBindings.emptyBindings());
        final JavaType longs = strings.withContentType(factory.constructType(Long.class));

        assertThat(longs.getRawClass()).isEqualTo(Long[].class);
        assertThat(longs.getContentType().getRawClass()).isEqualTo(Long.class);
    }
}
