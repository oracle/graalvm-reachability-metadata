/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

/** Verifies identifier conversion through an entity finder method. */
public class IdToEntityConverterTest {
    @Test
    void findsEntityUsingConvertedIdentifier() {
        DefaultConversionService conversionService = new DefaultConversionService();

        Entity entity = conversionService.convert("42", Entity.class);

        assertThat(entity).isNotNull();
        assertThat(entity.id).isEqualTo(42L);
    }

    public static final class Entity {
        private final long id;

        private Entity(long id) {
            this.id = id;
        }

        public static Entity findEntity(Long id) {
            return new Entity(id);
        }
    }
}
