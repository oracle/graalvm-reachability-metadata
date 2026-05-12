/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.msgpack.template.builder.beans.IndexedPropertyDescriptor;
import org.msgpack.template.builder.beans.IntrospectionException;

public class IndexedPropertyDescriptorTest {
    @Test
    void locatesIndexedGetterAndSetterByExplicitNames() throws IntrospectionException {
        final IndexedPropertyDescriptor descriptor = new IndexedPropertyDescriptor(
                "items", IndexedItemsBean.class, "getItems", "setItems", "getItem", "setItem");

        assertThat(descriptor.getReadMethod().getName()).isEqualTo("getItems");
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setItems");
        assertThat(descriptor.getIndexedReadMethod().getName()).isEqualTo("getItem");
        assertThat(descriptor.getIndexedWriteMethod().getName()).isEqualTo("setItem");
        assertThat(descriptor.getPropertyType()).isSameAs(String[].class);
        assertThat(descriptor.getIndexedPropertyType()).isSameAs(String.class);
    }

    @Test
    void locatesIndexedSetterFromArrayPropertyTypeWhenIndexedGetterIsAbsent() throws IntrospectionException {
        final IndexedPropertyDescriptor descriptor = new IndexedPropertyDescriptor(
                "items", IndexedItemsBean.class, "getItems", "setItems", null, "setItem");

        assertThat(descriptor.getReadMethod().getName()).isEqualTo("getItems");
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setItems");
        assertThat(descriptor.getIndexedReadMethod()).isNull();
        assertThat(descriptor.getIndexedWriteMethod().getName()).isEqualTo("setItem");
        assertThat(descriptor.getPropertyType()).isSameAs(String[].class);
        assertThat(descriptor.getIndexedPropertyType()).isSameAs(String.class);
    }

    public static final class IndexedItemsBean {
        private String[] items = new String[0];

        public String[] getItems() {
            return this.items;
        }

        public void setItems(String[] items) {
            this.items = items;
        }

        public String getItem(int index) {
            return this.items[index];
        }

        public void setItem(int index, String item) {
            this.items[index] = item;
        }
    }
}
