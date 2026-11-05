/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.util.FactoryBuilderSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FactoryBuilderSupportTest {
    @Test
    void autoRegistersPublicRegistrationMethods() {
        CatalogBuilder builder = new CatalogBuilder();
        builder.autoRegisterNodes();
        Product product = (Product) builder.invokeMethod("product", new Object[]{new LinkedHashMap<>(Map.of("name", "pencil"))});
        assertEquals("pencil", product.name);
        assertEquals(1, builder.registrations);
        builder.autoRegisterNodes();
        assertEquals(1, builder.registrations);
    }

    public static class CatalogBuilder extends FactoryBuilderSupport {
        public int registrations;

        public void registerProducts() {
            registrations++;
            registerBeanFactory("product", Product.class);
        }
    }

    public static class Product {
        public String name;
    }
}
