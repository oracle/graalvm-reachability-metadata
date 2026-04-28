/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordsHelpersDynamicAccessTest {
    @Test
    void derivesCanonicalRecordConstructorAndOrderedProperties() {
        Map<String, String> properties = new LinkedHashMap<>();

        Constructor<?> canonical = BeanPropertyIntrospector.derivePropertiesFromRecordConstructor(
                SampleRecord.class, properties, name -> name);

        assertThat(canonical.getParameterTypes()).containsExactly(String.class, int.class, boolean.class);
        assertThat(properties).containsExactly(
                Map.entry("name", "name"),
                Map.entry("id", "id"),
                Map.entry("active", "active"));
    }

    @Test
    void deserializesRecordsThroughTheirCanonicalConstructor() throws Exception {
        SampleRecord record = JSON.std.beanFrom(SampleRecord.class, "{\"active\":true,\"id\":7,\"name\":\"Ada\"}");

        assertThat(record.name()).isEqualTo("Ada");
        assertThat(record.id()).isEqualTo(7);
        assertThat(record.active()).isTrue();
    }

    @Test
    void usesRecordComponentDefaultsWhenPrimitivePropertiesAreMissing() throws Exception {
        SampleRecord record = JSON.std.beanFrom(SampleRecord.class, "{\"name\":\"Bob\",\"id\":3}");

        assertThat(record.name()).isEqualTo("Bob");
        assertThat(record.id()).isEqualTo(3);
        assertThat(record.active()).isFalse();
    }

    public record SampleRecord(String name, int id, boolean active) {
    }
}
