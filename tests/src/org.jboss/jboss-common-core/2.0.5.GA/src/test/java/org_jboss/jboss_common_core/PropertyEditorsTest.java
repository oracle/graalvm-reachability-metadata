/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.propertyeditor.PropertyEditors;
import org.junit.jupiter.api.Test;

import java.beans.IntrospectionException;
import java.math.BigDecimal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PropertyEditorsTest {
    @Test
    void convertsConfigurationValuesByTypeName() throws Exception {
        assertEquals(42, PropertyEditors.convertValue("42", "int"));
        assertEquals(new BigDecimal("12.50"),
                PropertyEditors.convertValue("12.50", "java.math.BigDecimal"));
    }

    @Test
    void mapsTextPropertiesToBeanSetters() throws Exception {
        Endpoint endpoint = new Endpoint();
        Properties properties = new Properties();
        properties.setProperty("port", "8181");
        properties.setProperty("label", "orders");
        properties.setProperty("price", "9.75");

        PropertyEditors.mapJavaBeanProperties(endpoint, properties);

        assertEquals(8181, endpoint.getPort());
        assertEquals("orders", endpoint.getLabel());
        assertEquals(new BigDecimal("9.75"), endpoint.getPrice());
    }

    @Test
    void rejectsUnknownBeanPropertiesUnlessLenientMappingIsRequested() throws Exception {
        Endpoint endpoint = new Endpoint();
        Properties properties = new Properties();
        properties.setProperty("unknownOption", "unused");
        assertThrows(IntrospectionException.class,
                () -> PropertyEditors.mapJavaBeanProperties(endpoint, properties));

        properties.setProperty("port", "8282");
        PropertyEditors.mapJavaBeanProperties(endpoint, properties, false);
        assertEquals(8282, endpoint.getPort());
    }

    public static class Endpoint {
        private int port;
        private String label;
        private BigDecimal price;

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }
    }
}
