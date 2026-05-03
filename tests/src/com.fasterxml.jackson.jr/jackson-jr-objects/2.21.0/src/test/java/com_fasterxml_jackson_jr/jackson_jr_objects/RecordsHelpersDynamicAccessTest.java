/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Constructor;
import java.util.List;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import com.fasterxml.jackson.jr.ob.impl.BeanPropertyIntrospector;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
import com.fasterxml.jackson.jr.ob.impl.RecordsHelpers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordsHelpersDynamicAccessTest {
    private static final JSON JSON_WITH_DECLARATION_ORDER = JSON.std.with(
            JSON.Feature.WRITE_RECORD_FIELDS_IN_DECLARATION_ORDER);
    private static final BeanPropertyIntrospector INTROSPECTOR = BeanPropertyIntrospector.instance();
    private static final JSONReader JSON_READER = new JSONReader(CollectionBuilder.defaultImpl(),
            MapBuilder.defaultImpl());

    @Test
    void findsCanonicalConstructorsForRecords() {
        Constructor<?> constructor = RecordsHelpers.findCanonicalConstructor(InventoryItem.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class, boolean.class);
    }

    @Test
    void derivesRecordPropertiesFromCanonicalConstructor() {
        POJODefinition definition = INTROSPECTOR.pojoDefinitionForDeserialization(JSON_READER, InventoryItem.class);

        assertThat(propertyNames(definition)).containsExactly("id", "quantity", "available");
        assertThat(definition.constructors()).isNotNull();
    }

    @Test
    void deserializesRecordsThroughJsonApi() throws Exception {
        InventoryItem item = JSON.std.beanFrom(InventoryItem.class,
                "{\"id\":\"A-1\",\"quantity\":3,\"available\":true}");

        assertThat(item).isEqualTo(new InventoryItem("A-1", 3, true));
    }

    @Test
    void serializesRecordFieldsInDeclarationOrderThroughJsonApi() throws Exception {
        String json = JSON_WITH_DECLARATION_ORDER.asString(new InventoryItem("A-1", 3, true));

        assertThat(json).isEqualTo("{\"id\":\"A-1\",\"quantity\":3,\"available\":true}");
    }

    private static List<String> propertyNames(POJODefinition definition) {
        return definition.getProperties().stream().map(prop -> prop.name).toList();
    }

    public record InventoryItem(String id, int quantity, boolean available) {
        public InventoryItem(String id) {
            this(id, 0, false);
        }
    }
}
