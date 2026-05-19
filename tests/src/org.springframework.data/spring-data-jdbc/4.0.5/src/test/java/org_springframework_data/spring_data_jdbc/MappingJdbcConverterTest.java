/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.repository.query.StatementFactory;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;

public class MappingJdbcConverterTest {

    @Test
    void getColumnTypeCreatesArrayTypeForCollectionProperty() {
        RelationalMappingContext mappingContext = new RelationalMappingContext();
        MappingJdbcConverter converter = new MappingJdbcConverter(mappingContext, (identifier, path) -> List.of());
        RelationalPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(
                StatementFactory.SelectionBuilder.class);
        RelationalPersistentProperty property = entity.getRequiredPersistentProperty("properties");

        Class<?> columnType = converter.getColumnType(property);

        assertThat(columnType).isEqualTo(String[].class);
    }
}
