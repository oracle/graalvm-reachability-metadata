/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_data_commons;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.data.autoconfigure.web.DataWebProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataWebPropertiesTest {

    @Test
    void bindsPageableAndSortSettingsFromApplicationProperties() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "spring.data.web.pageable.page-parameter", "number",
                "spring.data.web.pageable.size-parameter", "limit",
                "spring.data.web.pageable.one-indexed-parameters", "true",
                "spring.data.web.pageable.prefix", "books-",
                "spring.data.web.pageable.qualifier-delimiter", ".",
                "spring.data.web.pageable.default-page-size", "30",
                "spring.data.web.pageable.max-page-size", "120",
                "spring.data.web.pageable.serialization-mode", "via-dto",
                "spring.data.web.sort.sort-parameter", "order"));

        DataWebProperties properties = new Binder(source).bind("spring.data.web", DataWebProperties.class).get();
        DataWebProperties.Pageable pageable = properties.getPageable();

        assertEquals("number", pageable.getPageParameter());
        assertEquals("limit", pageable.getSizeParameter());
        assertTrue(pageable.isOneIndexedParameters());
        assertEquals("books-", pageable.getPrefix());
        assertEquals(".", pageable.getQualifierDelimiter());
        assertEquals(30, pageable.getDefaultPageSize());
        assertEquals(120, pageable.getMaxPageSize());
        assertEquals(PageSerializationMode.VIA_DTO, pageable.getSerializationMode());
        assertEquals("order", properties.getSort().getSortParameter());
    }
}
