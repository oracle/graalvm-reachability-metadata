/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mortbay.util.ajax.JSON;
import org.mortbay.util.ajax.JSONPojoConvertor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JSONPojoConvertorInnerSetterTest {
    @Test
    public void coercesParsedNumbersAndArraysToBeanPropertyTypes() {
        JSON json = new JSON();
        json.addConvertor(Settings.class, new JSONPojoConvertor(Settings.class));
        String document = """
                {"class":"%s","count":7,"tags":["fast","safe"],"weights":[2,5],"label":"batch"}
                """.formatted(Settings.class.getName());
        Settings settings = (Settings) json.fromJSON(document);
        assertEquals(7, settings.getCount());
        assertArrayEquals(new String[]{"fast", "safe"}, settings.getTags());
        assertArrayEquals(new int[]{2, 5}, settings.getWeights());
        assertEquals("batch", settings.getLabel());
    }

    @Test
    public void appliesNullAndCompatibleObjectProperties() {
        Settings settings = new Settings();
        settings.setLabel("old label");
        JSONPojoConvertor convertor = new JSONPojoConvertor(Settings.class);
        Map<String, Object> properties = new HashMap<>();
        properties.put("label", null);
        properties.put("count", 12L);
        properties.put("tags", new Object[]{"one", "two"});
        properties.put("weights", new Object[]{3L, 4L});
        assertEquals(4, convertor.setProps(settings, properties));
        assertNull(settings.getLabel());
        assertEquals(12, settings.getCount());
        assertArrayEquals(new String[]{"one", "two"}, settings.getTags());
        assertArrayEquals(new int[]{3, 4}, settings.getWeights());
    }

    public static class Settings {
        private int count;
        private String[] tags;
        private int[] weights;
        private String label;

        public Settings() {
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public String[] getTags() {
            return tags;
        }

        public void setTags(String[] tags) {
            this.tags = tags;
        }

        public int[] getWeights() {
            return weights;
        }

        public void setWeights(int[] weights) {
            this.weights = weights;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
