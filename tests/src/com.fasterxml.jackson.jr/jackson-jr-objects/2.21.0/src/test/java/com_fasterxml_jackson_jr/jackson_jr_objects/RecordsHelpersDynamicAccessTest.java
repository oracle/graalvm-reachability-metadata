/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import java.lang.reflect.Constructor;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.api.CollectionBuilder;
import com.fasterxml.jackson.jr.ob.api.MapBuilder;
import com.fasterxml.jackson.jr.ob.api.ValueReader;
import com.fasterxml.jackson.jr.ob.impl.JSONReader;
import com.fasterxml.jackson.jr.ob.impl.RecordsHelpers;
import com.fasterxml.jackson.jr.ob.impl.ValueReaderLocator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordsHelpersDynamicAccessTest {
    private static final JSON JSON_WITH_RECORD_DECLARATION_ORDER = JSON.std.with(
            JSON.Feature.WRITE_RECORD_FIELDS_IN_DECLARATION_ORDER);

    @Test
    void findsCanonicalConstructorForTopLevelRecord() {
        Constructor<?> constructor = RecordsHelpers.findCanonicalConstructor(RecordsHelpersConstructorItem.class);

        assertThat(constructor).isNotNull();
        assertThat(constructor.getParameterTypes()).containsExactly(String.class, int.class, boolean.class);
    }

    @Test
    void resolvesRecordReaderFromDeclaredRecordFields() {
        JSONReader reader = new JSONReader(CollectionBuilder.defaultImpl(), MapBuilder.defaultImpl());
        ValueReaderLocator locator = ValueReaderLocator.blueprint(null, null).perOperationInstance(reader,
                JSON.Feature.defaults());

        ValueReader valueReader = locator.findReader(RecordsHelpersReadableItem.class);

        assertThat(valueReader.valueType()).isEqualTo(RecordsHelpersReadableItem.class);
    }

    @Test
    void deserializesRecordThroughCanonicalConstructor() throws Exception {
        RecordsHelpersReadableItem item = JSON.std.beanFrom(RecordsHelpersReadableItem.class, """
                {
                  "quantity": 4,
                  "stocked": true,
                  "sku": "SKU-13"
                }
                """);

        assertThat(item.sku()).isEqualTo("SKU-13");
        assertThat(item.quantity()).isEqualTo(4);
        assertThat(item.stocked()).isTrue();
    }

    @Test
    void serializesRecordInDeclarationOrder() throws Exception {
        RecordsHelpersInventoryItem item = new RecordsHelpersInventoryItem("SKU-21", 7, false);

        String json = JSON_WITH_RECORD_DECLARATION_ORDER.asString(item);

        assertThat(json).isEqualTo("{\"sku\":\"SKU-21\",\"quantity\":7,\"stocked\":false}");
    }
}

record RecordsHelpersConstructorItem(String sku, int quantity, boolean stocked) {
}

record RecordsHelpersReadableItem(String sku, int quantity, boolean stocked) {
}

record RecordsHelpersInventoryItem(String sku, int quantity, boolean stocked) {
}
