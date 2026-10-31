/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONPojoConvertor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JSONPojoConvertorTest {
    @Test
    public void roundTripsBeanPropertiesThroughRegisteredConvertor() {
        JSON json = new JSON();
        json.addConvertor(Book.class, new JSONPojoConvertor(Book.class));
        Book book = new Book();
        book.setTitle("Native libraries");
        book.setPages(240);
        book.setAvailable(true);
        String encoded = json.toJSON(book);
        Book restored = (Book) json.fromJSON(encoded);
        assertEquals("Native libraries", restored.getTitle());
        assertEquals(240, restored.getPages());
        assertTrue(restored.isAvailable());
    }

    @Test
    public void omitsExcludedPropertiesFromPlainJson() {
        JSON json = new JSON();
        json.addConvertor(Book.class, new JSONPojoConvertor(Book.class, new String[]{"pages"}));
        Book book = new Book();
        book.setTitle("Public title");
        book.setPages(99);
        Map<?, ?> properties = (Map<?, ?>) new JSON().fromJSON(json.toJSON(book));
        assertEquals("Public title", properties.get("title"));
        assertEquals(false, properties.get("available"));
        assertFalse(properties.containsKey("pages"));
    }

    public static class Book {
        private String title;
        private int pages;
        private boolean available;

        public Book() {
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getPages() {
            return pages;
        }

        public void setPages(int pages) {
            this.pages = pages;
        }

        public boolean isAvailable() {
            return available;
        }

        public void setAvailable(boolean available) {
            this.available = available;
        }
    }
}
