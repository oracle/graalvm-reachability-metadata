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

public class FactoryBuilderSupportAnonymous1Test {
    @Test
    void createsAndConfiguresARegisteredBean() {
        FactoryBuilderSupport builder = new BeanBuilder();
        builder.registerBeanFactory("item", Item.class);
        Item item = (Item) builder.invokeMethod("item", new Object[]{new LinkedHashMap<>(Map.of("name", "paper"))});
        assertEquals("paper", item.name);
    }

    public static class BeanBuilder extends FactoryBuilderSupport {
    }

    public static class Item {
        public String name;
    }
}
