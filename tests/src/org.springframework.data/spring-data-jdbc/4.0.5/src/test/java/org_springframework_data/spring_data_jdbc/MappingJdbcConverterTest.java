/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jdbc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(30)
public class MappingJdbcConverterTest {
    private MappingJdbcConverter converter;
    private RelationalPersistentEntity<?> entity;

    @BeforeEach
    void setUp() {
        RelationalMappingContext context = new RelationalMappingContext();
        context.setSimpleTypeHolder(new JdbcCustomConversions().getSimpleTypeHolder());
        converter = new MappingJdbcConverter(context, (identifier, path) -> {
            throw new AssertionError("Column type resolution must not fetch aggregate relations");
        });
        entity = context.getRequiredPersistentEntity(ArrayColumns.class);
    }

    @Test
    void resolvesStringCollectionsToJdbcArrayColumns() {
        RelationalPersistentProperty property = entity.getRequiredPersistentProperty("tags");

        assertEquals(String[].class, converter.getColumnType(property));
    }

    @Test
    void resolvesPrimitiveArraysToBoxedJdbcArrayColumns() {
        RelationalPersistentProperty property = entity.getRequiredPersistentProperty("scores");

        assertEquals(Integer[].class, converter.getColumnType(property));
    }

    public static class ArrayColumns {
        public List<String> tags;
        public int[] scores;
    }
}
