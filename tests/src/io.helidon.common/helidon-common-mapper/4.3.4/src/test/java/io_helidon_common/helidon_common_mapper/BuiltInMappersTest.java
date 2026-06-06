/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_helidon_common.helidon_common_mapper;

import io.helidon.common.mapper.Mappers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuiltInMappersTest {
    @Test
    void mapsClassNamesThroughBuiltInStringToClassMapper() {
        Mappers mappers = Mappers.builder()
                .mapperProvidersDiscoverServices(false)
                .mappersDiscoverServices(false)
                .build();

        Class<?> mappedClass = mappers.map(MappedType.class.getName(), String.class, Class.class);

        assertThat(mappedClass).isSameAs(MappedType.class);
    }

    public static final class MappedType {
        private MappedType() {
        }
    }
}
